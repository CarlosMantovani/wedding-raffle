package com.weddingraffle.rifa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "raffle_capacity")
public class RaffleCapacity {

    public static final short SINGLETON_ID = 1;

    @Id
    private Short id;

    @Column(nullable = false)
    private Long totalCapacity;

    @Column(nullable = false)
    private Long reservedQuantity;

    @Column(nullable = false)
    private Long allocatedQuantity;

    protected RaffleCapacity() {}

    public RaffleCapacity(long totalCapacity, long reservedQuantity, long allocatedQuantity) {
        if (totalCapacity <= 0
                || reservedQuantity < 0
                || allocatedQuantity < 0
                || reservedQuantity + allocatedQuantity > totalCapacity) {
            throw new IllegalArgumentException("Invalid raffle capacity values.");
        }
        this.id = SINGLETON_ID;
        this.totalCapacity = totalCapacity;
        this.reservedQuantity = reservedQuantity;
        this.allocatedQuantity = allocatedQuantity;
    }

    public long getAvailableQuantity() {
        return totalCapacity - reservedQuantity - allocatedQuantity;
    }

    public long getReservedQuantity() {
        return reservedQuantity;
    }

    public long getAllocatedQuantity() {
        return allocatedQuantity;
    }

    public void reserve(int quantity) {
        ensureAvailable(quantity);
        reservedQuantity += quantity;
    }

    public void expire(int quantity) {
        ensureReserved(quantity);
        reservedQuantity -= quantity;
    }

    public void allocateReserved(int quantity) {
        ensureReserved(quantity);
        reservedQuantity -= quantity;
        allocatedQuantity += quantity;
    }

    public void allocateAvailable(int quantity) {
        ensureAvailable(quantity);
        allocatedQuantity += quantity;
    }

    public void releaseAllocated(int quantity) {
        if (quantity <= 0 || allocatedQuantity < quantity) {
            throw new IllegalStateException("Invalid allocated capacity release.");
        }
        allocatedQuantity -= quantity;
    }

    private void ensureAvailable(int quantity) {
        if (quantity <= 0 || getAvailableQuantity() < quantity) {
            throw new IllegalStateException("Insufficient raffle capacity.");
        }
    }

    private void ensureReserved(int quantity) {
        if (quantity <= 0 || reservedQuantity < quantity) {
            throw new IllegalStateException("Invalid reserved capacity transition.");
        }
    }
}
