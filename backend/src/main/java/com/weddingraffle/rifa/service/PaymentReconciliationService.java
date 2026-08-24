package com.weddingraffle.rifa.service;

import com.weddingraffle.rifa.entity.PaymentEventProcessingStatus;
import com.weddingraffle.rifa.integration.PaymentProviderPayment;

public interface PaymentReconciliationService {

    PaymentEventProcessingStatus reconcile(
            String requestedPaymentId, String expectedExternalReference, PaymentProviderPayment payment);
}
