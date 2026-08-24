package com.weddingraffle.rifa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "purchase_intent")
public class PurchaseIntent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PurchaseIntentAction action;

    @Column(nullable = false, length = 64)
    private String requestHash;

    @Column(nullable = false, unique = true)
    private String externalReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurchaseIntentStatus status = PurchaseIntentStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String responsePayload;

    @Column(insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    protected PurchaseIntent() {}

    public PurchaseIntent(
            String idempotencyKey, PurchaseIntentAction action, String requestHash, String externalReference) {
        this.idempotencyKey = idempotencyKey;
        this.action = action;
        this.requestHash = requestHash;
        this.externalReference = externalReference;
    }

    public Long getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public PurchaseIntentAction getAction() {
        return action;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public PurchaseIntentStatus getStatus() {
        return status;
    }

    public String getResponsePayload() {
        return responsePayload;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void complete(String responsePayload) {
        if (status == PurchaseIntentStatus.COMPLETED) {
            throw new IllegalStateException("Purchase intent is already completed.");
        }
        this.responsePayload = responsePayload;
        status = PurchaseIntentStatus.COMPLETED;
    }
}
