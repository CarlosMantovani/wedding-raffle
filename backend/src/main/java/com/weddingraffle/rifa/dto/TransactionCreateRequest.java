package com.weddingraffle.rifa.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TransactionCreateRequest(
        @NotBlank String name,
        @NotBlank String phone,
        @Email String email,
        String giftMessage,
        @NotNull @Min(value = 1) Integer quantity,
        @Positive Long comboId) {

    public TransactionCreateRequest(String name, String phone, Integer quantity) {
        this(name, phone, null, null, quantity, null);
    }

    public TransactionCreateRequest(String name, String phone, String giftMessage, Integer quantity) {
        this(name, phone, null, giftMessage, quantity, null);
    }

    @AssertTrue(message = "Gift message must not exceed 280 characters.")
    public boolean isGiftMessageWithinLimit() {
        return giftMessage == null || giftMessage.trim().length() <= 280;
    }
}
