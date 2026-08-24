package com.weddingraffle.rifa.service;

import com.weddingraffle.rifa.dto.RaffleComboResponse;
import com.weddingraffle.rifa.dto.RaffleComboUpdateRequest;
import com.weddingraffle.rifa.entity.RaffleCombo;
import java.math.BigDecimal;
import java.util.List;

public interface RaffleComboService {

    RaffleCombo getActiveCombo(Long comboId, int expectedQuantity);

    List<RaffleComboResponse> getActiveCombos(BigDecimal unitPrice);

    List<RaffleComboResponse> getAllCombos(BigDecimal unitPrice);

    RaffleComboResponse updateCombo(Long comboId, RaffleComboUpdateRequest request, BigDecimal unitPrice);

    void validateUnitPrice(BigDecimal unitPrice);
}
