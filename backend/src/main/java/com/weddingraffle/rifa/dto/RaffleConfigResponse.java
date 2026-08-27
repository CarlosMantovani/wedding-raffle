package com.weddingraffle.rifa.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record RaffleConfigResponse(
        BigDecimal unitPrice,
        OffsetDateTime scheduledDrawAt,
        OffsetDateTime weddingEventAt,
        OffsetDateTime updatedAt,
        List<RaffleComboResponse> combos) {

    public RaffleConfigResponse(BigDecimal unitPrice, OffsetDateTime scheduledDrawAt, OffsetDateTime updatedAt) {
        this(unitPrice, scheduledDrawAt, null, updatedAt, List.of());
    }

    public RaffleConfigResponse(
            BigDecimal unitPrice,
            OffsetDateTime scheduledDrawAt,
            OffsetDateTime weddingEventAt,
            OffsetDateTime updatedAt) {
        this(unitPrice, scheduledDrawAt, weddingEventAt, updatedAt, List.of());
    }
}
