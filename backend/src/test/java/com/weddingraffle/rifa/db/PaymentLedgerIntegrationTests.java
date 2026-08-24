package com.weddingraffle.rifa.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.weddingraffle.rifa.dto.TransactionCreateRequest;
import com.weddingraffle.rifa.dto.TransactionCreateResponse;
import com.weddingraffle.rifa.entity.PaymentEvent;
import com.weddingraffle.rifa.entity.PaymentEventProcessingStatus;
import com.weddingraffle.rifa.entity.PaymentEventReconciliationStatus;
import com.weddingraffle.rifa.entity.PaymentStatus;
import com.weddingraffle.rifa.entity.ProviderPayment;
import com.weddingraffle.rifa.entity.PurchaseIntentStatus;
import com.weddingraffle.rifa.entity.RaffleCapacity;
import com.weddingraffle.rifa.integration.CheckoutPreferenceResponse;
import com.weddingraffle.rifa.integration.PaymentProviderClient;
import com.weddingraffle.rifa.integration.PaymentProviderPayment;
import com.weddingraffle.rifa.repository.LuckyNumberRepository;
import com.weddingraffle.rifa.repository.PaymentEventRepository;
import com.weddingraffle.rifa.repository.ProviderPaymentRepository;
import com.weddingraffle.rifa.repository.PurchaseIntentRepository;
import com.weddingraffle.rifa.repository.RaffleCapacityRepository;
import com.weddingraffle.rifa.repository.TransactionRepository;
import com.weddingraffle.rifa.service.PaymentReconciliationService;
import com.weddingraffle.rifa.service.TransactionService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PaymentLedgerIntegrationTests {

    private static final String ADMIN_PASSWORD_HASH = "$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
    private static final String PREFERENCE_ID = "preference-123";
    private static final String COLLECTOR_ID = "collector-123";
    private static final OffsetDateTime T1 = OffsetDateTime.parse("2026-08-22T11:00:00Z");
    private static final OffsetDateTime T2 = OffsetDateTime.parse("2026-08-22T12:00:00Z");
    private static final OffsetDateTime T3 = OffsetDateTime.parse("2026-08-22T13:00:00Z");
    private static final OffsetDateTime T4 = OffsetDateTime.parse("2026-08-22T14:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("payment_ledger")
            .withUsername("payment_ledger")
            .withPassword("payment_ledger");

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private PaymentReconciliationService paymentReconciliationService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private ProviderPaymentRepository providerPaymentRepository;

    @Autowired
    private PaymentEventRepository paymentEventRepository;

    @Autowired
    private LuckyNumberRepository luckyNumberRepository;

    @Autowired
    private PurchaseIntentRepository purchaseIntentRepository;

    @Autowired
    private RaffleCapacityRepository raffleCapacityRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
                "TRUNCATE TABLE payment_event, provider_payment, purchase_intent, lucky_number, capacity_reservation, raffle_draw, transaction RESTART IDENTITY CASCADE");
        jdbcTemplate.update("UPDATE raffle_capacity SET reserved_quantity = 0, allocated_quantity = 0 WHERE id = 1");
        when(paymentProviderClient.createPreference(any(), anyString()))
                .thenReturn(
                        new CheckoutPreferenceResponse(PREFERENCE_ID, "https://checkout.example.com", COLLECTOR_ID));
    }

    @Test
    void matchingAmountCurrencyAndIdentityReleaseNumbers() {
        TransactionCreateResponse created = createTransaction(2);

        process(payment("payment-1", created.externalReference(), "approved", T2));

        assertThat(transaction(created).getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(luckyNumberRepository.count()).isEqualTo(2);
        assertThat(paymentEventRepository.findAll()).singleElement().satisfies(event -> {
            assertThat(event.getReconciliationStatus()).isEqualTo(PaymentEventReconciliationStatus.MATCHED);
            assertThat(event.getProcessingStatus()).isEqualTo(PaymentEventProcessingStatus.APPLIED);
        });
    }

    @Test
    void divergentAmountDoesNotReleaseNumbersOrConsumeTheReservation() {
        TransactionCreateResponse created = createTransaction(2);
        PaymentProviderPayment payment = payment(
                "payment-1",
                created.externalReference(),
                created.externalReference(),
                PREFERENCE_ID,
                COLLECTOR_ID,
                new BigDecimal("19.99"),
                "BRL",
                "approved",
                T2);

        process(payment);

        assertRejected(created, "AMOUNT_MISMATCH");
        RaffleCapacity capacity =
                raffleCapacityRepository.findById(RaffleCapacity.SINGLETON_ID).orElseThrow();
        assertThat(capacity.getReservedQuantity()).isEqualTo(2);
        assertThat(capacity.getAllocatedQuantity()).isZero();
    }

    @Test
    void divergentCurrencyDoesNotReleaseNumbers() {
        TransactionCreateResponse created = createTransaction(2);
        PaymentProviderPayment payment = payment(
                "payment-1",
                created.externalReference(),
                created.externalReference(),
                PREFERENCE_ID,
                COLLECTOR_ID,
                new BigDecimal("20.00"),
                "USD",
                "approved",
                T2);

        process(payment);

        assertRejected(created, "CURRENCY_MISMATCH");
    }

    @Test
    void divergentExternalReferenceDoesNotReleaseNumbers() {
        TransactionCreateResponse created = createTransaction(2);
        PaymentProviderPayment payment = payment(
                "payment-1",
                "different-reference",
                created.externalReference(),
                PREFERENCE_ID,
                COLLECTOR_ID,
                new BigDecimal("20.00"),
                "BRL",
                "approved",
                T2);

        paymentReconciliationService.reconcile("payment-1", created.externalReference(), payment);

        assertRejected(created, "EXTERNAL_REFERENCE_MISMATCH");
    }

    @Test
    void divergentPreferenceDoesNotReleaseNumbers() {
        TransactionCreateResponse created = createTransaction(2);
        PaymentProviderPayment payment = payment(
                "payment-1",
                created.externalReference(),
                created.externalReference(),
                "different-preference",
                COLLECTOR_ID,
                new BigDecimal("20.00"),
                "BRL",
                "approved",
                T2);

        process(payment);

        assertRejected(created, "PREFERENCE_ID_MISMATCH");
    }

    @Test
    void divergentCollectorDoesNotReleaseNumbers() {
        TransactionCreateResponse created = createTransaction(2);
        PaymentProviderPayment payment = payment(
                "payment-1",
                created.externalReference(),
                created.externalReference(),
                PREFERENCE_ID,
                "different-collector",
                new BigDecimal("20.00"),
                "BRL",
                "approved",
                T2);

        process(payment);

        assertRejected(created, "COLLECTOR_ID_MISMATCH");
    }

    @Test
    void divergentPaymentIdDoesNotReleaseNumbers() {
        TransactionCreateResponse created = createTransaction(2);
        PaymentProviderPayment payment = payment("provider-payment", created.externalReference(), "approved", T2);

        paymentReconciliationService.reconcile("requested-payment", created.externalReference(), payment);

        assertRejected(created, "PAYMENT_ID_MISMATCH");
    }

    @Test
    void oneProviderPaymentIdCannotBeLinkedToTwoTransactions() {
        TransactionCreateResponse first = createTransaction("checkout-key-1", 2);
        TransactionCreateResponse second = createTransaction("checkout-key-2", 2);
        process(payment("payment-1", first.externalReference(), "pending", T1));
        PaymentProviderPayment conflicting = payment(
                "payment-1",
                second.externalReference(),
                second.externalReference(),
                PREFERENCE_ID,
                COLLECTOR_ID,
                new BigDecimal("20.00"),
                "BRL",
                "approved",
                T2);

        paymentReconciliationService.reconcile("payment-1", second.externalReference(), conflicting);

        assertThat(providerPaymentRepository.count()).isEqualTo(1);
        assertThat(transaction(second).getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(latestEvent().getFailureReasons()).contains("PAYMENT_ID_ALREADY_LINKED");
    }

    @Test
    void identicalEventDeliveredThreeTimesIsAppliedOnceAndCountsDeliveries() {
        TransactionCreateResponse created = createTransaction(2);
        PaymentProviderPayment approved = payment("payment-1", created.externalReference(), "approved", T2);

        process(approved);
        process(approved);
        process(approved);

        assertThat(providerPaymentRepository.count()).isEqualTo(1);
        assertThat(paymentEventRepository.findAll())
                .singleElement()
                .extracting(PaymentEvent::getDeliveryCount)
                .isEqualTo(3);
        assertThat(luckyNumberRepository.count()).isEqualTo(2);
    }

    @Test
    void approvedFollowedByRefundedKeepsNumbersButMakesThemIneligible() {
        TransactionCreateResponse created = createTransaction(2);
        process(payment("payment-1", created.externalReference(), "approved", T1));

        process(payment("payment-1", created.externalReference(), "refunded", T2));

        assertThat(transaction(created).getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(luckyNumberRepository.count()).isEqualTo(2);
        assertThat(paymentEventRepository.count()).isEqualTo(2);
    }

    @Test
    void oldApprovedAfterRefundedCannotRestoreEligibility() {
        TransactionCreateResponse created = createTransaction(2);
        process(payment("payment-1", created.externalReference(), "approved", T1));
        process(payment("payment-1", created.externalReference(), "refunded", T3));

        process(payment("payment-1", created.externalReference(), "approved", T2));

        assertThat(transaction(created).getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(latestEvent().getProcessingStatus()).isEqualTo(PaymentEventProcessingStatus.OBSOLETE);
    }

    @Test
    void approvedFollowedByChargedBackMakesNumbersIneligible() {
        TransactionCreateResponse created = createTransaction(2);
        process(payment("payment-1", created.externalReference(), "approved", T1));

        process(payment("payment-1", created.externalReference(), "charged_back", T2));

        assertThat(transaction(created).getStatus()).isEqualTo(PaymentStatus.CHARGED_BACK);
        assertThat(luckyNumberRepository.count()).isEqualTo(2);
    }

    @Test
    void approvedFollowedByMediationMakesNumbersIneligible() {
        TransactionCreateResponse created = createTransaction(2);
        process(payment("payment-1", created.externalReference(), "approved", T1));

        process(payment("payment-1", created.externalReference(), "in_mediation", T2));

        assertThat(transaction(created).getStatus()).isEqualTo(PaymentStatus.IN_MEDIATION);
        assertThat(luckyNumberRepository.count()).isEqualTo(2);
    }

    @Test
    void differentArrivalOrdersConvergeToTheNewestProviderState() {
        TransactionCreateResponse chronological = createTransaction("checkout-key-1", 2);
        process(payment("payment-1", chronological.externalReference(), "approved", T1));
        process(payment("payment-1", chronological.externalReference(), "in_mediation", T2));
        process(payment("payment-1", chronological.externalReference(), "charged_back", T3));

        TransactionCreateResponse shuffled = createTransaction("checkout-key-2", 2);
        process(payment("payment-2", shuffled.externalReference(), "charged_back", T3));
        process(payment("payment-2", shuffled.externalReference(), "approved", T1));
        process(payment("payment-2", shuffled.externalReference(), "in_mediation", T2));

        assertThat(transaction(chronological).getStatus()).isEqualTo(PaymentStatus.CHARGED_BACK);
        assertThat(transaction(shuffled).getStatus()).isEqualTo(PaymentStatus.CHARGED_BACK);
    }

    @Test
    void webhookAndPollingOfTheSamePaymentEventAreIdempotent() throws Exception {
        TransactionCreateResponse created = createTransaction(2);
        process(payment("payment-1", created.externalReference(), "pending", T1));
        PaymentProviderPayment approved = payment("payment-1", created.externalReference(), "approved", T2);
        CountDownLatch bothFetched = new CountDownLatch(2);
        when(paymentProviderClient.getPayment("payment-1")).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .isFalse();
            bothFetched.countDown();
            assertThat(bothFetched.await(10, TimeUnit.SECONDS)).isTrue();
            return approved;
        });

        runConcurrently(
                () -> transactionService.processPaymentNotification("payment-1"),
                () -> transactionService.getStatus(created.externalReference()));

        assertThat(transaction(created).getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(luckyNumberRepository.count()).isEqualTo(2);
        assertThat(paymentEventRepository.findAll().stream()
                        .filter(event -> "approved".equals(event.getProviderStatus())))
                .singleElement()
                .extracting(PaymentEvent::getDeliveryCount)
                .isEqualTo(2);
    }

    @Test
    void delayedOldProcessingCannotRestoreAStateCommittedByANewerEvent() throws Exception {
        TransactionCreateResponse created = createTransaction(2);
        process(payment("payment-1", created.externalReference(), "approved", T1));
        PaymentProviderPayment oldApproved = payment("payment-1", created.externalReference(), "approved", T2);
        PaymentProviderPayment newerRefund = payment("payment-1", created.externalReference(), "refunded", T3);
        CountDownLatch oldFetchStarted = new CountDownLatch(1);
        CountDownLatch releaseOldFetch = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        when(paymentProviderClient.getPayment("payment-1")).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                oldFetchStarted.countDown();
                assertThat(releaseOldFetch.await(10, TimeUnit.SECONDS)).isTrue();
                return oldApproved;
            }
            return newerRefund;
        });
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> delayed = executor.submit(() -> transactionService.processPaymentNotification("payment-1"));
            assertThat(oldFetchStarted.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> newer = executor.submit(() -> transactionService.processPaymentNotification("payment-1"));
            newer.get(10, TimeUnit.SECONDS);
            releaseOldFetch.countDown();
            delayed.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(transaction(created).getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(paymentEventRepository.findAll().stream()
                        .filter(event -> event.getProviderUpdatedAt().equals(T2)))
                .singleElement()
                .extracting(PaymentEvent::getProcessingStatus)
                .isEqualTo(PaymentEventProcessingStatus.OBSOLETE);
    }

    @Test
    void historyPreservesAllPaymentIdsAndDistinctProviderEvents() {
        TransactionCreateResponse created = createTransaction(2);
        process(payment("payment-1", created.externalReference(), "pending", T1));
        process(payment("payment-1", created.externalReference(), "approved", T2));
        process(payment("payment-2", created.externalReference(), "approved", T3));

        List<ProviderPayment> payments = providerPaymentRepository.findByTransactionIdOrderById(
                transaction(created).getId());
        assertThat(payments)
                .extracting(ProviderPayment::getProviderPaymentId)
                .containsExactly("payment-1", "payment-2");
        assertThat(paymentEventRepository.count()).isEqualTo(3);
        assertThat(luckyNumberRepository.count()).isEqualTo(2);
        assertThat(purchaseIntentRepository.findAll())
                .singleElement()
                .extracting(intent -> intent.getStatus())
                .isEqualTo(PurchaseIntentStatus.COMPLETED);
    }

    @Test
    void databaseConstraintsRejectDuplicatePaymentAndEventIdentities() {
        TransactionCreateResponse created = createTransaction(2);
        process(payment("payment-1", created.externalReference(), "pending", T1));
        ProviderPayment providerPayment = providerPaymentRepository.findAll().getFirst();
        PaymentEvent event = paymentEventRepository.findAll().getFirst();

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "insert into provider_payment (provider, provider_payment_id, transaction_id) values ('MERCADO_PAGO', ?, ?)",
                        providerPayment.getProviderPaymentId(),
                        transaction(created).getId()))
                .isInstanceOf(DuplicateKeyException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                        "insert into payment_event (provider_payment_id, event_key) values (?, ?)",
                        providerPayment.getId(),
                        event.getEventKey()))
                .isInstanceOf(DuplicateKeyException.class);
    }

    private TransactionCreateResponse createTransaction(int quantity) {
        return createTransaction("checkout-key", quantity);
    }

    private TransactionCreateResponse createTransaction(String key, int quantity) {
        return transactionService.create(key, new TransactionCreateRequest("Guest User", "(11) 99999-9999", quantity));
    }

    private void process(PaymentProviderPayment payment) {
        when(paymentProviderClient.getPayment(payment.paymentId())).thenReturn(payment);
        transactionService.processPaymentNotification(payment.paymentId());
    }

    private com.weddingraffle.rifa.entity.Transaction transaction(TransactionCreateResponse response) {
        return transactionRepository
                .findByExternalReference(response.externalReference())
                .orElseThrow();
    }

    private void assertRejected(TransactionCreateResponse created, String failure) {
        assertThat(transaction(created).getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(luckyNumberRepository.count()).isZero();
        assertThat(latestEvent().getReconciliationStatus()).isEqualTo(PaymentEventReconciliationStatus.MISMATCHED);
        assertThat(latestEvent().getProcessingStatus()).isEqualTo(PaymentEventProcessingStatus.REJECTED);
        assertThat(latestEvent().getFailureReasons()).contains(failure);
    }

    private PaymentEvent latestEvent() {
        return paymentEventRepository.findAll().stream()
                .max(java.util.Comparator.comparing(PaymentEvent::getId))
                .orElseThrow();
    }

    private static PaymentProviderPayment payment(
            String paymentId, String externalReference, String status, OffsetDateTime updatedAt) {
        BigDecimal amount = new BigDecimal("20.00");
        return payment(
                paymentId,
                externalReference,
                externalReference,
                PREFERENCE_ID,
                COLLECTOR_ID,
                amount,
                "BRL",
                status,
                updatedAt);
    }

    private static PaymentProviderPayment payment(
            String paymentId,
            String externalReference,
            String orderExternalReference,
            String preferenceId,
            String collectorId,
            BigDecimal amount,
            String currency,
            String status,
            OffsetDateTime updatedAt) {
        return new PaymentProviderPayment(
                paymentId,
                externalReference,
                orderExternalReference,
                preferenceId,
                collectorId,
                amount,
                currency,
                status,
                status,
                T1,
                updatedAt);
    }

    private static void runConcurrently(ThrowingRunnable first, ThrowingRunnable second) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> futures = new ArrayList<>();
            futures.add(executor.submit(() -> run(first)));
            futures.add(executor.submit(() -> run(second)));
            for (Future<?> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static void run(ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
