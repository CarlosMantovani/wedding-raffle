package com.weddingraffle.rifa.integration;

public interface PaymentProviderClient {

    CheckoutPreferenceResponse createPreference(CheckoutPreferenceRequest request, String idempotencyKey);

    PaymentProviderPayment getPayment(String paymentId);
}
