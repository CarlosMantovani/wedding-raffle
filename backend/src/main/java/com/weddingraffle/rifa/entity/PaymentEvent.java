package com.weddingraffle.rifa.entity;

import com.weddingraffle.rifa.integration.PaymentProviderPayment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "payment_event")
public class PaymentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "provider_payment_id", nullable = false)
    private ProviderPayment providerPayment;

    @Column(nullable = false, unique = true, length = 128)
    private String eventKey;

    private String requestedPaymentId;

    @Column(length = 40)
    private String providerStatus;

    @Column(length = 100)
    private String statusDetail;

    private String externalReference;

    private String orderExternalReference;

    private String preferenceId;

    private String collectorId;

    @Column(precision = 19, scale = 2)
    private BigDecimal transactionAmount;

    @Column(length = 3)
    private String currencyId;

    private OffsetDateTime providerCreatedAt;

    private OffsetDateTime providerUpdatedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentEventReconciliationStatus reconciliationStatus = PaymentEventReconciliationStatus.RECEIVED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentEventProcessingStatus processingStatus = PaymentEventProcessingStatus.RECEIVED;

    @Column(length = 500)
    private String failureReasons;

    @Column(nullable = false)
    private Integer deliveryCount = 1;

    @Column(insertable = false, updatable = false)
    private OffsetDateTime firstReceivedAt;

    private OffsetDateTime lastReceivedAt;

    private OffsetDateTime processedAt;

    protected PaymentEvent() {}

    public PaymentEvent(
            ProviderPayment providerPayment,
            String eventKey,
            String requestedPaymentId,
            PaymentProviderPayment payment,
            OffsetDateTime receivedAt) {
        this.providerPayment = providerPayment;
        this.eventKey = eventKey;
        this.requestedPaymentId = requestedPaymentId;
        providerStatus = payment.status();
        statusDetail = payment.statusDetail();
        externalReference = payment.externalReference();
        orderExternalReference = payment.orderExternalReference();
        preferenceId = payment.preferenceId();
        collectorId = payment.collectorId();
        transactionAmount = payment.transactionAmount();
        currencyId = payment.currencyId();
        providerCreatedAt = payment.dateCreated();
        providerUpdatedAt = payment.dateLastUpdated();
        lastReceivedAt = receivedAt;
    }

    public Long getId() {
        return id;
    }

    public ProviderPayment getProviderPayment() {
        return providerPayment;
    }

    public String getEventKey() {
        return eventKey;
    }

    public String getRequestedPaymentId() {
        return requestedPaymentId;
    }

    public String getProviderStatus() {
        return providerStatus;
    }

    public String getStatusDetail() {
        return statusDetail;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public String getOrderExternalReference() {
        return orderExternalReference;
    }

    public String getPreferenceId() {
        return preferenceId;
    }

    public String getCollectorId() {
        return collectorId;
    }

    public BigDecimal getTransactionAmount() {
        return transactionAmount;
    }

    public String getCurrencyId() {
        return currencyId;
    }

    public OffsetDateTime getProviderCreatedAt() {
        return providerCreatedAt;
    }

    public OffsetDateTime getProviderUpdatedAt() {
        return providerUpdatedAt;
    }

    public PaymentEventReconciliationStatus getReconciliationStatus() {
        return reconciliationStatus;
    }

    public PaymentEventProcessingStatus getProcessingStatus() {
        return processingStatus;
    }

    public String getFailureReasons() {
        return failureReasons;
    }

    public Integer getDeliveryCount() {
        return deliveryCount;
    }

    public OffsetDateTime getFirstReceivedAt() {
        return firstReceivedAt;
    }

    public OffsetDateTime getLastReceivedAt() {
        return lastReceivedAt;
    }

    public OffsetDateTime getProcessedAt() {
        return processedAt;
    }

    public void registerDuplicate(OffsetDateTime receivedAt) {
        deliveryCount++;
        lastReceivedAt = receivedAt;
    }

    public void markRejected(String failureReasons, OffsetDateTime processedAt) {
        reconciliationStatus = PaymentEventReconciliationStatus.MISMATCHED;
        processingStatus = PaymentEventProcessingStatus.REJECTED;
        this.failureReasons = failureReasons;
        this.processedAt = processedAt;
    }

    public void markApplied(OffsetDateTime processedAt) {
        reconciliationStatus = PaymentEventReconciliationStatus.MATCHED;
        processingStatus = PaymentEventProcessingStatus.APPLIED;
        this.processedAt = processedAt;
    }

    public void markObsolete(OffsetDateTime processedAt) {
        reconciliationStatus = PaymentEventReconciliationStatus.MATCHED;
        processingStatus = PaymentEventProcessingStatus.OBSOLETE;
        this.processedAt = processedAt;
    }
}
