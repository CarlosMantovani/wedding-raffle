package com.weddingraffle.rifa.service.impl;

import com.weddingraffle.rifa.dto.RaffleComboResponse;
import com.weddingraffle.rifa.dto.RaffleComboUpdateRequest;
import com.weddingraffle.rifa.entity.RaffleCombo;
import com.weddingraffle.rifa.exception.ResourceNotFoundException;
import com.weddingraffle.rifa.repository.RaffleComboRepository;
import com.weddingraffle.rifa.service.RaffleComboService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RaffleComboServiceImpl implements RaffleComboService {

    private static final int PERCENT_SCALE = 2;

    private final RaffleComboRepository raffleComboRepository;

    public RaffleComboServiceImpl(RaffleComboRepository raffleComboRepository) {
        this.raffleComboRepository = raffleComboRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public RaffleCombo getActiveCombo(Long comboId, int expectedQuantity) {
        RaffleCombo combo = findCombo(comboId);
        if (!combo.isActive()) {
            throw new IllegalArgumentException("Selected raffle combo is not active.");
        }
        if (combo.getQuantity() != expectedQuantity) {
            throw new IllegalArgumentException("Selected raffle combo does not match the requested quantity.");
        }
        return combo;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RaffleComboResponse> getActiveCombos(BigDecimal unitPrice) {
        return raffleComboRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().stream()
                .map(combo -> toResponse(combo, unitPrice))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RaffleComboResponse> getAllCombos(BigDecimal unitPrice) {
        return raffleComboRepository.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .map(combo -> toResponse(combo, unitPrice))
                .toList();
    }

    @Override
    @Transactional
    public RaffleComboResponse updateCombo(Long comboId, RaffleComboUpdateRequest request, BigDecimal unitPrice) {
        RaffleCombo combo = findCombo(comboId);
        validatePromotionalPrice(combo.getQuantity(), request.price(), unitPrice);
        combo.update(
                request.price(),
                request.active(),
                request.displayOrder(),
                request.highlightMostChosen(),
                request.highlightBestValue());
        return toResponse(raffleComboRepository.saveAndFlush(combo), unitPrice);
    }

    @Override
    @Transactional(readOnly = true)
    public void validateUnitPrice(BigDecimal unitPrice) {
        List<RaffleCombo> invalidCombos = raffleComboRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().stream()
                .filter(combo -> combo.getPrice().compareTo(regularPrice(combo.getQuantity(), unitPrice)) >= 0)
                .toList();
        if (!invalidCombos.isEmpty()) {
            String quantities = invalidCombos.stream()
                    .map(combo -> combo.getQuantity().toString())
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException("Adjust or deactivate the active raffle combos for quantities "
                    + quantities + " before changing the unit price.");
        }
    }

    private RaffleCombo findCombo(Long comboId) {
        return raffleComboRepository
                .findById(comboId)
                .orElseThrow(() -> new ResourceNotFoundException("Raffle combo not found."));
    }

    private static RaffleComboResponse toResponse(RaffleCombo combo, BigDecimal unitPrice) {
        BigDecimal regularPrice = regularPrice(combo.getQuantity(), unitPrice);
        BigDecimal savingsAmount = regularPrice.subtract(combo.getPrice());
        BigDecimal discountPercent = savingsAmount
                .multiply(BigDecimal.valueOf(100))
                .divide(regularPrice, PERCENT_SCALE, RoundingMode.HALF_UP);
        BigDecimal averagePricePerNumber =
                combo.getPrice().divide(BigDecimal.valueOf(combo.getQuantity()), 2, RoundingMode.HALF_UP);
        return new RaffleComboResponse(
                combo.getId(),
                combo.getQuantity(),
                combo.getPrice(),
                combo.isActive(),
                combo.getDisplayOrder(),
                combo.isHighlightMostChosen(),
                combo.isHighlightBestValue(),
                regularPrice,
                savingsAmount,
                discountPercent,
                averagePricePerNumber);
    }

    private static void validatePromotionalPrice(int quantity, BigDecimal comboPrice, BigDecimal unitPrice) {
        if (comboPrice.compareTo(regularPrice(quantity, unitPrice)) >= 0) {
            throw new IllegalArgumentException("Raffle combo price must be lower than its regular price.");
        }
    }

    private static BigDecimal regularPrice(int quantity, BigDecimal unitPrice) {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
