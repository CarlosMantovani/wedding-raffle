package com.weddingraffle.rifa.repository;

import com.weddingraffle.rifa.entity.LuckyNumber;
import com.weddingraffle.rifa.entity.PaymentStatus;
import com.weddingraffle.rifa.entity.Transaction;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LuckyNumberRepository extends JpaRepository<LuckyNumber, Long> {

    boolean existsByTransaction(Transaction transaction);

    List<LuckyNumber> findByTransactionOrderByNumberAsc(Transaction transaction);

    List<LuckyNumber> findByTransactionInOrderByNumberAsc(List<Transaction> transactions);

    void deleteByTransaction(Transaction transaction);

    Optional<LuckyNumber> findByNumber(String number);

    @Query(
            """
            select luckyNumber
            from LuckyNumber luckyNumber
            join fetch luckyNumber.transaction transaction
            where transaction.status = :status
            order by luckyNumber.number
            """)
    List<LuckyNumber> findEligibleForDraw(@Param("status") PaymentStatus status);

    @Query(
            """
            select luckyNumber.number
            from LuckyNumber luckyNumber
            where luckyNumber.transaction.externalReference = :externalReference
            order by luckyNumber.number
            """)
    List<String> findNumbersByTransactionExternalReference(@Param("externalReference") String externalReference);

    @Query(
            """
            select luckyNumber.number
            from LuckyNumber luckyNumber
            where luckyNumber.transaction.phone = :phone
                and luckyNumber.transaction.status = :status
            order by luckyNumber.number
            """)
    List<String> findNumbersByPhoneAndStatus(@Param("phone") String phone, @Param("status") PaymentStatus status);

    @Query(
            """
            select luckyNumber.number
            from LuckyNumber luckyNumber
            where luckyNumber.transaction.phone = :phone
                and luckyNumber.transaction.status = :status
                and luckyNumber.transaction.externalReference <> :externalReference
            order by luckyNumber.number
            """)
    List<String> findNumbersByPhoneAndStatusExcludingExternalReference(
            @Param("phone") String phone,
            @Param("status") PaymentStatus status,
            @Param("externalReference") String externalReference);
}
