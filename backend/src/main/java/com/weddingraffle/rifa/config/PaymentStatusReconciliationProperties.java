package com.weddingraffle.rifa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payment-status-reconciliation")
public record PaymentStatusReconciliationProperties(long minimumIntervalMillis, long leaseDurationMillis) {

    public PaymentStatusReconciliationProperties {
        if (minimumIntervalMillis <= 0) {
            throw new IllegalArgumentException("minimumIntervalMillis must be greater than zero");
        }
        if (leaseDurationMillis <= 0) {
            throw new IllegalArgumentException("leaseDurationMillis must be greater than zero");
        }
    }
}
