package com.weddingraffle.rifa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity(name = "RaffleTransaction")
@Table(name = "transaction")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 320)
    private String email;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "transaction_status")
    private PaymentStatus status = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "payment_method")
    private PaymentMethod paymentMethod = PaymentMethod.MERCADO_PAGO;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private CapacityReviewStatus capacityReviewStatus;

    @Column(nullable = false, unique = true)
    private String externalReference;

    @Column(nullable = false, length = 4)
    private String recoveryCode;

    private String mpPaymentId;

    private String mpPreferenceId;

    private String mpCollectorId;

    @Column(length = 2048)
    private String mpCheckoutUrl;

    private OffsetDateTime paymentStateUpdatedAt;

    private Short paymentStatePriority;

    private Long currentPaymentEventId;

    private OffsetDateTime luckyNumbersGeneratedAt;

    private OffsetDateTime paymentReconciliationAttemptedAt;

    private OffsetDateTime paymentReconciliationLeaseUntil;

    private UUID paymentReconciliationLeaseToken;

    @Column(nullable = false, length = 50)
    private String participantFlagCode;

    @Column(nullable = false, length = 100)
    private String participantFlagName;

    @Column(nullable = false, length = 20)
    private String participantFlagEmoji;

    @Column(insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    protected Transaction() {}

    public Transaction(
            String email, Integer quantity, BigDecimal totalAmount, PaymentStatus status, String externalReference) {
        this(
                email,
                "0000000000",
                email,
                quantity,
                inferUnitPrice(totalAmount, quantity),
                totalAmount,
                status,
                PaymentMethod.MERCADO_PAGO,
                externalReference);
    }

    public Transaction(
            String name,
            String phone,
            String email,
            Integer quantity,
            BigDecimal totalAmount,
            PaymentStatus status,
            PaymentMethod paymentMethod,
            String externalReference) {
        this(
                name,
                phone,
                email,
                quantity,
                inferUnitPrice(totalAmount, quantity),
                totalAmount,
                status,
                paymentMethod,
                externalReference);
    }

    public Transaction(
            String name,
            String phone,
            String email,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal totalAmount,
            PaymentStatus status,
            PaymentMethod paymentMethod,
            String externalReference) {
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalAmount = totalAmount;
        this.status = status;
        this.paymentMethod = paymentMethod;
        this.externalReference = externalReference;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public CapacityReviewStatus getCapacityReviewStatus() {
        return capacityReviewStatus;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public String getRecoveryCode() {
        return recoveryCode;
    }

    public String getMpPaymentId() {
        return mpPaymentId;
    }

    public String getMpPreferenceId() {
        return mpPreferenceId;
    }

    public String getMpCollectorId() {
        return mpCollectorId;
    }

    public String getMpCheckoutUrl() {
        return mpCheckoutUrl;
    }

    public OffsetDateTime getPaymentStateUpdatedAt() {
        return paymentStateUpdatedAt;
    }

    public Short getPaymentStatePriority() {
        return paymentStatePriority;
    }

    public Long getCurrentPaymentEventId() {
        return currentPaymentEventId;
    }

    public OffsetDateTime getLuckyNumbersGeneratedAt() {
        return luckyNumbersGeneratedAt;
    }

    public OffsetDateTime getPaymentReconciliationAttemptedAt() {
        return paymentReconciliationAttemptedAt;
    }

    public OffsetDateTime getPaymentReconciliationLeaseUntil() {
        return paymentReconciliationLeaseUntil;
    }

    public UUID getPaymentReconciliationLeaseToken() {
        return paymentReconciliationLeaseToken;
    }

    public String getParticipantFlagCode() {
        return participantFlagCode;
    }

    public String getParticipantFlagName() {
        return participantFlagName;
    }

    public String getParticipantFlagEmoji() {
        return participantFlagEmoji;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void markPaymentState(
            PaymentStatus status,
            String mpPaymentId,
            OffsetDateTime paymentStateUpdatedAt,
            short paymentStatePriority,
            Long currentPaymentEventId) {
        this.status = status;
        this.mpPaymentId = mpPaymentId;
        this.paymentStateUpdatedAt = paymentStateUpdatedAt;
        this.paymentStatePriority = paymentStatePriority;
        this.currentPaymentEventId = currentPaymentEventId;
    }

    public void assignPreference(String mpPreferenceId, String mpCheckoutUrl, String mpCollectorId) {
        this.mpPreferenceId = mpPreferenceId;
        this.mpCheckoutUrl = mpCheckoutUrl;
        this.mpCollectorId = mpCollectorId;
    }

    public boolean hasCompletedLuckyNumberBatch() {
        return luckyNumbersGeneratedAt != null;
    }

    public void markLuckyNumberBatchCompleted(OffsetDateTime completedAt) {
        if (luckyNumbersGeneratedAt == null) {
            luckyNumbersGeneratedAt = completedAt;
        }
    }

    public void assignRecoveryCode(String recoveryCode) {
        this.recoveryCode = recoveryCode;
    }

    public void assignParticipantFlag(ParticipantFlag participantFlag) {
        this.participantFlagCode = participantFlag.code();
        this.participantFlagName = participantFlag.name();
        this.participantFlagEmoji = participantFlag.emoji();
    }

    public void markCapacityReviewPending() {
        capacityReviewStatus = CapacityReviewStatus.PENDING;
    }

    public void completeCapacityReview(CapacityReviewStatus resolution) {
        if (capacityReviewStatus != CapacityReviewStatus.PENDING
                || resolution == null
                || resolution == CapacityReviewStatus.PENDING) {
            throw new IllegalStateException("Invalid capacity review transition.");
        }
        capacityReviewStatus = resolution;
    }

    private static BigDecimal inferUnitPrice(BigDecimal totalAmount, Integer quantity) {
        return totalAmount.divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP);
    }
}
