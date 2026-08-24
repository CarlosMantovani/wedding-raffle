package com.weddingraffle.rifa.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TransactionCreateRequest(
        @NotBlank String name,
        @NotBlank String phone,
        @NotNull @Min(value = 1) Integer quantity,
        @Positive Long comboId) {

    public TransactionCreateRequest(String name, String phone, Integer quantity) {
        this(name, phone, quantity, null);
    }
}
