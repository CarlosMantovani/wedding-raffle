package com.weddingraffle.rifa.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CashTransactionCreateRequest(
        @NotBlank String name,
        @NotBlank String phone,
        @Email String email,
        String giftMessage,
        @NotNull @Min(value = 1) Integer quantity) {

    public CashTransactionCreateRequest(String name, String phone, String email, Integer quantity) {
        this(name, phone, email, null, quantity);
    }

    @AssertTrue(message = "Gift message must not exceed 280 characters.")
    public boolean isGiftMessageWithinLimit() {
        return giftMessage == null || giftMessage.trim().length() <= 280;
    }
}
