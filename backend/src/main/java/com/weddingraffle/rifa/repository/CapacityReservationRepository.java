package com.weddingraffle.rifa.repository;

import com.weddingraffle.rifa.entity.CapacityReservation;
import com.weddingraffle.rifa.entity.CapacityReservationStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CapacityReservationRepository extends JpaRepository<CapacityReservation, Long> {

    Optional<CapacityReservation> findByExternalReference(String externalReference);

    List<CapacityReservation> findByStatusAndExpiresAtLessThanEqual(
            CapacityReservationStatus status, OffsetDateTime expiresAt);
}
