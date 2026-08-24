package com.weddingraffle.rifa.service.impl;

import com.weddingraffle.rifa.entity.CapacityReservation;
import com.weddingraffle.rifa.entity.CapacityReservationStatus;
import com.weddingraffle.rifa.entity.RaffleCapacity;
import com.weddingraffle.rifa.exception.InvalidRaffleStateException;
import com.weddingraffle.rifa.exception.InvalidTransactionStateException;
import com.weddingraffle.rifa.exception.ResourceNotFoundException;
import com.weddingraffle.rifa.repository.CapacityReservationRepository;
import com.weddingraffle.rifa.repository.RaffleCapacityRepository;
import com.weddingraffle.rifa.service.CapacityAllocationResult;
import com.weddingraffle.rifa.service.CapacityReservationService;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CapacityReservationServiceImpl implements CapacityReservationService {

    static final Duration RESERVATION_DURATION = Duration.ofMinutes(45);

    private final RaffleCapacityRepository raffleCapacityRepository;
    private final CapacityReservationRepository capacityReservationRepository;
    private final Clock clock;

    public CapacityReservationServiceImpl(
            RaffleCapacityRepository raffleCapacityRepository,
            CapacityReservationRepository capacityReservationRepository,
            Clock clock) {
        this.raffleCapacityRepository = raffleCapacityRepository;
        this.capacityReservationRepository = capacityReservationRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void reserve(String externalReference, int quantity) {
        OffsetDateTime now = now();
        RaffleCapacity capacity = lockCapacity();
        expireReservations(capacity, now);

        Optional<CapacityReservation> existing =
                capacityReservationRepository.findByExternalReference(externalReference);
        if (existing.isPresent()) {
            reserveExisting(capacity, existing.get(), quantity, now);
            return;
        }

        if (capacity.getAvailableQuantity() < quantity) {
            throw insufficientCapacity();
        }

        capacity.reserve(quantity);
        capacityReservationRepository.save(
                new CapacityReservation(externalReference, quantity, now.plus(RESERVATION_DURATION)));
    }

    @Override
    @Transactional
    public CapacityAllocationResult allocate(String externalReference, int quantity) {
        OffsetDateTime now = now();
        RaffleCapacity capacity = lockCapacity();
        expireReservations(capacity, now);

        Optional<CapacityReservation> existing =
                capacityReservationRepository.findByExternalReference(externalReference);
        if (existing.isEmpty()) {
            return allocateLateReservation(capacity, externalReference, quantity, now);
        }

        CapacityReservation reservation = existing.get();
        ensureQuantityMatches(reservation, quantity);
        if (reservation.getStatus() == CapacityReservationStatus.ALLOCATED) {
            return CapacityAllocationResult.ALREADY_ALLOCATED;
        }
        if (reservation.getStatus() == CapacityReservationStatus.CAPACITY_REJECTED) {
            return CapacityAllocationResult.INSUFFICIENT_CAPACITY;
        }
        if (reservation.getStatus() == CapacityReservationStatus.RELEASED) {
            throw new InvalidTransactionStateException("Capacity allocation was already released.");
        }
        if (reservation.getStatus() == CapacityReservationStatus.ACTIVE) {
            capacity.allocateReserved(quantity);
            reservation.markAllocated();
            return CapacityAllocationResult.ALLOCATED;
        }
        if (capacity.getAvailableQuantity() < quantity) {
            reservation.markCapacityRejected();
            return CapacityAllocationResult.INSUFFICIENT_CAPACITY;
        }

        capacity.allocateAvailable(quantity);
        reservation.markAllocated();
        return CapacityAllocationResult.ALLOCATED;
    }

    @Override
    @Transactional
    public void releaseAllocation(String externalReference) {
        RaffleCapacity capacity = lockCapacity();
        CapacityReservation reservation = capacityReservationRepository
                .findByExternalReference(externalReference)
                .orElseThrow(() -> new ResourceNotFoundException("Capacity reservation not found."));
        if (reservation.getStatus() != CapacityReservationStatus.ALLOCATED) {
            throw new InvalidTransactionStateException("Capacity reservation is not allocated.");
        }

        capacity.releaseAllocated(reservation.getQuantity());
        reservation.markReleased();
    }

    private void reserveExisting(
            RaffleCapacity capacity, CapacityReservation reservation, int quantity, OffsetDateTime now) {
        ensureQuantityMatches(reservation, quantity);
        if (reservation.getStatus() == CapacityReservationStatus.ACTIVE) {
            return;
        }
        if (reservation.getStatus() == CapacityReservationStatus.ALLOCATED) {
            throw new InvalidTransactionStateException("Capacity was already allocated.");
        }
        if (capacity.getAvailableQuantity() < quantity) {
            throw insufficientCapacity();
        }

        capacity.reserve(quantity);
        reservation.reactivate(now.plus(RESERVATION_DURATION));
    }

    private CapacityAllocationResult allocateLateReservation(
            RaffleCapacity capacity, String externalReference, int quantity, OffsetDateTime now) {
        if (capacity.getAvailableQuantity() < quantity) {
            capacityReservationRepository.save(CapacityReservation.rejected(externalReference, quantity, now));
            return CapacityAllocationResult.INSUFFICIENT_CAPACITY;
        }

        capacity.allocateAvailable(quantity);
        CapacityReservation reservation = new CapacityReservation(externalReference, quantity, now);
        reservation.markAllocated();
        capacityReservationRepository.save(reservation);
        return CapacityAllocationResult.ALLOCATED;
    }

    private void expireReservations(RaffleCapacity capacity, OffsetDateTime now) {
        var expiredReservations = capacityReservationRepository.findByStatusAndExpiresAtLessThanEqual(
                CapacityReservationStatus.ACTIVE, now);
        for (CapacityReservation reservation : expiredReservations) {
            capacity.expire(reservation.getQuantity());
            reservation.markExpired();
        }
    }

    private RaffleCapacity lockCapacity() {
        return raffleCapacityRepository
                .findLockedById(RaffleCapacity.SINGLETON_ID)
                .orElseThrow(() -> new ResourceNotFoundException("Raffle capacity not found."));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private static void ensureQuantityMatches(CapacityReservation reservation, int quantity) {
        if (reservation.getQuantity() != quantity) {
            throw new InvalidTransactionStateException("Capacity reservation quantity does not match transaction.");
        }
    }

    private static InvalidRaffleStateException insufficientCapacity() {
        return new InvalidRaffleStateException("Insufficient lucky number capacity for this purchase.");
    }
}
