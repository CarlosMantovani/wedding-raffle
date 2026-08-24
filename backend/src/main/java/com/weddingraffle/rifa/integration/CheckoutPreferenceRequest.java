package com.weddingraffle.rifa.integration;

import java.math.BigDecimal;

public record CheckoutPreferenceRequest(
        String name,
        String email,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        boolean promotionalCombo,
        String externalReference) {

    public CheckoutPreferenceRequest(
            String name, String email, Integer quantity, BigDecimal unitPrice, String externalReference) {
        this(
                name,
                email,
                quantity,
                unitPrice,
                unitPrice.multiply(BigDecimal.valueOf(quantity)),
                false,
                externalReference);
    }
}
