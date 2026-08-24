package com.weddingraffle.rifa.service;

import com.weddingraffle.rifa.dto.CashTransactionCreateResponse;
import com.weddingraffle.rifa.dto.TransactionCreateResponse;
import com.weddingraffle.rifa.integration.CheckoutPreferenceResponse;
import java.math.BigDecimal;
import java.util.Optional;

public interface PurchaseIntentService {

    Optional<OnlinePurchaseAttempt> findOnline(String idempotencyKey, String requestHash);

    OnlinePurchaseAttempt prepareOnline(
            String idempotencyKey,
            String requestHash,
            String name,
            String phone,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal totalAmount);

    TransactionCreateResponse completeOnline(
            String idempotencyKey, String requestHash, CheckoutPreferenceResponse preference);

    Optional<CashTransactionCreateResponse> findCash(String idempotencyKey, String requestHash);

    CashTransactionCreateResponse createCash(
            String idempotencyKey,
            String requestHash,
            String name,
            String phone,
            String email,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal totalAmount);
}
