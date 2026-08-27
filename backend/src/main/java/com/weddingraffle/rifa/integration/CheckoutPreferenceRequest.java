package com.weddingraffle.rifa.integration;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CheckoutPreferenceRequest(
        String name,
        String phone,
        String email,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        boolean promotionalCombo,
        String externalReference,
        String cpf,
        String deviceId,
        OffsetDateTime weddingEventAt) {

    public CheckoutPreferenceRequest(
            String name,
            String phone,
            String email,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal totalAmount,
            boolean promotionalCombo,
            String externalReference,
            String deviceId) {
        this(
                name,
                phone,
                email,
                quantity,
                unitPrice,
                totalAmount,
                promotionalCombo,
                externalReference,
                null,
                deviceId,
                null);
    }

    public CheckoutPreferenceRequest(
            String name,
            String phone,
            String email,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal totalAmount,
            boolean promotionalCombo,
            String externalReference) {
        this(
                name,
                phone,
                email,
                quantity,
                unitPrice,
                totalAmount,
                promotionalCombo,
                externalReference,
                null,
                null,
                null);
    }

    public CheckoutPreferenceRequest(
            String name, String phone, String email, Integer quantity, BigDecimal unitPrice, String externalReference) {
        this(
                name,
                phone,
                email,
                quantity,
                unitPrice,
                unitPrice.multiply(BigDecimal.valueOf(quantity)),
                false,
                externalReference,
                null,
                null,
                null);
    }

    public CheckoutPreferenceRequest withDeviceId(String deviceId) {
        return withPaymentContext(cpf, deviceId);
    }

    public CheckoutPreferenceRequest withPaymentContext(String cpf, String deviceId) {
        return withPaymentContext(cpf, deviceId, weddingEventAt);
    }

    public CheckoutPreferenceRequest withPaymentContext(String cpf, String deviceId, OffsetDateTime weddingEventAt) {
        return new CheckoutPreferenceRequest(
                name,
                phone,
                email,
                quantity,
                unitPrice,
                totalAmount,
                promotionalCombo,
                externalReference,
                cpf,
                deviceId,
                weddingEventAt);
    }
}
