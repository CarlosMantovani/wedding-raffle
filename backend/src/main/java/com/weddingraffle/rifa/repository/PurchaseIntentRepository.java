package com.weddingraffle.rifa.repository;

import com.weddingraffle.rifa.entity.PurchaseIntent;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseIntentRepository extends JpaRepository<PurchaseIntent, Long> {

    Optional<PurchaseIntent> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select intent from PurchaseIntent intent where intent.idempotencyKey = :idempotencyKey")
    Optional<PurchaseIntent> findLockedByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);
}
