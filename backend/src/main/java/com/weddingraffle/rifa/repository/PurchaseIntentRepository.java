package com.weddingraffle.rifa.repository;

import com.weddingraffle.rifa.entity.PurchaseIntent;
import com.weddingraffle.rifa.entity.PurchaseIntentAction;
import com.weddingraffle.rifa.entity.PurchaseIntentStatus;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseIntentRepository extends JpaRepository<PurchaseIntent, Long> {

    Optional<PurchaseIntent> findByIdempotencyKey(String idempotencyKey);

    Optional<PurchaseIntent> findByExternalReference(String externalReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select intent from PurchaseIntent intent where intent.idempotencyKey = :idempotencyKey")
    Optional<PurchaseIntent> findLockedByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select intent from PurchaseIntent intent where intent.externalReference = :externalReference")
    Optional<PurchaseIntent> findLockedByExternalReference(@Param("externalReference") String externalReference);

    @Modifying
    @Query(
            """
            delete from PurchaseIntent intent
            where intent.action = :action
              and intent.status = :status
              and intent.updatedAt <= :cutoff
              and not exists (
                  select 1
                  from RaffleTransaction raffleTransaction
                  where raffleTransaction.externalReference = intent.externalReference
              )
            """)
    int deleteAbandonedPendingIntents(
            @Param("action") PurchaseIntentAction action,
            @Param("status") PurchaseIntentStatus status,
            @Param("cutoff") OffsetDateTime cutoff);
}
