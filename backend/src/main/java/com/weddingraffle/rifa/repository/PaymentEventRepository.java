package com.weddingraffle.rifa.repository;

import com.weddingraffle.rifa.entity.PaymentEvent;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {

    Optional<PaymentEvent> findByEventKey(String eventKey);

    List<PaymentEvent> findByProviderPayment_IdOrderByProviderUpdatedAtAscIdAsc(Long providerPaymentId);
}
