package com.weddingraffle.rifa.service;

import com.weddingraffle.rifa.dto.RaffleComboResponse;
import java.util.List;

public interface RafflePricingService {

    PurchasePrice calculate(int quantity, Long comboId);

    List<RaffleComboResponse> getActiveCombos();
}
