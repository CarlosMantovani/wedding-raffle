package com.weddingraffle.rifa.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.weddingraffle.rifa.service.impl.PaymentReconciliationLeaseService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PaymentReconciliationLeaseIntegrationTests {

    private static final String ADMIN_PASSWORD_HASH = "$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-24T15:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("payment_reconciliation_lease")
            .withUsername("payment_reconciliation_lease")
            .withPassword("payment_reconciliation_lease");

    @Autowired
    private PaymentReconciliationLeaseService leaseService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
        registry.add("app.payment-status-reconciliation.minimum-interval-millis", () -> "5000");
        registry.add("app.payment-status-reconciliation.lease-duration-millis", () -> "30000");
    }

    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE transaction RESTART IDENTITY CASCADE");
    }

    @ParameterizedTest(name = "{0} simultaneous claimers produce one durable owner")
    @ValueSource(ints = {2, 10, 50})
    void simultaneousClaimersProduceOneDurableOwner(int workers) throws Exception {
        long transactionId = insertPendingTransaction();

        List<ClaimResult> results = runConcurrently(workers, transactionId, NOW);

        assertThat(results).filteredOn(ClaimResult::acquired).hasSize(1);
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from transaction where payment_reconciliation_lease_token is not null",
                        Long.class))
                .isEqualTo(1L);
    }

    @Test
    void minimumIntervalPreventsAnotherExternalAttemptAfterLeaseRelease() {
        long transactionId = insertPendingTransaction();
        UUID firstToken = UUID.randomUUID();
        UUID secondToken = UUID.randomUUID();

        assertThat(leaseService.tryAcquire(transactionId, firstToken, NOW)).isTrue();
        leaseService.release(transactionId, firstToken);
        assertThat(leaseService.tryAcquire(transactionId, secondToken, NOW.plusSeconds(4)))
                .isFalse();
        assertThat(leaseService.tryAcquire(transactionId, secondToken, NOW.plusSeconds(5)))
                .isTrue();
    }

    @Test
    void expiredLeaseCanBeRecoveredByAnotherApplicationInstance() {
        long transactionId = insertPendingTransaction();

        assertThat(leaseService.tryAcquire(transactionId, UUID.randomUUID(), NOW))
                .isTrue();
        assertThat(leaseService.tryAcquire(transactionId, UUID.randomUUID(), NOW.plusSeconds(29)))
                .isFalse();
        assertThat(leaseService.tryAcquire(transactionId, UUID.randomUUID(), NOW.plusSeconds(30)))
                .isTrue();
    }

    @Test
    void terminalTransactionCannotAcquireAReconciliationLease() {
        long transactionId = insertPendingTransaction();
        jdbcTemplate.update("update transaction set status = 'APPROVED' where id = ?", transactionId);

        assertThat(leaseService.tryAcquire(transactionId, UUID.randomUUID(), NOW))
                .isFalse();
    }

    private List<ClaimResult> runConcurrently(int workers, long transactionId, OffsetDateTime attemptedAt)
            throws Exception {
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(workers);
        List<Future<ClaimResult>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < workers; index++) {
                futures.add(executor.submit(() -> {
                    UUID token = UUID.randomUUID();
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    return new ClaimResult(token, leaseService.tryAcquire(transactionId, token, attemptedAt));
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<ClaimResult> results = new ArrayList<>();
            for (Future<ClaimResult> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private long insertPendingTransaction() {
        return jdbcTemplate.queryForObject(
                """
                insert into transaction (
                    name,
                    phone,
                    quantity,
                    total_amount,
                    unit_price,
                    status,
                    payment_method,
                    external_reference,
                    recovery_code,
                    participant_flag_code,
                    participant_flag_name,
                    participant_flag_emoji,
                    mp_payment_id,
                    mp_preference_id,
                    mp_collector_id
                ) values (
                    'Polling Buyer',
                    '11999999999',
                    2,
                    20.00,
                    10.00,
                    'PENDING',
                    'MERCADO_PAGO',
                    'external-reference-polling',
                    '4821',
                    'BRAZIL',
                    'Brasil',
                    'BR',
                    'payment-123',
                    'preference-123',
                    'collector-123'
                )
                returning id
                """,
                Long.class);
    }

    private record ClaimResult(UUID token, boolean acquired) {}
}
