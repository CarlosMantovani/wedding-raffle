package com.weddingraffle.rifa.service;

import com.weddingraffle.rifa.dto.TransactionCreateRequest;
import com.weddingraffle.rifa.dto.TransactionCreateResponse;
import com.weddingraffle.rifa.dto.TransactionQuoteRequest;
import com.weddingraffle.rifa.dto.TransactionQuoteResponse;
import com.weddingraffle.rifa.dto.TransactionRecoveryRequest;
import com.weddingraffle.rifa.dto.TransactionStatusResponse;

public interface TransactionService {

    TransactionQuoteResponse quote(TransactionQuoteRequest request);

    TransactionCreateResponse create(String idempotencyKey, TransactionCreateRequest request);

    void processPaymentNotification(String paymentId);

    TransactionStatusResponse getStatus(String externalReference, String paymentId);

    TransactionStatusResponse recover(TransactionRecoveryRequest request);
}
