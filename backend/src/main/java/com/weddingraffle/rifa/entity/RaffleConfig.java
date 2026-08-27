package com.weddingraffle.rifa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "raffle_config")
public class RaffleConfig {

    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    private OffsetDateTime scheduledDrawAt;

    private OffsetDateTime weddingEventAt;

    @Column(insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    protected RaffleConfig() {}

    public RaffleConfig(BigDecimal unitPrice) {
        this.id = SINGLETON_ID;
        this.unitPrice = unitPrice;
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public OffsetDateTime getScheduledDrawAt() {
        return scheduledDrawAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getWeddingEventAt() {
        return weddingEventAt;
    }

    public void updateUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public void updateScheduledDrawAt(OffsetDateTime scheduledDrawAt) {
        this.scheduledDrawAt = scheduledDrawAt;
    }

    public void updateWeddingEventAt(OffsetDateTime weddingEventAt) {
        this.weddingEventAt = weddingEventAt;
    }
}
