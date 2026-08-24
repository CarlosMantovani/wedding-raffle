package com.weddingraffle.rifa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.lucky-number-allocation")
public record LuckyNumberAllocationProperties(
        @DefaultValue("1000") int chunkSize, @DefaultValue("8") int maxConflictRetries) {

    public LuckyNumberAllocationProperties {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("Lucky-number allocation chunk size must be positive.");
        }
        if (maxConflictRetries < 0) {
            throw new IllegalArgumentException("Lucky-number allocation conflict retry limit cannot be negative.");
        }
    }
}
