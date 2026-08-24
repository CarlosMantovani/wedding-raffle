package com.weddingraffle.rifa.service;

import com.weddingraffle.rifa.dto.AdminGiftMessageResponse;
import com.weddingraffle.rifa.dto.AdminTransactionResponse;
import com.weddingraffle.rifa.dto.AdminTransactionSummaryResponse;
import com.weddingraffle.rifa.dto.CapacityReviewDecision;
import com.weddingraffle.rifa.dto.CashTransactionCreateRequest;
import com.weddingraffle.rifa.dto.CashTransactionCreateResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminTransactionService {

    AdminTransactionSummaryResponse getSummary();

    Page<AdminTransactionResponse> list(String query, Pageable pageable);

    Page<AdminGiftMessageResponse> listGiftMessages(Pageable pageable);

    CashTransactionCreateResponse createCashTransaction(String idempotencyKey, CashTransactionCreateRequest request);

    void deleteCashTransaction(String externalReference);

    void resolveCapacityReview(String externalReference, CapacityReviewDecision decision);
}
