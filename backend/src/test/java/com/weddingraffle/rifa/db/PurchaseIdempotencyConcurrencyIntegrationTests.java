package com.weddingraffle.rifa.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.weddingraffle.rifa.dto.CashTransactionCreateRequest;
import com.weddingraffle.rifa.dto.CashTransactionCreateResponse;
import com.weddingraffle.rifa.dto.TransactionCreateRequest;
import com.weddingraffle.rifa.dto.TransactionCreateResponse;
import com.weddingraffle.rifa.entity.CapacityReservationStatus;
import com.weddingraffle.rifa.entity.PurchaseIntentStatus;
import com.weddingraffle.rifa.entity.RaffleCapacity;
import com.weddingraffle.rifa.exception.IdempotencyConflictException;
import com.weddingraffle.rifa.integration.CheckoutPreferenceResponse;
import com.weddingraffle.rifa.integration.PaymentProviderClient;
import com.weddingraffle.rifa.repository.CapacityReservationRepository;
import com.weddingraffle.rifa.repository.LuckyNumberRepository;
import com.weddingraffle.rifa.repository.PurchaseIntentRepository;
import com.weddingraffle.rifa.repository.RaffleCapacityRepository;
import com.weddingraffle.rifa.repository.TransactionRepository;
import com.weddingraffle.rifa.service.AdminTransactionService;
import com.weddingraffle.rifa.service.TransactionService;
import com.zaxxer.hikari.HikariDataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PurchaseIdempotencyConcurrencyIntegrationTests {

    private static final String ADMIN_PASSWORD_HASH = "$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("purchase_idempotency")
            .withUsername("purchase_idempotency")
            .withPassword("purchase_idempotency");

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AdminTransactionService adminTransactionService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private LuckyNumberRepository luckyNumberRepository;

    @Autowired
    private PurchaseIntentRepository purchaseIntentRepository;

    @Autowired
    private CapacityReservationRepository capacityReservationRepository;

    @Autowired
    private RaffleCapacityRepository raffleCapacityRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private HikariDataSource dataSource;

    @MockBean
    private PaymentProviderClient paymentProviderClient;

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
        reset(paymentProviderClient);
        jdbcTemplate.execute(
                "TRUNCATE TABLE purchase_intent, lucky_number, capacity_reservation, raffle_draw, transaction RESTART IDENTITY CASCADE");
        jdbcTemplate.update("UPDATE raffle_capacity SET reserved_quantity = 0, allocated_quantity = 0 WHERE id = 1");
    }

    @Test
    void simultaneousOnlinePostsWithSameKeyReturnOneTransactionAndCheckout() throws Exception {
        String idempotencyKey = "simultaneous-online-key";
        CountDownLatch providerCalls = new CountDownLatch(2);
        AtomicInteger createdCheckouts = new AtomicInteger();
        var checkouts = new ConcurrentHashMap<String, CheckoutPreferenceResponse>();
        when(paymentProviderClient.createPreference(any(), anyString())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .isFalse();
            String key = invocation.getArgument(1);
            providerCalls.countDown();
            providerCalls.await(10, TimeUnit.SECONDS);
            assertThat(dataSource.getHikariPoolMXBean().getActiveConnections()).isZero();
            return checkouts.computeIfAbsent(key, ignored -> {
                createdCheckouts.incrementAndGet();
                return new CheckoutPreferenceResponse(
                        "preference-123", "https://checkout.example.com", "collector-123");
            });
        });

        List<TransactionCreateResponse> responses = runConcurrently(
                2,
                () -> transactionService.create(
                        idempotencyKey, new TransactionCreateRequest("Guest User", "(11) 99999-9999", 2)));

        assertThat(responses).hasSize(2).allMatch(responses.getFirst()::equals);
        assertThat(createdCheckouts).hasValue(1);
        assertThat(transactionRepository.count()).isEqualTo(1);
        assertThat(purchaseIntentRepository.findAll())
                .singleElement()
                .extracting(intent -> intent.getStatus())
                .isEqualTo(PurchaseIntentStatus.COMPLETED);
        assertThat(capacityReservationRepository.findAll())
                .singleElement()
                .extracting(reservation -> reservation.getStatus())
                .isEqualTo(CapacityReservationStatus.ACTIVE);
        RaffleCapacity capacity =
                raffleCapacityRepository.findById(RaffleCapacity.SINGLETON_ID).orElseThrow();
        assertThat(capacity.getReservedQuantity()).isEqualTo(2);
        assertThat(capacity.getAllocatedQuantity()).isZero();
    }

    @Test
    void simultaneousCashPostsWithSameKeyDoNotDuplicateTransactionOrLuckyNumbers() throws Exception {
        List<CashTransactionCreateResponse> responses = runConcurrently(
                2,
                () -> adminTransactionService.createCashTransaction(
                        "simultaneous-cash-key",
                        new CashTransactionCreateRequest("Cash Guest", "(11) 99999-9999", null, 3)));

        assertThat(responses).hasSize(2).allMatch(responses.getFirst()::equals);
        assertThat(transactionRepository.count()).isEqualTo(1);
        assertThat(luckyNumberRepository.count()).isEqualTo(3);
        assertThat(transactionRepository.findAll())
                .singleElement()
                .extracting(transaction -> transaction.getLuckyNumbersGeneratedAt())
                .isNotNull();
        assertThat(purchaseIntentRepository.count()).isEqualTo(1);
        assertThat(capacityReservationRepository.findAll())
                .singleElement()
                .extracting(reservation -> reservation.getStatus())
                .isEqualTo(CapacityReservationStatus.ALLOCATED);
        RaffleCapacity capacity =
                raffleCapacityRepository.findById(RaffleCapacity.SINGLETON_ID).orElseThrow();
        assertThat(capacity.getReservedQuantity()).isZero();
        assertThat(capacity.getAllocatedQuantity()).isEqualTo(3);
    }

    @Test
    void sameKeyWithDifferentPayloadIsRejectedWithoutAnotherReservation() {
        when(paymentProviderClient.createPreference(any(), anyString()))
                .thenReturn(new CheckoutPreferenceResponse(
                        "preference-123", "https://checkout.example.com", "collector-123"));
        transactionService.create(
                "payload-conflict-key", new TransactionCreateRequest("Guest User", "(11) 99999-9999", 1));

        assertThatThrownBy(() -> transactionService.create(
                        "payload-conflict-key", new TransactionCreateRequest("Guest User", "(11) 99999-9999", 2)))
                .isInstanceOf(IdempotencyConflictException.class);
        assertThat(transactionRepository.count()).isEqualTo(1);
        assertThat(capacityReservationRepository.count()).isEqualTo(1);
        RaffleCapacity capacity =
                raffleCapacityRepository.findById(RaffleCapacity.SINGLETON_ID).orElseThrow();
        assertThat(capacity.getReservedQuantity()).isEqualTo(1);
    }

    private static <T> List<T> runConcurrently(int count, ThrowingSupplier<T> supplier) throws Exception {
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(count);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return supplier.get();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(20, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
