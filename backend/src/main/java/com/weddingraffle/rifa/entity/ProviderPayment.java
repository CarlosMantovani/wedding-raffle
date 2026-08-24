package com.weddingraffle.rifa.entity;

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
import java.time.OffsetDateTime;

@Entity
@Table(name = "provider_payment")
public class ProviderPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PaymentProviderName provider;

    @Column(nullable = false)
    private String providerPaymentId;

    @ManyToOne
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    @Column(insertable = false, updatable = false)
    private OffsetDateTime firstSeenAt;

    protected ProviderPayment() {}

    public Long getId() {
        return id;
    }

    public PaymentProviderName getProvider() {
        return provider;
    }

    public String getProviderPaymentId() {
        return providerPaymentId;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public OffsetDateTime getFirstSeenAt() {
        return firstSeenAt;
    }

    public void linkTo(Transaction transaction) {
        if (this.transaction != null && !this.transaction.getId().equals(transaction.getId())) {
            throw new IllegalStateException("Provider payment is already linked to another transaction.");
        }
        this.transaction = transaction;
    }
}
