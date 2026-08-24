package com.weddingraffle.rifa.service;

import com.weddingraffle.rifa.dto.TransactionCreateResponse;
import com.weddingraffle.rifa.integration.CheckoutPreferenceRequest;

public record OnlinePurchaseAttempt(
        CheckoutPreferenceRequest checkoutPreferenceRequest, TransactionCreateResponse completedResponse) {

    public boolean isCompleted() {
        return completedResponse != null;
    }
}
