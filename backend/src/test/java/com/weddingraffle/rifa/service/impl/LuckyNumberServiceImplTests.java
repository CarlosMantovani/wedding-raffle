package com.weddingraffle.rifa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weddingraffle.rifa.config.AppProperties;
import com.weddingraffle.rifa.config.LuckyNumberAllocationProperties;
import com.weddingraffle.rifa.entity.LuckyNumber;
import com.weddingraffle.rifa.entity.PaymentStatus;
import com.weddingraffle.rifa.entity.Transaction;
import com.weddingraffle.rifa.exception.LuckyNumberAllocationException;
import com.weddingraffle.rifa.exception.LuckyNumberAllocationException.Reason;
import com.weddingraffle.rifa.repository.LuckyNumberAllocationCandidate;
import com.weddingraffle.rifa.repository.LuckyNumberAllocationRepository;
import com.weddingraffle.rifa.repository.LuckyNumberRepository;
import com.weddingraffle.rifa.service.LuckyNumberCandidateGenerator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LuckyNumberServiceImplTests {

    @Mock
    private LuckyNumberRepository luckyNumberRepository;

    @Mock
    private LuckyNumberAllocationRepository allocationRepository;

    @Mock
    private LuckyNumberCandidateGenerator candidateGenerator;

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 100, 1_000, 10_000})
    void allocatesConfiguredQuantityInChunksWithoutPerNumberQueries(int quantity) {
        Transaction transaction = transaction(quantity);
        List<String> insertedNumbers = prepareSuccessfulPersistence(transaction);
        AtomicInteger nextCandidate = new AtomicInteger();
        when(candidateGenerator.nextInt(0, 99999)).thenAnswer(invocation -> nextCandidate.getAndIncrement());

        List<LuckyNumber> luckyNumbers = service().generateFor(transaction);

        assertThat(luckyNumbers).hasSize(quantity);
        assertThat(insertedNumbers).hasSize(quantity).doesNotHaveDuplicates();
        assertThat(luckyNumbers)
                .extracting(LuckyNumber::getAllocationIndex)
                .containsExactlyElementsOf(
                        IntStream.rangeClosed(1, quantity).boxed().toList());
        assertThat(transaction.getLuckyNumbersGeneratedAt()).isEqualTo(OffsetDateTime.parse("2026-08-22T12:00:00Z"));
        verify(allocationRepository, times((quantity + 999) / 1_000))
                .insertCandidates(anyLong(), nullable(String.class), anyList());
        verify(allocationRepository, never())
                .insertRandomAvailable(
                        anyLong(), nullable(String.class), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void deduplicatesCandidatesWithConstantTimeSetLookups() {
        Transaction transaction = transaction(2);
        prepareSuccessfulPersistence(transaction);
        when(candidateGenerator.nextInt(0, 99999)).thenReturn(1, 1, 2);

        List<LuckyNumber> luckyNumbers = service().generateFor(transaction);

        assertThat(luckyNumbers).extracting(LuckyNumber::getNumber).containsExactly("00001", "00002");
        verify(candidateGenerator, times(3)).nextInt(0, 99999);
    }

    @Test
    void retainsInsertedCandidatesAndReplacesOnlyConflictsWithSetBasedFallback() {
        Transaction transaction = transaction(2);
        List<String> insertedNumbers = new ArrayList<>();
        when(candidateGenerator.nextInt(0, 99999)).thenReturn(1, 2, 3);
        when(allocationRepository.insertCandidates(
                        1L,
                        "guest@example.com",
                        List.of(
                                new LuckyNumberAllocationCandidate("00001", 3),
                                new LuckyNumberAllocationCandidate("00002", 4))))
                .thenAnswer(invocation -> {
                    insertedNumbers.add("00002");
                    return List.of("00002");
                });
        when(allocationRepository.insertRandomAvailable(1L, "guest@example.com", 0, 99999, 5, 1, 5, 3))
                .thenAnswer(invocation -> {
                    insertedNumbers.add("00003");
                    return List.of("00003");
                });
        prepareFinalBatch(transaction, insertedNumbers);

        List<LuckyNumber> luckyNumbers = service().generateFor(transaction);

        assertThat(luckyNumbers).extracting(LuckyNumber::getNumber).containsExactly("00002", "00003");
        verify(allocationRepository).insertCandidates(anyLong(), nullable(String.class), anyList());
        verify(allocationRepository)
                .insertRandomAvailable(
                        anyLong(), nullable(String.class), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void reportsInsufficientNumbersAndReliesOnTransactionRollbackForPartialWork() {
        Transaction transaction = transaction(2);
        when(candidateGenerator.nextInt(0, 99999)).thenReturn(1, 2, 3);
        when(allocationRepository.insertCandidates(anyLong(), nullable(String.class), anyList()))
                .thenReturn(List.of("00001"));
        when(allocationRepository.insertRandomAvailable(
                        anyLong(), nullable(String.class), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(allocationRepository.countAvailable(0, 99999, 5)).thenReturn(0L);

        assertThatThrownBy(() -> service().generateFor(transaction))
                .isInstanceOfSatisfying(
                        LuckyNumberAllocationException.class,
                        exception -> assertThat(exception.getReason()).isEqualTo(Reason.INSUFFICIENT_NUMBERS))
                .hasMessage("Not enough lucky numbers are available to complete this transaction.");

        verify(allocationRepository, never()).compactAllocationIndexes(anyLong());
        assertThat(transaction.getLuckyNumbersGeneratedAt()).isNull();
    }

    @Test
    void stopsAfterConfiguredConflictRetryLimit() {
        Transaction transaction = transaction(1);
        when(candidateGenerator.nextInt(0, 99999)).thenReturn(1, 2, 3, 4);
        when(allocationRepository.insertCandidates(anyLong(), nullable(String.class), anyList()))
                .thenReturn(List.of());
        when(allocationRepository.insertRandomAvailable(
                        anyLong(), nullable(String.class), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(allocationRepository.countAvailable(0, 99999, 5)).thenReturn(100_000L);

        assertThatThrownBy(
                        () -> service(new LuckyNumberAllocationProperties(1, 1)).generateFor(transaction))
                .isInstanceOfSatisfying(
                        LuckyNumberAllocationException.class,
                        exception -> assertThat(exception.getReason()).isEqualTo(Reason.RETRY_LIMIT_REACHED))
                .hasMessage("Lucky-number allocation could not be completed after concurrent conflicts.");

        verify(allocationRepository, times(2))
                .insertRandomAvailable(
                        anyLong(), nullable(String.class), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void doesNotGenerateAgainWhenTransactionAlreadyHasLuckyNumbers() {
        Transaction transaction = transaction(1);
        LuckyNumber existingLuckyNumber = new LuckyNumber("00001", "guest@example.com", transaction, 1);
        transaction.markLuckyNumberBatchCompleted(OffsetDateTime.parse("2026-08-22T11:00:00Z"));
        when(luckyNumberRepository.findByTransactionOrderByNumberAsc(transaction))
                .thenReturn(List.of(existingLuckyNumber));

        List<LuckyNumber> luckyNumbers = service().generateFor(transaction);

        assertThat(luckyNumbers).containsExactly(existingLuckyNumber);
        verify(allocationRepository, never()).insertCandidates(anyLong(), nullable(String.class), anyList());
    }

    @Test
    void findsNumbersByTransactionExternalReference() {
        when(luckyNumberRepository.findNumbersByTransactionExternalReference("external"))
                .thenReturn(List.of("00001", "00002"));

        List<String> numbers = service().findNumbers("external");

        assertThat(numbers).containsExactly("00001", "00002");
    }

    @Test
    void findsPreviousApprovedNumbersByPhoneExcludingCurrentTransaction() {
        when(luckyNumberRepository.findNumbersByPhoneAndStatusExcludingExternalReference(
                        "11999999999", PaymentStatus.APPROVED, "external"))
                .thenReturn(List.of("00001", "00002"));

        List<String> numbers = service().findPreviousApprovedNumbers("11999999999", "external");

        assertThat(numbers).containsExactly("00001", "00002");
    }

    private List<String> prepareSuccessfulPersistence(Transaction transaction) {
        List<String> insertedNumbers = new ArrayList<>();
        when(allocationRepository.insertCandidates(anyLong(), nullable(String.class), anyList()))
                .thenAnswer(invocation -> {
                    List<LuckyNumberAllocationCandidate> candidates = invocation.getArgument(2);
                    List<String> numbers = candidates.stream()
                            .map(LuckyNumberAllocationCandidate::number)
                            .toList();
                    insertedNumbers.addAll(numbers);
                    return numbers;
                });
        prepareFinalBatch(transaction, insertedNumbers);
        return insertedNumbers;
    }

    private void prepareFinalBatch(Transaction transaction, List<String> insertedNumbers) {
        when(allocationRepository.compactAllocationIndexes(1L)).thenReturn(transaction.getQuantity());
        when(luckyNumberRepository.findByTransactionOrderByNumberAsc(transaction))
                .thenAnswer(invocation -> {
                    List<String> sorted = insertedNumbers.stream().sorted().toList();
                    return IntStream.range(0, sorted.size())
                            .mapToObj(index ->
                                    new LuckyNumber(sorted.get(index), transaction.getEmail(), transaction, index + 1))
                            .toList();
                });
    }

    private LuckyNumberServiceImpl service() {
        return service(new LuckyNumberAllocationProperties(1_000, 8));
    }

    private LuckyNumberServiceImpl service(LuckyNumberAllocationProperties properties) {
        return new LuckyNumberServiceImpl(
                appProperties(), properties, luckyNumberRepository, allocationRepository, candidateGenerator, clock());
    }

    private static Transaction transaction(int quantity) {
        Transaction transaction = new Transaction(
                "guest@example.com",
                quantity,
                BigDecimal.TEN.multiply(BigDecimal.valueOf(quantity)),
                PaymentStatus.PENDING,
                "external");
        ReflectionTestUtils.setField(transaction, "id", 1L);
        return transaction;
    }

    private static AppProperties appProperties() {
        return new AppProperties(
                "http://localhost:5173",
                new AppProperties.Jwt("01234567890123456789012345678901", 3600, "raffle-api-test"),
                new AppProperties.Raffle(new BigDecimal("10.00"), "00000", "99999"),
                new AppProperties.MercadoPago(
                        "token",
                        "http://localhost:8080/payments/webhook",
                        "",
                        "http://localhost:5173/payment-return/success",
                        "http://localhost:5173/payment-return/failure",
                        "http://localhost:5173/payment-return/pending",
                        new AppProperties.Retry(3, 500, 2)));
    }

    private static Clock clock() {
        return Clock.fixed(OffsetDateTime.parse("2026-08-22T12:00:00Z").toInstant(), ZoneOffset.UTC);
    }
}
