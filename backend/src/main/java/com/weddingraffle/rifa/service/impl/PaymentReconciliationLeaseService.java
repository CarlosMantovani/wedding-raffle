package com.weddingraffle.rifa.service.impl;

import com.weddingraffle.rifa.config.AppProperties;
import com.weddingraffle.rifa.config.PaymentStatusReconciliationProperties;
import com.weddingraffle.rifa.repository.TransactionRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentReconciliationLeaseService {

    private final TransactionRepository transactionRepository;
    private final PaymentStatusReconciliationProperties properties;

    public PaymentReconciliationLeaseService(
            TransactionRepository transactionRepository,
            PaymentStatusReconciliationProperties properties,
            AppProperties appProperties) {
        this.transactionRepository = transactionRepository;
        this.properties = properties;
        if (properties.leaseDurationMillis()
                <= appProperties.mercadoPago().http().callTimeoutMillis()) {
            throw new IllegalArgumentException(
                    "Payment reconciliation lease duration must exceed the Mercado Pago call timeout");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryAcquire(Long transactionId, UUID leaseToken, OffsetDateTime attemptedAt) {
        OffsetDateTime earliestAllowedAttempt =
                attemptedAt.minus(Duration.ofMillis(properties.minimumIntervalMillis()));
        OffsetDateTime leaseUntil = attemptedAt.plus(Duration.ofMillis(properties.leaseDurationMillis()));
        return transactionRepository.tryAcquirePaymentReconciliation(
                        transactionId, leaseToken, attemptedAt, earliestAllowedAttempt, leaseUntil)
                == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(Long transactionId, UUID leaseToken) {
        transactionRepository.releasePaymentReconciliation(transactionId, leaseToken);
    }
}
