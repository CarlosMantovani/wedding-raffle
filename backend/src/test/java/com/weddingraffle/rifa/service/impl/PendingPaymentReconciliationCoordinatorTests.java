package com.weddingraffle.rifa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weddingraffle.rifa.entity.PaymentStatus;
import com.weddingraffle.rifa.entity.Transaction;
import com.weddingraffle.rifa.exception.ExternalPaymentException;
import com.weddingraffle.rifa.exception.ExternalPaymentException.FailureType;
import com.weddingraffle.rifa.integration.PaymentProviderClient;
import com.weddingraffle.rifa.integration.PaymentProviderPayment;
import com.weddingraffle.rifa.service.PaymentReconciliationService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PendingPaymentReconciliationCoordinatorTests {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-24T15:00:00Z"), ZoneOffset.UTC);

    @Mock
    private PaymentReconciliationLeaseService leaseService;

    @Mock
    private PaymentProviderClient paymentProviderClient;

    @Mock
    private PaymentReconciliationService paymentReconciliationService;

    private PendingPaymentReconciliationCoordinator coordinator;
    private Transaction transaction;

    @BeforeEach
    void setUp() {
        coordinator = coordinator();
        transaction = pendingTransaction();
        when(leaseService.tryAcquire(isNull(), any(), any())).thenReturn(true);
    }

    @Test
    void normalPollPerformsOneExternalReconciliationAndReleasesTheLease() {
        PaymentProviderPayment payment = payment("pending");
        when(paymentProviderClient.getPayment("payment-123")).thenReturn(payment);

        assertThat(coordinator.reconcileIfDue(transaction)).isTrue();

        verify(paymentProviderClient).getPayment("payment-123");
        verify(paymentReconciliationService).reconcile("payment-123", "external-reference-123", payment);
        verify(leaseService).release(isNull(), any());
    }

    @Test
    void coalescedPollReturnsPersistedStateWithoutCallingMercadoPago() {
        when(leaseService.tryAcquire(isNull(), any(), any())).thenReturn(false);

        assertThat(coordinator.reconcileIfDue(transaction)).isFalse();

        verify(paymentProviderClient, never()).getPayment(any());
        verify(paymentReconciliationService, never()).reconcile(any(), any(), any());
        verify(leaseService, never()).release(any(), any());
    }

    @ParameterizedTest(name = "{0} simultaneous polls perform one external reconciliation")
    @ValueSource(ints = {10, 50})
    void simultaneousPollsPerformOnlyOneExternalReconciliation(int workers) throws Exception {
        AtomicBoolean claimed = new AtomicBoolean();
        when(leaseService.tryAcquire(isNull(), any(), any()))
                .thenAnswer(invocation -> claimed.compareAndSet(false, true));
        when(paymentProviderClient.getPayment("payment-123")).thenReturn(payment("pending"));

        runConcurrently(workers, () -> coordinator.reconcileIfDue(transaction));

        verify(paymentProviderClient, times(1)).getPayment("payment-123");
        verify(paymentReconciliationService, times(1)).reconcile(any(), any(), any());
    }

    @Test
    void separateCoordinatorInstancesShareTheDurableSingleFlightDecision() {
        AtomicBoolean claimed = new AtomicBoolean();
        when(leaseService.tryAcquire(isNull(), any(), any()))
                .thenAnswer(invocation -> claimed.compareAndSet(false, true));
        when(paymentProviderClient.getPayment("payment-123")).thenReturn(payment("pending"));
        PendingPaymentReconciliationCoordinator secondCoordinator = coordinator();

        assertThat(coordinator.reconcileIfDue(transaction)).isTrue();
        assertThat(secondCoordinator.reconcileIfDue(transaction)).isFalse();

        verify(paymentProviderClient, times(1)).getPayment("payment-123");
    }

    @Test
    void timeoutDoesNotChangeThePaymentStateAndReleasesTheLease() {
        assertExternalFailureIsPropagated(
                new ExternalPaymentException("timeout", null, FailureType.TRANSIENT, null, null, false, true, false));
    }

    @Test
    void rateLimitDoesNotChangeThePaymentStateAndReleasesTheLease() {
        assertExternalFailureIsPropagated(new ExternalPaymentException(
                "rate limited", null, FailureType.TRANSIENT, 429, 1_000L, true, true, true));
    }

    @Test
    void serverFailureDoesNotChangeThePaymentStateAndReleasesTheLease() {
        assertExternalFailureIsPropagated(new ExternalPaymentException(
                "server failure", null, FailureType.TRANSIENT, 503, null, true, true, true));
    }

    @Test
    void openCircuitDoesNotChangeThePaymentStateAndReleasesTheLease() {
        assertExternalFailureIsPropagated(new ExternalPaymentException(
                "circuit open", null, FailureType.TRANSIENT, null, null, false, false, false));
    }

    @Test
    void reconciliationRecoversOnALaterAttemptAfterAnExternalFailure() {
        ExternalPaymentException failure =
                new ExternalPaymentException("timeout", null, FailureType.TRANSIENT, null, null, false, true, false);
        PaymentProviderPayment payment = payment("approved");
        when(paymentProviderClient.getPayment("payment-123")).thenThrow(failure).thenReturn(payment);

        assertThatThrownBy(() -> coordinator.reconcileIfDue(transaction)).isSameAs(failure);
        assertThat(coordinator.reconcileIfDue(transaction)).isTrue();

        verify(paymentProviderClient, times(2)).getPayment("payment-123");
        verify(paymentReconciliationService).reconcile("payment-123", "external-reference-123", payment);
        verify(leaseService, times(2)).release(isNull(), any());
    }

    private void assertExternalFailureIsPropagated(ExternalPaymentException failure) {
        when(paymentProviderClient.getPayment("payment-123")).thenThrow(failure);

        assertThatThrownBy(() -> coordinator.reconcileIfDue(transaction)).isSameAs(failure);

        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(paymentReconciliationService, never()).reconcile(any(), any(), any());
        verify(leaseService).release(isNull(), any());
    }

    private PendingPaymentReconciliationCoordinator coordinator() {
        return new PendingPaymentReconciliationCoordinator(
                leaseService, paymentProviderClient, paymentReconciliationService, CLOCK);
    }

    private static Transaction pendingTransaction() {
        Transaction pending = new Transaction(
                "guest@example.com", 2, new BigDecimal("20.00"), PaymentStatus.PENDING, "external-reference-123");
        pending.markPaymentState(
                PaymentStatus.PENDING, "payment-123", OffsetDateTime.parse("2026-08-24T14:00:00Z"), (short) 10, 1L);
        return pending;
    }

    private static PaymentProviderPayment payment(String status) {
        return new PaymentProviderPayment(
                "payment-123",
                "external-reference-123",
                "external-reference-123",
                "preference-123",
                "collector-123",
                new BigDecimal("20.00"),
                "BRL",
                status,
                status,
                OffsetDateTime.parse("2026-08-24T13:00:00Z"),
                OffsetDateTime.parse("2026-08-24T14:00:00Z"));
    }

    private static void runConcurrently(int workers, Runnable action) throws Exception {
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(workers);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < workers; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    action.run();
                    return null;
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
