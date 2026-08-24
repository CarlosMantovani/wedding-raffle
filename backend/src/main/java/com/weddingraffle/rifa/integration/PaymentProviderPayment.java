package com.weddingraffle.rifa.integration;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PaymentProviderPayment(
        String paymentId,
        String externalReference,
        String orderExternalReference,
        String preferenceId,
        String collectorId,
        BigDecimal transactionAmount,
        String currencyId,
        String status,
        String statusDetail,
        OffsetDateTime dateCreated,
        OffsetDateTime dateLastUpdated) {}
