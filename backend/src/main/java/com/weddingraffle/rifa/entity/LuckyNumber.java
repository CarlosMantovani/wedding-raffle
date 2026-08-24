package com.weddingraffle.rifa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "lucky_number")
public class LuckyNumber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String number;

    @Column(length = 320)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @Column(nullable = false)
    private Integer allocationIndex;

    @Column(insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected LuckyNumber() {}

    public LuckyNumber(String number, String email, Transaction transaction) {
        this(number, email, transaction, null);
    }

    public LuckyNumber(String number, String email, Transaction transaction, Integer allocationIndex) {
        this.number = number;
        this.email = email;
        this.transaction = transaction;
        this.allocationIndex = allocationIndex;
    }

    public Long getId() {
        return id;
    }

    public String getNumber() {
        return number;
    }

    public String getEmail() {
        return email;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public Integer getAllocationIndex() {
        return allocationIndex;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
