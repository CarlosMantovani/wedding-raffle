package com.weddingraffle.rifa.service.impl;

import com.weddingraffle.rifa.entity.PaymentStatus;
import com.weddingraffle.rifa.entity.Transaction;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PaymentStateMachine {

    public Optional<PaymentStatus> mapProviderStatus(String providerStatus) {
        if (!StringUtils.hasText(providerStatus)) {
            return Optional.empty();
        }
        return switch (providerStatus.toLowerCase(Locale.ROOT)) {
            case "pending", "in_process", "authorized" -> Optional.of(PaymentStatus.PENDING);
            case "approved" -> Optional.of(PaymentStatus.APPROVED);
            case "rejected" -> Optional.of(PaymentStatus.REJECTED);
            case "cancelled", "canceled" -> Optional.of(PaymentStatus.CANCELLED);
            case "refunded" -> Optional.of(PaymentStatus.REFUNDED);
            case "charged_back" -> Optional.of(PaymentStatus.CHARGED_BACK);
            case "in_mediation" -> Optional.of(PaymentStatus.IN_MEDIATION);
            default -> Optional.empty();
        };
    }

    public boolean shouldApply(Transaction transaction, PaymentStatus candidate, OffsetDateTime providerUpdatedAt) {
        OffsetDateTime currentUpdatedAt = transaction.getPaymentStateUpdatedAt();
        if (currentUpdatedAt == null) {
            return true;
        }

        int timestampComparison = providerUpdatedAt.compareTo(currentUpdatedAt);
        if (timestampComparison != 0) {
            return timestampComparison > 0;
        }

        short currentPriority = transaction.getPaymentStatePriority() != null
                ? transaction.getPaymentStatePriority()
                : priority(transaction.getStatus());
        return priority(candidate) > currentPriority;
    }

    public short priority(PaymentStatus status) {
        return switch (status) {
            case PENDING -> 10;
            case REJECTED -> 20;
            case CANCELLED -> 30;
            case APPROVED -> 40;
            case IN_MEDIATION -> 50;
            case REFUNDED -> 60;
            case CHARGED_BACK -> 70;
        };
    }
}
