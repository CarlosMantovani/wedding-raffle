package com.weddingraffle.rifa.repository;

import com.weddingraffle.rifa.entity.Transaction;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select raffleTransaction from RaffleTransaction raffleTransaction where raffleTransaction.externalReference = :externalReference")
    Optional<Transaction> findLockedByExternalReference(@Param("externalReference") String externalReference);

    List<Transaction> findByPhoneAndRecoveryCodeOrderByCreatedAtDesc(String phone, String recoveryCode);

    boolean existsByRecoveryCode(String recoveryCode);

    Optional<Transaction> findFirstByPhoneOrderByCreatedAtAsc(String phone);

    @Query("select distinct raffleTransaction.participantFlagCode from RaffleTransaction raffleTransaction")
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
