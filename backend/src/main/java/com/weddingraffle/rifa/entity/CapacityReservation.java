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
@Table(name = "capacity_reservation")
public class CapacityReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String externalReference;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CapacityReservationStatus status;

    @Column(nullable = false)
    private OffsetDateTime expiresAt;

    @Column(insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    protected CapacityReservation() {}

    public CapacityReservation(String externalReference, Integer quantity, OffsetDateTime expiresAt) {
        this.externalReference = externalReference;
        this.quantity = quantity;
        this.expiresAt = expiresAt;
        this.status = CapacityReservationStatus.ACTIVE;
    }

    public static CapacityReservation rejected(String externalReference, Integer quantity, OffsetDateTime occurredAt) {
        CapacityReservation reservation = new CapacityReservation(externalReference, quantity, occurredAt);
        reservation.status = CapacityReservationStatus.CAPACITY_REJECTED;
        return reservation;
    }

    public Long getId() {
        return id;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public CapacityReservationStatus getStatus() {
        return status;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void reactivate(OffsetDateTime newExpiresAt) {
        status = CapacityReservationStatus.ACTIVE;
        expiresAt = newExpiresAt;
    }

    public void markAllocated() {
        status = CapacityReservationStatus.ALLOCATED;
    }

    public void markExpired() {
        status = CapacityReservationStatus.EXPIRED;
    }

    public void markReleased() {
        status = CapacityReservationStatus.RELEASED;
    }

    public void markCapacityRejected() {
        status = CapacityReservationStatus.CAPACITY_REJECTED;
    }
}
