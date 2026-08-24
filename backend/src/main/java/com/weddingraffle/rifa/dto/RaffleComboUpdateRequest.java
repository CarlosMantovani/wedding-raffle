package com.weddingraffle.rifa.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record RaffleComboUpdateRequest(
        @NotNull @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 17, fraction = 2) BigDecimal price,
        @NotNull Boolean active,
        @NotNull @Min(0) Integer displayOrder,
        @NotNull Boolean highlightMostChosen,
        @NotNull Boolean highlightBestValue) {}
