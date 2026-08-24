package com.weddingraffle.rifa.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String frontendOrigin, Jwt jwt, Raffle raffle, MercadoPago mercadoPago) {

    public record Jwt(String secret, long expirationSeconds, String issuer) {}

    public record Raffle(BigDecimal unitPrice, String numberMin, String numberMax) {}

    public record MercadoPago(
            String accessToken,
            String webhookUrl,
            String webhookSecret,
            String successUrl,
            String failureUrl,
            String pendingUrl,
            Http http,
            Retry retry,
            CircuitBreaker circuitBreaker,
            Bulkhead bulkhead) {

        @ConstructorBinding
        public MercadoPago {}

        public MercadoPago(
                String accessToken,
                String webhookUrl,
                String webhookSecret,
                String successUrl,
                String failureUrl,
                String pendingUrl,
                Retry retry) {
            this(
                    accessToken,
                    webhookUrl,
                    webhookSecret,
                    successUrl,
                    failureUrl,
                    pendingUrl,
                    Http.safeDefaults(),
                    retry,
                    CircuitBreaker.safeDefaults(),
                    Bulkhead.safeDefaults());
        }
    }

    public record Http(
            int connectTimeoutMillis,
            int readTimeoutMillis,
            int callTimeoutMillis,
            int connectionRequestTimeoutMillis,
            int maxConnections) {

        public Http {
            requirePositive(connectTimeoutMillis, "connectTimeoutMillis");
            requirePositive(readTimeoutMillis, "readTimeoutMillis");
            requirePositive(callTimeoutMillis, "callTimeoutMillis");
            requirePositive(connectionRequestTimeoutMillis, "connectionRequestTimeoutMillis");
            requirePositive(maxConnections, "maxConnections");
        }

        private static Http safeDefaults() {
            return new Http(2_000, 5_000, 18_000, 500, 10);
        }
    }

    public record Retry(
            int maxAttempts,
            long delayMillis,
            double multiplier,
            long maxDelayMillis,
            double jitterFactor,
            long maxRetryAfterMillis) {

        @ConstructorBinding
        public Retry {
            requirePositive(maxAttempts, "maxAttempts");
            requirePositive(delayMillis, "delayMillis");
            if (multiplier < 1) {
                throw new IllegalArgumentException("multiplier must be at least 1");
            }
            requirePositive(maxDelayMillis, "maxDelayMillis");
            if (jitterFactor < 0 || jitterFactor > 1) {
                throw new IllegalArgumentException("jitterFactor must be between 0 and 1");
            }
            requirePositive(maxRetryAfterMillis, "maxRetryAfterMillis");
        }

        public Retry(int maxAttempts, long delayMillis, double multiplier) {
            this(maxAttempts, delayMillis, multiplier, 2_000, 0.5, 5_000);
        }
    }

    public record CircuitBreaker(int failureThreshold, long openDurationMillis) {

        public CircuitBreaker {
            requirePositive(failureThreshold, "failureThreshold");
            requirePositive(openDurationMillis, "openDurationMillis");
        }

        private static CircuitBreaker safeDefaults() {
            return new CircuitBreaker(5, 30_000);
        }
    }

    public record Bulkhead(int maxConcurrentCalls, long maxWaitMillis) {

        public Bulkhead {
            requirePositive(maxConcurrentCalls, "maxConcurrentCalls");
            requirePositive(maxWaitMillis, "maxWaitMillis");
        }

        private static Bulkhead safeDefaults() {
            return new Bulkhead(10, 250);
        }
    }

    private static void requirePositive(long value, String property) {
        if (value <= 0) {
            throw new IllegalArgumentException(property + " must be greater than zero");
        }
    }
}
