package com.weddingraffle.rifa.dto;

import java.math.BigDecimal;
import java.util.List;

public record TransactionStatusResponse(
        String externalReference,
        String recoveryCode,
        PaymentStatusResponse status,
        Integer quantity,
        BigDecimal totalAmount,
        String participantFlagName,
        String participantFlagEmoji,
        List<String> luckyNumbers,
        List<String> previousLuckyNumbers,
        Integer totalLuckyNumbers,
        String checkoutUrl) {}
