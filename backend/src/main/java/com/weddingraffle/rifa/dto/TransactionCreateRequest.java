package com.weddingraffle.rifa.dto;

import com.weddingraffle.rifa.util.CpfValidator;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record TransactionCreateRequest(
        @NotBlank String name,
        @NotBlank String phone,
        @Email String email,
        String giftMessage,
        @NotNull @Min(value = 1) Integer quantity,
        @Positive Long comboId,
        @NotBlank
                @Pattern(
                        regexp = "^(?:\\d{11}|\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2})$",
                        message = "CPF must contain 11 digits.")
                String cpf,
        @Pattern(
                        regexp = "^[\\x21-\\x7E]{1,256}$",
                        message =
                                "Device ID must contain only printable ASCII characters and not exceed 256 characters.")
                String deviceId) {

    public TransactionCreateRequest(
            String name,
            String phone,
            String email,
            String giftMessage,
            Integer quantity,
            Long comboId,
            String deviceId) {
        this(name, phone, email, giftMessage, quantity, comboId, null, deviceId);
    }

    public TransactionCreateRequest(
            String name, String phone, String email, String giftMessage, Integer quantity, Long comboId) {
        this(name, phone, email, giftMessage, quantity, comboId, null, null);
    }

    public TransactionCreateRequest(String name, String phone, Integer quantity) {
        this(name, phone, null, null, quantity, null, null, null);
    }

    public TransactionCreateRequest(String name, String phone, String giftMessage, Integer quantity) {
        this(name, phone, null, giftMessage, quantity, null, null, null);
    }

    @AssertTrue(message = "Gift message must not exceed 280 characters.")
    public boolean isGiftMessageWithinLimit() {
        return giftMessage == null || giftMessage.trim().length() <= 280;
    }

    @AssertTrue(message = "CPF must be valid.")
    public boolean isCpfValid() {
        return cpf == null || CpfValidator.isValid(cpf);
    }
}
