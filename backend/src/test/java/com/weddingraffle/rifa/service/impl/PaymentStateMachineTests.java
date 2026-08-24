package com.weddingraffle.rifa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.weddingraffle.rifa.entity.PaymentStatus;
import com.weddingraffle.rifa.entity.Transaction;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class PaymentStateMachineTests {

    private final PaymentStateMachine stateMachine = new PaymentStateMachine();

    @Test
    void olderEventNeverRestoresAnObsoleteState() {
        Transaction transaction = transaction(PaymentStatus.REFUNDED);
        transaction.markPaymentState(
                PaymentStatus.REFUNDED,
                "payment-123",
                OffsetDateTime.parse("2026-08-22T13:00:00Z"),
                stateMachine.priority(PaymentStatus.REFUNDED),
                2L);

        assertThat(stateMachine.shouldApply(
                        transaction, PaymentStatus.APPROVED, OffsetDateTime.parse("2026-08-22T12:00:00Z")))
                .isFalse();
    }

    @Test
    void financialReversalWinsWhenProviderTimestampsAreEqual() {
        Transaction transaction = transaction(PaymentStatus.APPROVED);
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-08-22T13:00:00Z");
        transaction.markPaymentState(
                PaymentStatus.APPROVED, "payment-123", timestamp, stateMachine.priority(PaymentStatus.APPROVED), 1L);

        assertThat(stateMachine.shouldApply(transaction, PaymentStatus.IN_MEDIATION, timestamp))
                .isTrue();
        assertThat(stateMachine.shouldApply(transaction, PaymentStatus.REFUNDED, timestamp))
                .isTrue();
        assertThat(stateMachine.shouldApply(transaction, PaymentStatus.CHARGED_BACK, timestamp))
                .isTrue();
    }

    @Test
    void aGenuinelyNewerProviderStateCanMoveTheTransactionAgain() {
        Transaction transaction = transaction(PaymentStatus.IN_MEDIATION);
        transaction.markPaymentState(
                PaymentStatus.IN_MEDIATION,
                "payment-123",
                OffsetDateTime.parse("2026-08-22T13:00:00Z"),
                stateMachine.priority(PaymentStatus.IN_MEDIATION),
                2L);

        assertThat(stateMachine.shouldApply(
                        transaction, PaymentStatus.APPROVED, OffsetDateTime.parse("2026-08-22T14:00:00Z")))
                .isTrue();
    }

    private static Transaction transaction(PaymentStatus status) {
        return new Transaction("guest@example.com", 2, new BigDecimal("20.00"), status, "external-reference-123");
    }
}
