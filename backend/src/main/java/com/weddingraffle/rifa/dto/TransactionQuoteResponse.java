package com.weddingraffle.rifa.dto;

import java.math.BigDecimal;
import java.util.List;

public record TransactionQuoteResponse(
        String name,
        String phone,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        Long comboId,
        List<RaffleComboResponse> availableCombos) {

    public TransactionQuoteResponse(
            String name, String phone, Integer quantity, BigDecimal unitPrice, BigDecimal totalAmount) {
        this(name, phone, quantity, unitPrice, totalAmount, null, List.of());
    }
}
