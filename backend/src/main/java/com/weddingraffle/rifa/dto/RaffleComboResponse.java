package com.weddingraffle.rifa.dto;

import java.math.BigDecimal;

public record RaffleComboResponse(
        Long id,
        Integer quantity,
        BigDecimal price,
        boolean active,
        Integer displayOrder,
        boolean highlightMostChosen,
        boolean highlightBestValue,
        BigDecimal regularPrice,
        BigDecimal savingsAmount,
        BigDecimal discountPercent,
        BigDecimal averagePricePerNumber) {}
