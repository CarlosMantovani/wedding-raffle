package com.weddingraffle.rifa.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.weddingraffle.rifa.entity.LuckyNumber;
import com.weddingraffle.rifa.entity.ParticipantFlag;
import com.weddingraffle.rifa.entity.PaymentMethod;
import com.weddingraffle.rifa.entity.PaymentStatus;
import com.weddingraffle.rifa.entity.Transaction;
import com.weddingraffle.rifa.exception.LuckyNumberAllocationException;
import com.weddingraffle.rifa.exception.LuckyNumberAllocationException.Reason;
import com.weddingraffle.rifa.repository.LuckyNumberAllocationCandidate;
import com.weddingraffle.rifa.repository.LuckyNumberAllocationRepository;
import com.weddingraffle.rifa.repository.LuckyNumberRepository;
import com.weddingraffle.rifa.repository.TransactionRepository;
import com.weddingraffle.rifa.service.LuckyNumberService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class LuckyNumberAllocationIntegrationTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(LuckyNumberAllocationIntegrationTests.class);
    private static final int NUMBER_CAPACITY = 100_000;
    private static final String ADMIN_PASSWORD_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhiLKJbQ1fR5fYbE4kNf4w6S2Gz7uK3a";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("lucky_number_allocation")
            .withUsername("lucky_number_allocation")
            .withPassword("lucky_number_allocation");

    @Autowired
    private LuckyNumberService luckyNumberService;

    @Autowired
    private LuckyNumberRepository luckyNumberRepository;

    @Autowired
    private LuckyNumberAllocationRepository allocationRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

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
        registry.add("app.lucky-number-allocation.chunk-size", () -> "1000");
        registry.add("app.lucky-number-allocation.max-conflict-retries", () -> "8");
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
        jdbcTemplate.execute(
                "TRUNCATE TABLE payment_event, provider_payment, purchase_intent, lucky_number, capacity_reservation, raffle_draw, transaction RESTART IDENTITY CASCADE");
        jdbcTemplate.update("UPDATE raffle_capacity SET reserved_quantity = 0, allocated_quantity = 0 WHERE id = 1");
    }

    @ParameterizedTest(name = "allocates {0} numbers in PostgreSQL")
    @ValueSource(ints = {1, 10, 100, 1_000, 10_000})
    void allocatesLargeQuantitiesWithExactBatches(int quantity) {
        String reference = createTransaction(quantity);

        long startedAt = System.nanoTime();
        List<LuckyNumber> allocated = allocate(reference);
        long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        assertThat(allocated).hasSize(quantity);
        assertExactBatch(reference, quantity);
        assertNoGlobalDuplicates();
        LOGGER.info("PLAN-05 quantity={} durationMillis={}", quantity, durationMillis);
    }

    @ParameterizedTest(name = "allocates at {0}% occupancy")
    @ValueSource(ints = {0, 50, 90, 99})
    void allocatesAtIncreasingOccupancyWithoutUnboundedRetries(int occupancyPercent) {
        int occupied = NUMBER_CAPACITY * occupancyPercent / 100;
        prefillNumbers(occupied);
        String reference = createTransaction(100);

        long startedAt = System.nanoTime();
        List<LuckyNumber> allocated = allocate(reference);
        long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        assertThat(allocated).hasSize(100);
        assertExactBatch(reference, 100);
        assertThat(luckyNumberRepository.count()).isEqualTo(occupied + 100L);
        assertNoGlobalDuplicates();
        LOGGER.info("PLAN-05 occupancyPercent={} durationMillis={}", occupancyPercent, durationMillis);
    }

    @ParameterizedTest(name = "{0} concurrent allocations")
    @ValueSource(ints = {2, 10, 50, 100})
    void concurrentTransactionsPersistNoDuplicatesAndExactBatches(int workers) throws Exception {
        int quantityPerTransaction = 10;
        List<String> references = new ArrayList<>();
        for (int worker = 0; worker < workers; worker++) {
            references.add(createTransaction(quantityPerTransaction));
        }

        long startedAt = System.nanoTime();
        runConcurrently(references);
        long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        assertThat(luckyNumberRepository.count()).isEqualTo((long) workers * quantityPerTransaction);
        for (String reference : references) {
            assertExactBatch(reference, quantityPerTransaction);
        }
        assertNoGlobalDuplicates();
        LOGGER.info("PLAN-05 concurrentWorkers={} durationMillis={}", workers, durationMillis);
    }

    @Test
    void onConflictKeepsTheWinnerAndOnlyTheLoserClaimsAReplacement() throws Exception {
        String firstReference = createTransaction(1);
        String secondReference = createTransaction(1);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> allocateSpecificCandidate(firstReference, ready, start));
            Future<Boolean> second = executor.submit(() -> allocateSpecificCandidate(secondReference, ready, start));
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }

        assertExactBatch(firstReference, 1);
        assertExactBatch(secondReference, 1);
        assertThat(luckyNumberRepository.count()).isEqualTo(2);
        assertNoGlobalDuplicates();
    }

    @Test
    void insufficientInventoryRollsBackEveryCandidateFromTheFailedBatch() {
        prefillNumbers(99_995);
        String reference = createTransaction(10);

        assertThatThrownBy(() -> allocate(reference))
                .isInstanceOfSatisfying(
                        LuckyNumberAllocationException.class,
                        exception -> assertThat(exception.getReason()).isEqualTo(Reason.INSUFFICIENT_NUMBERS));

        assertThat(countFor(reference)).isZero();
        assertThat(hasCompletedMarker(reference)).isFalse();
        assertThat(luckyNumberRepository.count()).isEqualTo(99_995);
        assertNoGlobalDuplicates();
    }

    @Test
    void rollbackDuringPersistenceLeavesNoPartialBatchAndRetryIsSafe() {
        String reference = createTransaction(1_000);

        assertThatThrownBy(() -> transactionTemplate().executeWithoutResult(status -> {
                    Transaction transaction = lockedTransaction(reference);
                    luckyNumberService.generateFor(transaction);
                    throw new InjectedRollbackException();
                }))
                .isInstanceOf(InjectedRollbackException.class);

        assertThat(countFor(reference)).isZero();
        assertThat(hasCompletedMarker(reference)).isFalse();

        List<LuckyNumber> retried = allocate(reference);

        assertThat(retried).hasSize(1_000);
        assertExactBatch(reference, 1_000);
        assertNoGlobalDuplicates();
    }

    @Test
    void databaseKeepsTheGlobalUniqueConstraintAsLastLineOfDefense() {
        Long uniqueConstraint = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM pg_constraint
                WHERE conname = 'uq_lucky_number_number'
                  AND contype = 'u'
                """,
                Long.class);

        assertThat(uniqueConstraint).isEqualTo(1);
    }

    private List<LuckyNumber> allocate(String reference) {
        return transactionTemplate().execute(status -> luckyNumberService.generateFor(lockedTransaction(reference)));
    }

    private boolean allocateSpecificCandidate(String reference, CountDownLatch ready, CountDownLatch start)
            throws Exception {
        ready.countDown();
        assertThat(start.await(30, TimeUnit.SECONDS)).isTrue();
        return Boolean.TRUE.equals(transactionTemplate().execute(status -> {
            Transaction transaction = lockedTransaction(reference);
            List<String> inserted = allocationRepository.insertCandidates(
                    transaction.getId(),
                    transaction.getEmail(),
                    List.of(new LuckyNumberAllocationCandidate("54321", 2)));
            if (inserted.isEmpty()) {
                inserted = allocationRepository.insertRandomAvailable(
                        transaction.getId(), transaction.getEmail(), 0, 99999, 5, 1, 3, 7);
            }
            assertThat(inserted).hasSize(1);
            assertThat(allocationRepository.compactAllocationIndexes(transaction.getId()))
                    .isEqualTo(1);
            transaction.markLuckyNumberBatchCompleted(OffsetDateTime.now(ZoneOffset.UTC));
            return "54321".equals(inserted.getFirst());
        }));
    }

    private void runConcurrently(List<String> references) throws Exception {
        CountDownLatch ready = new CountDownLatch(references.size());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(references.size());
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (String reference : references) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(30, TimeUnit.SECONDS)).isTrue();
                    allocate(reference);
                    return null;
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(120, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private String createTransaction(int quantity) {
        return transactionTemplate().execute(status -> {
            String reference = UUID.randomUUID().toString();
            Transaction transaction = new Transaction(
                    "Allocation Buyer",
                    "11999999999",
                    "allocation@example.com",
                    quantity,
                    BigDecimal.TEN,
                    BigDecimal.TEN.multiply(BigDecimal.valueOf(quantity)),
                    PaymentStatus.APPROVED,
                    PaymentMethod.CASH,
                    reference);
            transaction.assignRecoveryCode("1234");
            transaction.assignParticipantFlag(new ParticipantFlag("BRAZIL", "Brasil", "BR"));
            transactionRepository.saveAndFlush(transaction);
            return reference;
        });
    }

    private void prefillNumbers(int quantity) {
        if (quantity == 0) {
            return;
        }
        String reference = createTransaction(quantity);
        transactionTemplate().executeWithoutResult(status -> {
            Transaction transaction = lockedTransaction(reference);
            jdbcTemplate.update(
                    """
                    INSERT INTO lucky_number (number, email, transaction_id, allocation_index)
                    SELECT lpad(value::text, 5, '0'), ?, ?, value + 1
                    FROM generate_series(0, ? - 1) AS occupied(value)
                    """,
                    transaction.getEmail(),
                    transaction.getId(),
                    quantity);
            transaction.markLuckyNumberBatchCompleted(OffsetDateTime.now(ZoneOffset.UTC));
        });
        assertExactBatch(reference, quantity);
    }

    private Transaction lockedTransaction(String reference) {
        return transactionRepository.findLockedByExternalReference(reference).orElseThrow();
    }

    private void assertExactBatch(String reference, int quantity) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM lucky_number number
                JOIN transaction raffle_transaction ON raffle_transaction.id = number.transaction_id
                WHERE raffle_transaction.external_reference = ?
                """,
                Long.class,
                reference);
        Long distinctNumbers = jdbcTemplate.queryForObject(
                """
                SELECT count(DISTINCT number.number)
                FROM lucky_number number
                JOIN transaction raffle_transaction ON raffle_transaction.id = number.transaction_id
                WHERE raffle_transaction.external_reference = ?
                """,
                Long.class,
                reference);
        String indexes = jdbcTemplate.queryForObject(
                """
                SELECT concat(min(number.allocation_index), ':', max(number.allocation_index), ':', count(DISTINCT number.allocation_index))
                FROM lucky_number number
                JOIN transaction raffle_transaction ON raffle_transaction.id = number.transaction_id
                WHERE raffle_transaction.external_reference = ?
                """,
                String.class,
                reference);

        assertThat(count).isEqualTo(quantity);
        assertThat(distinctNumbers).isEqualTo(quantity);
        assertThat(indexes).isEqualTo("1:" + quantity + ":" + quantity);
        assertThat(hasCompletedMarker(reference)).isTrue();
    }

    private void assertNoGlobalDuplicates() {
        Long duplicates = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM (
                    SELECT number
                    FROM lucky_number
                    GROUP BY number
                    HAVING count(*) > 1
                ) duplicated
                """,
                Long.class);
        assertThat(duplicates).isZero();
    }

    private long countFor(String reference) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM lucky_number number
                JOIN transaction raffle_transaction ON raffle_transaction.id = number.transaction_id
                WHERE raffle_transaction.external_reference = ?
                """,
                Long.class,
                reference);
        return count == null ? 0 : count;
    }

    private boolean hasCompletedMarker(String reference) {
        Boolean completed = jdbcTemplate.queryForObject(
                "SELECT lucky_numbers_generated_at IS NOT NULL FROM transaction WHERE external_reference = ?",
                Boolean.class,
                reference);
        return Boolean.TRUE.equals(completed);
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    private static final class InjectedRollbackException extends RuntimeException {}
}
