package com.weddingraffle.rifa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.purchase-state-cleanup")
public record PurchaseStateCleanupProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("300000") long initialDelayMillis,
        @DefaultValue("300000") long fixedDelayMillis,
        @DefaultValue("3600000") long abandonedIntentAgeMillis) {

    public PurchaseStateCleanupProperties {
        if (initialDelayMillis < 0) {
            throw new IllegalArgumentException("Purchase-state cleanup initial delay cannot be negative");
        }
        if (fixedDelayMillis <= 0) {
            throw new IllegalArgumentException("Purchase-state cleanup fixed delay must be greater than zero");
        }
        if (abandonedIntentAgeMillis <= 0) {
            throw new IllegalArgumentException("Abandoned purchase-intent age must be greater than zero");
        }
    }
}
