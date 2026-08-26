package com.weddingraffle.rifa.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TransactionQuoteRequest(
        @NotBlank String name,
        @NotBlank String phone,
        @Email String email,
        @NotNull @Min(value = 1) Integer quantity,
        @Positive Long comboId) {

    public TransactionQuoteRequest(String name, String phone, Integer quantity) {
        this(name, phone, null, quantity, null);
    }
}
