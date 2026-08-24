package com.weddingraffle.rifa.repository;

import com.weddingraffle.rifa.entity.PaymentProviderName;
import com.weddingraffle.rifa.entity.ProviderPayment;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProviderPaymentRepository extends JpaRepository<ProviderPayment, Long> {

    @Modifying(flushAutomatically = true)
    @Query(
            value =
                    """
                    insert into provider_payment (provider, provider_payment_id, transaction_id)
                    values (:provider, :providerPaymentId, :transactionId)
                    on conflict (provider, provider_payment_id) do nothing
                    """,
            nativeQuery = true)
    int insertIfAbsent(
            @Param("provider") String provider,
            @Param("providerPaymentId") String providerPaymentId,
            @Param("transactionId") Long transactionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select providerPayment from ProviderPayment providerPayment
            where providerPayment.provider = :provider
              and providerPayment.providerPaymentId = :providerPaymentId
            """)
    Optional<ProviderPayment> findLocked(
            @Param("provider") PaymentProviderName provider, @Param("providerPaymentId") String providerPaymentId);

    List<ProviderPayment> findByTransactionIdOrderById(Long transactionId);
}
