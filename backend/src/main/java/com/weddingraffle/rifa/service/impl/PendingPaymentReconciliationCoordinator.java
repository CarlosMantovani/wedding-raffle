package com.weddingraffle.rifa.service.impl;

import com.weddingraffle.rifa.entity.PaymentStatus;
import com.weddingraffle.rifa.entity.Transaction;
import com.weddingraffle.rifa.exception.ExternalPaymentException;
import com.weddingraffle.rifa.integration.PaymentProviderClient;
import com.weddingraffle.rifa.integration.PaymentProviderPayment;
import com.weddingraffle.rifa.service.PaymentReconciliationService;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PendingPaymentReconciliationCoordinator {

    private static final Logger LOGGER = LoggerFactory.getLogger(PendingPaymentReconciliationCoordinator.class);

    private final PaymentReconciliationLeaseService leaseService;
    private final PaymentProviderClient paymentProviderClient;
    private final PaymentReconciliationService paymentReconciliationService;
    private final Clock clock;

    public PendingPaymentReconciliationCoordinator(
            PaymentReconciliationLeaseService leaseService,
            PaymentProviderClient paymentProviderClient,
            PaymentReconciliationService paymentReconciliationService,
            Clock clock) {
        this.leaseService = leaseService;
        this.paymentProviderClient = paymentProviderClient;
        this.paymentReconciliationService = paymentReconciliationService;
        this.clock = clock;
    }

    public boolean reconcileIfDue(Transaction transaction) {
        if (transaction.getStatus() != PaymentStatus.PENDING || transaction.getMpPaymentId() == null) {
            return false;
        }

        UUID leaseToken = UUID.randomUUID();
        OffsetDateTime attemptedAt = OffsetDateTime.now(clock);
        if (!leaseService.tryAcquire(transaction.getId(), leaseToken, attemptedAt)) {
            LOGGER.debug(
                    "Coalesced payment status reconciliation or skipped minimum interval externalReference={}",
                    transaction.getExternalReference());
            return false;
        }

        long startedAtNanos = System.nanoTime();
        try {
            PaymentProviderPayment payment = paymentProviderClient.getPayment(transaction.getMpPaymentId());
            paymentReconciliationService.reconcile(
                    transaction.getMpPaymentId(), transaction.getExternalReference(), payment);
            return true;
        } catch (ExternalPaymentException exception) {
            LOGGER.warn(
                    "Pending payment reconciliation failed externalReference={} status={} failureType={} durationMillis={}",
                    transaction.getExternalReference(),
                    exception.getHttpStatus(),
                    exception.getFailureType(),
                    elapsedMillis(startedAtNanos));
            throw exception;
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Unexpected pending payment reconciliation failure externalReference={} durationMillis={}",
                    transaction.getExternalReference(),
                    elapsedMillis(startedAtNanos),
                    exception);
            throw exception;
        } finally {
            leaseService.release(transaction.getId(), leaseToken);
        }
    }

    private static long elapsedMillis(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }
}
