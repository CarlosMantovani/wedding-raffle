package com.weddingraffle.rifa.dto;

import com.weddingraffle.rifa.entity.CapacityReviewStatus;
import com.weddingraffle.rifa.entity.PaymentMethod;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record AdminTransactionResponse(
        String externalReference,
        OffsetDateTime createdAt,
        String name,
        String phone,
        String email,
        PaymentMethod paymentMethod,
        CapacityReviewStatus capacityReviewStatus,
        Integer quantity,
        BigDecimal totalAmount,
        PaymentStatusResponse status,
        List<String> luckyNumbers) {}
