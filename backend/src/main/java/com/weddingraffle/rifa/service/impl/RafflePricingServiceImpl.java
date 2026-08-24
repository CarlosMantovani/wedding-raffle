package com.weddingraffle.rifa.service.impl;

import com.weddingraffle.rifa.dto.RaffleComboResponse;
import com.weddingraffle.rifa.entity.RaffleCombo;
import com.weddingraffle.rifa.service.PurchasePrice;
import com.weddingraffle.rifa.service.RaffleComboService;
import com.weddingraffle.rifa.service.RaffleConfigService;
import com.weddingraffle.rifa.service.RafflePricingService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RafflePricingServiceImpl implements RafflePricingService {

    private final RaffleConfigService raffleConfigService;
    private final RaffleComboService raffleComboService;

    public RafflePricingServiceImpl(RaffleConfigService raffleConfigService, RaffleComboService raffleComboService) {
        this.raffleConfigService = raffleConfigService;
        this.raffleComboService = raffleComboService;
    }

    @Override
    public PurchasePrice calculate(int quantity, Long comboId) {
        BigDecimal unitPrice = raffleConfigService.getCurrentUnitPrice();
        if (comboId == null) {
            return new PurchasePrice(unitPrice, unitPrice.multiply(BigDecimal.valueOf(quantity)), null);
        }
        RaffleCombo combo = raffleComboService.getActiveCombo(comboId, quantity);
        BigDecimal regularPrice = unitPrice.multiply(BigDecimal.valueOf(quantity));
        if (combo.getPrice().compareTo(regularPrice) >= 0) {
            throw new IllegalArgumentException("Selected raffle combo is not cheaper than the regular price.");
        }
        return new PurchasePrice(unitPrice, combo.getPrice(), combo);
    }

    @Override
    public List<RaffleComboResponse> getActiveCombos() {
        BigDecimal unitPrice = raffleConfigService.getCurrentUnitPrice();
        return raffleComboService.getActiveCombos(unitPrice);
    }
}
