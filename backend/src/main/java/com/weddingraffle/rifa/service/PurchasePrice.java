package com.weddingraffle.rifa.service;

import com.weddingraffle.rifa.entity.RaffleCombo;
import java.math.BigDecimal;

public record PurchasePrice(BigDecimal unitPrice, BigDecimal totalAmount, RaffleCombo combo) {

    public Long comboId() {
        return combo != null ? combo.getId() : null;
    }
}
