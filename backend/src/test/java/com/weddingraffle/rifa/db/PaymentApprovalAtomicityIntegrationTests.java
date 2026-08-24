package com.weddingraffle.rifa.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weddingraffle.rifa.dto.TransactionCreateRequest;
import com.weddingraffle.rifa.dto.TransactionCreateResponse;
import com.weddingraffle.rifa.entity.CapacityReservationStatus;
import com.weddingraffle.rifa.entity.PaymentEventProcessingStatus;
import com.weddingraffle.rifa.entity.PaymentStatus;
import com.weddingraffle.rifa.entity.RaffleCapacity;
import com.weddingraffle.rifa.integration.CheckoutPreferenceResponse;
import com.weddingraffle.rifa.integration.PaymentProviderClient;
import com.weddingraffle.rifa.integration.PaymentProviderPayment;
import com.weddingraffle.rifa.repository.CapacityReservationRepository;
import com.weddingraffle.rifa.repository.LuckyNumberRepository;
import com.weddingraffle.rifa.repository.PaymentEventRepository;
import com.weddingraffle.rifa.repository.ProviderPaymentRepository;
import com.weddingraffle.rifa.repository.RaffleCapacityRepository;
import com.weddingraffle.rifa.repository.TransactionRepository;
import com.weddingraffle.rifa.service.LuckyNumberCandidateGenerator;
import com.weddingraffle.rifa.service.TransactionService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PaymentApprovalAtomicityIntegrationTests {

    private static final String ADMIN_PASSWORD_HASH = "$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
    private static final String PAYMENT_ID = "payment-atomic-123";
    private static final String PREFERENCE_ID = "preference-atomic-123";
    private static final String COLLECTOR_ID = "collector-atomic-123";
    private static final int QUANTITY = 3;
    private static final OffsetDateTime T1 = OffsetDateTime.parse("2026-08-22T11:00:00Z");
    private static final OffsetDateTime T2 = OffsetDateTime.parse("2026-08-22T12:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("payment_approval_atomicity")
            .withUsername("payment_approval_atomicity")
            .withPassword("payment_approval_atomicity");

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private LuckyNumberRepository luckyNumberRepository;

    @Autowired
    private PaymentEventRepository paymentEventRepository;

    @Autowired
    private ProviderPaymentRepository providerPaymentRepository;

    @Autowired
    private CapacityReservationRepository capacityReservationRepository;

    @Autowired
    private RaffleCapacityRepository raffleCapacityRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private PaymentProviderClient paymentProviderClient;

    @MockBean
    private LuckyNumberCandidateGenerator candidateGenerator;

    private AtomicInteger generatedCandidateCalls;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.placeholders.admin_username", () -> "admin");
        registry.add("spring.flyway.placeholders.admin_password_hash", () -> ADMIN_PASSWORD_HASH);
        registry.add("spring.flyway.placeholders.raffle_unit_price", () -> "10.00");
        registry.add("spring.flyway.placeholders.raffle_number_min", () -> "00000");
        registry.add("spring.flyway.placeholders.raffle_number_max", () -> "99999");
        registry.add("app.frontend-origin", () -> "http://localhost:5173");
        registry.add("app.jwt.secret", () -> "01234567890123456789012345678901");
        registry.add("app.jwt.expiration-seconds", () -> "3600");
        registry.add("app.jwt.issuer", () -> "test");
        registry.add("app.raffle.unit-price", () -> "10.00");
        registry.add("app.raffle.number-min", () -> "00000");
        registry.add("app.raffle.number-max", () -> "99999");
        registry.add("app.mercado-pago.access-token", () -> "TEST-token");
        registry.add("app.mercado-pago.webhook-url", () -> "https://example.com/webhook");
        registry.add("app.mercado-pago.webhook-secret", () -> "secret");
        registry.add("app.mercado-pago.success-url", () -> "https://example.com/success");
        registry.add("app.mercado-pago.failure-url", () -> "https://example.com/failure");
        registry.add("app.mercado-pago.pending-url", () -> "https://example.com/pending");
        registry.add("app.mercado-pago.retry.max-attempts", () -> "1");
        registry.add("app.mercado-pago.retry.delay-millis", () -> "1");
        registry.add("app.mercado-pago.retry.multiplier", () -> "1");
    }

    @BeforeEach
    void resetDatabase() {
        reset(paymentProviderClient, candidateGenerator);
        jdbcTemplate.execute(
                "TRUNCATE TABLE payment_event, provider_payment, purchase_intent, lucky_number, capacity_reservation, raffle_draw, transaction RESTART IDENTITY CASCADE");
        jdbcTemplate.update("UPDATE raffle_capacity SET reserved_quantity = 0, allocated_quantity = 0 WHERE id = 1");
        when(paymentProviderClient.createPreference(any(), anyString()))
                .thenReturn(
                        new CheckoutPreferenceResponse(PREFERENCE_ID, "https://checkout.example.com", COLLECTOR_ID));
        useSuccessfulCandidates();
    }

    @Test
    void normalApprovalCreatesOneExactBatch() {
        TransactionCreateResponse created = createTransaction();
        PaymentProviderPayment approved = payment(created, "approved", T2);
        when(paymentProviderClient.getPayment(PAYMENT_ID)).thenReturn(approved);

        transactionService.processPaymentNotification(PAYMENT_ID);

        assertSingleCompletedBatch(created, 1);
        assertThat(generatedCandidateCalls).hasValue(QUANTITY);
    }

    @ParameterizedTest(name = "{0} duplicate webhook workers create one exact batch")
    @ValueSource(ints = {2, 10, 50, 100})
    void duplicateWebhookWorkersCreateOneExactBatch(int workers) throws Exception {
        TransactionCreateResponse created = createTransaction();
        PaymentProviderPayment approved = payment(created, "approved", T2);
        makeAllProviderCallsReadBeforeTheCriticalSection(workers, approved);

        runConcurrently(workers, () -> transactionService.processPaymentNotification(PAYMENT_ID));

        assertSingleCompletedBatch(created, workers);
        assertThat(generatedCandidateCalls).hasValue(QUANTITY);
    }

    @Test
    void webhookAndPollingApplyTheSameApprovalOnce() throws Exception {
        TransactionCreateResponse created = createTransactionWithPendingPayment();
        PaymentProviderPayment approved = payment(created, "approved", T2);
        makeAllProviderCallsReadBeforeTheCriticalSection(2, approved);

        runConcurrently(
                () -> transactionService.processPaymentNotification(PAYMENT_ID),
                () -> transactionService.getStatus(created.externalReference()));

        assertSingleCompletedBatch(created, 2);
        assertThat(generatedCandidateCalls).hasValue(QUANTITY);
    }

    @ParameterizedTest(name = "{0} concurrent polls apply the approval through one external reconciliation")
    @ValueSource(ints = {2, 10, 50})
    void concurrentPollsApplyTheApprovalThroughOneExternalReconciliation(int workers) throws Exception {
        TransactionCreateResponse created = createTransactionWithPendingPayment();
        PaymentProviderPayment approved = payment(created, "approved", T2);
        clearInvocations(paymentProviderClient);
        when(paymentProviderClient.getPayment(PAYMENT_ID)).thenReturn(approved);

        runConcurrently(workers, () -> transactionService.getStatus(created.externalReference()));

        verify(paymentProviderClient, times(1)).getPayment(PAYMENT_ID);
        assertSingleCompletedBatch(created, 1);
        assertThat(generatedCandidateCalls).hasValue(QUANTITY);
    }

    @Test
    void webhookApprovalPrevailsOverAnOlderPendingPollingResponse() throws Exception {
        TransactionCreateResponse created = createTransactionWithPendingPayment();
        PaymentProviderPayment oldPending = payment(created, "pending", T1);
        PaymentProviderPayment approved = payment(created, "approved", T2);
        CountDownLatch pollingFetchStarted = new CountDownLatch(1);
        CountDownLatch releasePollingFetch = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        clearInvocations(paymentProviderClient);
        when(paymentProviderClient.getPayment(PAYMENT_ID)).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                pollingFetchStarted.countDown();
                assertThat(releasePollingFetch.await(10, TimeUnit.SECONDS)).isTrue();
                return oldPending;
            }
            return approved;
        });

        var executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> delayedPolling = executor.submit(() -> transactionService.getStatus(created.externalReference()));
            assertThat(pollingFetchStarted.await(10, TimeUnit.SECONDS)).isTrue();
            transactionService.processPaymentNotification(PAYMENT_ID);
            releasePollingFetch.countDown();
            delayedPolling.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        verify(paymentProviderClient, times(2)).getPayment(PAYMENT_ID);
        assertSingleCompletedBatch(created, 1);
        assertThat(generatedCandidateCalls).hasValue(QUANTITY);
    }

    @Test
    void retryAfterCommittedApprovalReturnsTheExistingBatch() {
        TransactionCreateResponse created = createTransaction();
        PaymentProviderPayment approved = payment(created, "approved", T2);
        when(paymentProviderClient.getPayment(PAYMENT_ID)).thenReturn(approved);

        transactionService.processPaymentNotification(PAYMENT_ID);
        transactionService.processPaymentNotification(PAYMENT_ID);

        assertSingleCompletedBatch(created, 2);
        assertThat(generatedCandidateCalls).hasValue(QUANTITY);
    }

    @Test
    void failureBeforeGenerationRollsBackAndRetryCreatesTheExactBatch() {
        TransactionCreateResponse created = createTransaction();
        PaymentProviderPayment approved = payment(created, "approved", T2);
        when(paymentProviderClient.getPayment(PAYMENT_ID)).thenReturn(approved);
        doThrow(new InjectedGenerationFailure("failure before the first candidate"))
                .when(candidateGenerator)
                .nextInt(anyInt(), anyInt());

        assertThatThrownBy(() -> transactionService.processPaymentNotification(PAYMENT_ID))
                .isInstanceOf(InjectedGenerationFailure.class);
        assertApprovalRolledBack(created);

        useSuccessfulCandidates();
        transactionService.processPaymentNotification(PAYMENT_ID);

        assertSingleCompletedBatch(created, 1);
    }

    @Test
    void failureDuringGenerationRollsBackAndRetryCreatesTheExactBatch() {
        TransactionCreateResponse created = createTransaction();
        PaymentProviderPayment approved = payment(created, "approved", T2);
        when(paymentProviderClient.getPayment(PAYMENT_ID)).thenReturn(approved);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
                    if (calls.incrementAndGet() == 2) {
                        throw new InjectedGenerationFailure("failure after one candidate");
                    }
                    return 0;
                })
                .when(candidateGenerator)
                .nextInt(anyInt(), anyInt());

        assertThatThrownBy(() -> transactionService.processPaymentNotification(PAYMENT_ID))
                .isInstanceOf(InjectedGenerationFailure.class);
        assertApprovalRolledBack(created);

        useSuccessfulCandidates();
        transactionService.processPaymentNotification(PAYMENT_ID);

        assertSingleCompletedBatch(created, 1);
    }

    @Test
    void duplicateLedgerEventDoesNotStartAnotherLocalGeneration() {
        TransactionCreateResponse created = createTransaction();
        PaymentProviderPayment approved = payment(created, "approved", T2);
        when(paymentProviderClient.getPayment(PAYMENT_ID)).thenReturn(approved);

        transactionService.processPaymentNotification(PAYMENT_ID);
        int callsAfterCommit = generatedCandidateCalls.get();
        transactionService.processPaymentNotification(PAYMENT_ID);

        assertThat(generatedCandidateCalls).hasValue(callsAfterCommit);
        assertSingleCompletedBatch(created, 2);
    }

    @Test
    void obsoleteFinancialEventDoesNotStartAnotherLocalGeneration() {
        TransactionCreateResponse created = createTransaction();
        PaymentProviderPayment approved = payment(created, "approved", T2);
        when(paymentProviderClient.getPayment(PAYMENT_ID)).thenReturn(approved);
        transactionService.processPaymentNotification(PAYMENT_ID);
        int callsAfterCommit = generatedCandidateCalls.get();

        PaymentProviderPayment obsoleteApproved = payment(created, "approved", T1);
        when(paymentProviderClient.getPayment(PAYMENT_ID)).thenReturn(obsoleteApproved);
        transactionService.processPaymentNotification(PAYMENT_ID);

        assertThat(generatedCandidateCalls).hasValue(callsAfterCommit);
        assertThat(paymentEventRepository.findAll())
                .extracting(event -> event.getProcessingStatus())
                .containsExactlyInAnyOrder(PaymentEventProcessingStatus.APPLIED, PaymentEventProcessingStatus.OBSOLETE);
        assertSingleCompletedBatch(created, 1);
    }

    @Test
    void databaseRejectsACompletedBatchWithMissingNumbersAtCommit() {
        TransactionCreateResponse created = createTransaction();
        PaymentProviderPayment approved = payment(created, "approved", T2);
        when(paymentProviderClient.getPayment(PAYMENT_ID)).thenReturn(approved);
        transactionService.processPaymentNotification(PAYMENT_ID);

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "delete from lucky_number where id = (select min(id) from lucky_number where transaction_id = (select id from transaction where external_reference = ?))",
                        created.externalReference()))
                .isInstanceOf(DataAccessException.class)
                .hasRootCauseInstanceOf(java.sql.SQLException.class)
                .hasStackTraceContaining("lucky-number batch is incomplete");

        assertSingleCompletedBatch(created, 1);
    }

    private TransactionCreateResponse createTransactionWithPendingPayment() {
        TransactionCreateResponse created = createTransaction();
        PaymentProviderPayment pending = payment(created, "pending", T1);
        when(paymentProviderClient.getPayment(PAYMENT_ID)).thenReturn(pending);
        transactionService.processPaymentNotification(PAYMENT_ID);
        return created;
    }

    private TransactionCreateResponse createTransaction() {
        return transactionService.create(
                UUID.randomUUID().toString(),
                new TransactionCreateRequest("Atomic Buyer", "(11) 99999-9999", QUANTITY));
    }

    private PaymentProviderPayment payment(
            TransactionCreateResponse created, String status, OffsetDateTime providerUpdatedAt) {
        return new PaymentProviderPayment(
                PAYMENT_ID,
                created.externalReference(),
                created.externalReference(),
                PREFERENCE_ID,
                COLLECTOR_ID,
                BigDecimal.valueOf(QUANTITY).multiply(new BigDecimal("10.00")),
                "BRL",
                status,
                status,
                T1,
                providerUpdatedAt);
    }

    private void makeAllProviderCallsReadBeforeTheCriticalSection(int workers, PaymentProviderPayment payment) {
        CountDownLatch fetched = new CountDownLatch(workers);
        when(paymentProviderClient.getPayment(PAYMENT_ID)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .isFalse();
            fetched.countDown();
            assertThat(fetched.await(30, TimeUnit.SECONDS)).isTrue();
            return payment;
        });
    }

    private void useSuccessfulCandidates() {
        generatedCandidateCalls = new AtomicInteger();
        doAnswer(invocation -> generatedCandidateCalls.getAndIncrement())
                .when(candidateGenerator)
                .nextInt(anyInt(), anyInt());
    }

    private void assertApprovalRolledBack(TransactionCreateResponse created) {
        var transaction = transactionRepository
                .findByExternalReference(created.externalReference())
                .orElseThrow();
        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(transaction.getLuckyNumbersGeneratedAt()).isNull();
        assertThat(luckyNumberRepository.count()).isZero();
        assertThat(paymentEventRepository.count()).isZero();
        assertThat(providerPaymentRepository.count()).isZero();
        assertThat(capacityReservationRepository.findAll())
                .singleElement()
                .extracting(reservation -> reservation.getStatus())
                .isEqualTo(CapacityReservationStatus.ACTIVE);
        RaffleCapacity capacity =
                raffleCapacityRepository.findById(RaffleCapacity.SINGLETON_ID).orElseThrow();
        assertThat(capacity.getReservedQuantity()).isEqualTo(QUANTITY);
        assertThat(capacity.getAllocatedQuantity()).isZero();
    }

    private void assertSingleCompletedBatch(TransactionCreateResponse created, int expectedApprovedDeliveries) {
        var transaction = transactionRepository
                .findByExternalReference(created.externalReference())
                .orElseThrow();
        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(transaction.getLuckyNumbersGeneratedAt()).isNotNull();

        Long completedBatchMarkers = jdbcTemplate.queryForObject(
                "select count(*) from transaction where external_reference = ? and lucky_numbers_generated_at is not null",
                Long.class,
                created.externalReference());
        Long persistedNumbers = jdbcTemplate.queryForObject(
                "select count(*) from lucky_number where transaction_id = ?", Long.class, transaction.getId());
        Long distinctNumbers = jdbcTemplate.queryForObject(
                "select count(distinct number) from lucky_number where transaction_id = ?",
                Long.class,
                transaction.getId());
        Long distinctAllocationIndexes = jdbcTemplate.queryForObject(
                "select count(distinct allocation_index) from lucky_number where transaction_id = ?",
                Long.class,
                transaction.getId());

        assertThat(completedBatchMarkers).isEqualTo(1);
        assertThat(persistedNumbers).isEqualTo(transaction.getQuantity().longValue());
        assertThat(distinctNumbers).isEqualTo(transaction.getQuantity().longValue());
        assertThat(distinctAllocationIndexes)
                .isEqualTo(transaction.getQuantity().longValue());
        assertThat(luckyNumberRepository.count())
                .isEqualTo(transaction.getQuantity().longValue());

        var approvedEvents = paymentEventRepository.findAll().stream()
                .filter(event -> "approved".equals(event.getProviderStatus()))
                .filter(event -> event.getProcessingStatus() == PaymentEventProcessingStatus.APPLIED)
                .toList();
        assertThat(approvedEvents).hasSize(1);
        assertThat(approvedEvents.getFirst().getProcessingStatus()).isEqualTo(PaymentEventProcessingStatus.APPLIED);
        assertThat(approvedEvents.getFirst().getDeliveryCount()).isEqualTo(expectedApprovedDeliveries);

        RaffleCapacity capacity =
                raffleCapacityRepository.findById(RaffleCapacity.SINGLETON_ID).orElseThrow();
        assertThat(capacity.getReservedQuantity()).isZero();
        assertThat(capacity.getAllocatedQuantity()).isEqualTo(QUANTITY);
    }

    private static void runConcurrently(int workers, ThrowingRunnable runnable) throws Exception {
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(workers);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int index = 0; index < workers; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(30, TimeUnit.SECONDS)).isTrue();
                    runnable.run();
                    return null;
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(90, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static void runConcurrently(ThrowingRunnable first, ThrowingRunnable second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> futures = List.of(
                    executor.submit(() -> runAfterBarrier(first, ready, start)),
                    executor.submit(() -> runAfterBarrier(second, ready, start)));
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(90, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static Void runAfterBarrier(ThrowingRunnable runnable, CountDownLatch ready, CountDownLatch start)
            throws Exception {
        ready.countDown();
        assertThat(start.await(30, TimeUnit.SECONDS)).isTrue();
        runnable.run();
        return null;
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class InjectedGenerationFailure extends RuntimeException {

        private InjectedGenerationFailure(String message) {
            super(message);
        }
    }
}
