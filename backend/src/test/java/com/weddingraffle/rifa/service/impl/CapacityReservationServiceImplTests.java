package com.weddingraffle.rifa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weddingraffle.rifa.entity.CapacityReservation;
import com.weddingraffle.rifa.entity.CapacityReservationStatus;
import com.weddingraffle.rifa.entity.RaffleCapacity;
import com.weddingraffle.rifa.exception.InvalidRaffleStateException;
import com.weddingraffle.rifa.repository.CapacityReservationRepository;
import com.weddingraffle.rifa.repository.RaffleCapacityRepository;
import com.weddingraffle.rifa.service.CapacityAllocationResult;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CapacityReservationServiceImplTests {

    private static final Instant NOW = Instant.parse("2026-08-21T18:00:00Z");

    @Mock
    private RaffleCapacityRepository raffleCapacityRepository;

    @Mock
    private CapacityReservationRepository capacityReservationRepository;

    private CapacityReservationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CapacityReservationServiceImpl(
                raffleCapacityRepository, capacityReservationRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void reservesCapacityForFortyFiveMinutes() {
        RaffleCapacity capacity = new RaffleCapacity(10, 0, 0);
        when(raffleCapacityRepository.findLockedById(RaffleCapacity.SINGLETON_ID))
                .thenReturn(Optional.of(capacity));
        when(capacityReservationRepository.findByStatusAndExpiresAtLessThanEqual(
                        CapacityReservationStatus.ACTIVE, OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC)))
                .thenReturn(List.of());
        when(capacityReservationRepository.findByExternalReference("reference")).thenReturn(Optional.empty());

        service.reserve("reference", 4);

        assertThat(capacity.getReservedQuantity()).isEqualTo(4);
        ArgumentCaptor<CapacityReservation> captor = ArgumentCaptor.forClass(CapacityReservation.class);
        verify(capacityReservationRepository).save(captor.capture());
        assertThat(captor.getValue().getExpiresAt())
                .isEqualTo(OffsetDateTime.ofInstant(NOW.plusSeconds(45 * 60), ZoneOffset.UTC));
    }

    @Test
    void expiredReservationIsReleasedBeforeNewReservation() {
        OffsetDateTime now = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        RaffleCapacity capacity = new RaffleCapacity(10, 5, 0);
        CapacityReservation expired = new CapacityReservation("expired", 5, now.minusSeconds(1));
        when(raffleCapacityRepository.findLockedById(RaffleCapacity.SINGLETON_ID))
                .thenReturn(Optional.of(capacity));
        when(capacityReservationRepository.findByStatusAndExpiresAtLessThanEqual(CapacityReservationStatus.ACTIVE, now))
                .thenReturn(List.of(expired));
        when(capacityReservationRepository.findByExternalReference("new-reference"))
                .thenReturn(Optional.empty());

        service.reserve("new-reference", 10);

        assertThat(expired.getStatus()).isEqualTo(CapacityReservationStatus.EXPIRED);
        assertThat(capacity.getReservedQuantity()).isEqualTo(10);
    }

    @Test
    void expiresActiveReservationsOnDemand() {
        OffsetDateTime now = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        RaffleCapacity capacity = new RaffleCapacity(10, 5, 0);
        CapacityReservation expired = new CapacityReservation("expired", 5, now.minusSeconds(1));
        when(raffleCapacityRepository.findLockedById(RaffleCapacity.SINGLETON_ID))
                .thenReturn(Optional.of(capacity));
        when(capacityReservationRepository.findByStatusAndExpiresAtLessThanEqual(CapacityReservationStatus.ACTIVE, now))
                .thenReturn(List.of(expired));

        int expiredCount = service.expireActiveReservations();

        assertThat(expiredCount).isEqualTo(1);
        assertThat(expired.getStatus()).isEqualTo(CapacityReservationStatus.EXPIRED);
        assertThat(capacity.getReservedQuantity()).isZero();
    }

    @Test
    void rejectsCheckoutBeforeProviderCallWhenCapacityIsInsufficient() {
        OffsetDateTime now = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        RaffleCapacity capacity = new RaffleCapacity(10, 0, 10);
        when(raffleCapacityRepository.findLockedById(RaffleCapacity.SINGLETON_ID))
                .thenReturn(Optional.of(capacity));
        when(capacityReservationRepository.findByStatusAndExpiresAtLessThanEqual(CapacityReservationStatus.ACTIVE, now))
                .thenReturn(List.of());
        when(capacityReservationRepository.findByExternalReference("reference")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reserve("reference", 1))
                .isInstanceOf(InvalidRaffleStateException.class)
                .hasMessage("Insufficient lucky number capacity for this purchase.");
        verify(capacityReservationRepository, never()).save(any());
    }

    @Test
    void lateApprovalAllocatesWholeQuantityWhenCapacityIsAvailable() {
        OffsetDateTime now = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        RaffleCapacity capacity = new RaffleCapacity(10, 0, 5);
        CapacityReservation expired = new CapacityReservation("reference", 5, now.minusHours(1));
        expired.markExpired();
        when(raffleCapacityRepository.findLockedById(RaffleCapacity.SINGLETON_ID))
                .thenReturn(Optional.of(capacity));
        when(capacityReservationRepository.findByStatusAndExpiresAtLessThanEqual(CapacityReservationStatus.ACTIVE, now))
                .thenReturn(List.of());
        when(capacityReservationRepository.findByExternalReference("reference")).thenReturn(Optional.of(expired));

        CapacityAllocationResult result = service.allocate("reference", 5);

        assertThat(result).isEqualTo(CapacityAllocationResult.ALLOCATED);
        assertThat(expired.getStatus()).isEqualTo(CapacityReservationStatus.ALLOCATED);
        assertThat(capacity.getAllocatedQuantity()).isEqualTo(10);
    }

    @Test
    void lateApprovalRejectsWholeQuantityWhenCapacityIsUnavailable() {
        OffsetDateTime now = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        RaffleCapacity capacity = new RaffleCapacity(10, 0, 10);
        CapacityReservation expired = new CapacityReservation("reference", 2, now.minusHours(1));
        expired.markExpired();
        when(raffleCapacityRepository.findLockedById(RaffleCapacity.SINGLETON_ID))
                .thenReturn(Optional.of(capacity));
        when(capacityReservationRepository.findByStatusAndExpiresAtLessThanEqual(CapacityReservationStatus.ACTIVE, now))
                .thenReturn(List.of());
        when(capacityReservationRepository.findByExternalReference("reference")).thenReturn(Optional.of(expired));

        CapacityAllocationResult result = service.allocate("reference", 2);

        assertThat(result).isEqualTo(CapacityAllocationResult.INSUFFICIENT_CAPACITY);
        assertThat(expired.getStatus()).isEqualTo(CapacityReservationStatus.CAPACITY_REJECTED);
        assertThat(capacity.getAllocatedQuantity()).isEqualTo(10);
    }

    @Test
    void rejectedLateApprovalIsNeverAutomaticallyRetriedAfterCapacityBecomesAvailable() {
        OffsetDateTime now = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        RaffleCapacity capacity = new RaffleCapacity(10, 0, 0);
        CapacityReservation rejected = CapacityReservation.rejected("reference", 2, now.minusHours(1));
        when(raffleCapacityRepository.findLockedById(RaffleCapacity.SINGLETON_ID))
                .thenReturn(Optional.of(capacity));
        when(capacityReservationRepository.findByStatusAndExpiresAtLessThanEqual(CapacityReservationStatus.ACTIVE, now))
                .thenReturn(List.of());
        when(capacityReservationRepository.findByExternalReference("reference")).thenReturn(Optional.of(rejected));

        CapacityAllocationResult result = service.allocate("reference", 2);

        assertThat(result).isEqualTo(CapacityAllocationResult.INSUFFICIENT_CAPACITY);
        assertThat(capacity.getAllocatedQuantity()).isZero();
    }
}
