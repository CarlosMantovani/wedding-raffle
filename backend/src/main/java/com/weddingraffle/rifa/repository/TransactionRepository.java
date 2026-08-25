package com.weddingraffle.rifa.repository;

import com.weddingraffle.rifa.entity.Transaction;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query(
            value =
                    """
                    select
                        cast(count(id) as bigint) as "totalTransactions",
                        cast(coalesce(sum(case
                            when status = 'APPROVED' and capacity_review_status is null then quantity
                            else 0
                        end), 0) as bigint)
                            as "approvedLuckyNumbers",
                        coalesce(sum(case
                            when status = 'APPROVED'
                                and capacity_review_status is distinct from 'REFUND_COMPLETED'
                            then total_amount
                            else 0
                        end), 0)
                            as "approvedRevenue"
                    from transaction
                    """,
            nativeQuery = true)
    AdminTransactionSummaryProjection getAdminSummary();

    Optional<Transaction> findByExternalReference(String externalReference);

    Page<Transaction> findByGiftMessageIsNotNullAndGiftMessageNot(String giftMessage, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select raffleTransaction from RaffleTransaction raffleTransaction where raffleTransaction.externalReference = :externalReference")
    Optional<Transaction> findLockedByExternalReference(@Param("externalReference") String externalReference);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value =
                    """
                    update transaction
                    set payment_reconciliation_attempted_at = :attemptedAt,
                        payment_reconciliation_lease_until = :leaseUntil,
                        payment_reconciliation_lease_token = :leaseToken
                    where id = :transactionId
                      and status = 'PENDING'
                      and mp_payment_id is not null
                      and (
                          payment_reconciliation_attempted_at is null
                          or payment_reconciliation_attempted_at <= :earliestAllowedAttempt
                      )
                      and (
                          payment_reconciliation_lease_until is null
                          or payment_reconciliation_lease_until <= :attemptedAt
                      )
                    """,
            nativeQuery = true)
    int tryAcquirePaymentReconciliation(
            @Param("transactionId") Long transactionId,
            @Param("leaseToken") UUID leaseToken,
            @Param("attemptedAt") OffsetDateTime attemptedAt,
            @Param("earliestAllowedAttempt") OffsetDateTime earliestAllowedAttempt,
            @Param("leaseUntil") OffsetDateTime leaseUntil);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value =
                    """
                    update transaction
                    set payment_reconciliation_lease_until = null,
                        payment_reconciliation_lease_token = null
                    where id = :transactionId
                      and payment_reconciliation_lease_token = :leaseToken
                    """,
            nativeQuery = true)
    int releasePaymentReconciliation(@Param("transactionId") Long transactionId, @Param("leaseToken") UUID leaseToken);

    List<Transaction> findByPhoneAndRecoveryCodeOrderByCreatedAtDesc(String phone, String recoveryCode);

    boolean existsByRecoveryCode(String recoveryCode);

    Optional<Transaction> findFirstByPhoneOrderByCreatedAtAsc(String phone);

    Optional<Transaction> findFirstByPhoneAndParticipantFlagCodeIsNotNullOrderByCreatedAtAsc(String phone);

    @Query(
            "select distinct raffleTransaction.participantFlagCode from RaffleTransaction raffleTransaction where raffleTransaction.participantFlagCode is not null")
    List<String> findDistinctParticipantFlagCodes();

    @Query(
            """
            select raffleTransaction from RaffleTransaction raffleTransaction
            where lower(raffleTransaction.name) like lower(concat('%', :query, '%'))
                or (:phoneQuery <> '' and raffleTransaction.phone like concat('%', :phoneQuery, '%'))
            """)
    Page<Transaction> findByNameOrPhone(
            @Param("query") String query, @Param("phoneQuery") String phoneQuery, Pageable pageable);

    @Query(
            value =
                    """
                    select cast(coalesce(sum(quantity), 0) as bigint)
                    from transaction
                    where status = 'APPROVED'
                      and capacity_review_status is null
                    """,
            nativeQuery = true)
    long sumApprovedQuantity();

    @Query(
            value =
                    """
                    select
                        participant_flag_code as code,
                        participant_flag_name as name,
                        participant_flag_emoji as emoji,
                        cast(sum(quantity) as bigint) as "totalNumbers"
                    from transaction
                    where status = 'APPROVED'
                      and capacity_review_status is null
                    group by participant_flag_code, participant_flag_name, participant_flag_emoji
                    order by sum(quantity) desc, max(created_at) desc, participant_flag_name asc
                    """,
            nativeQuery = true)
    List<FlagRankingProjection> findApprovedFlagRanking(Pageable pageable);
}
