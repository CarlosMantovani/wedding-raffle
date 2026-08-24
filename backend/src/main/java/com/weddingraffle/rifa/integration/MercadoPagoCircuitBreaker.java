package com.weddingraffle.rifa.integration;

import com.weddingraffle.rifa.config.AppProperties;
import com.weddingraffle.rifa.exception.ExternalPaymentException;
import com.weddingraffle.rifa.exception.ExternalPaymentException.FailureType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class MercadoPagoCircuitBreaker {

    enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    record Permission(boolean halfOpen) {}

    private static final Logger LOGGER = LoggerFactory.getLogger(MercadoPagoCircuitBreaker.class);

    private final AppProperties.CircuitBreaker properties;
    private final Clock clock;

    private State state = State.CLOSED;
    private int consecutiveFailures;
    private Instant openedAt;
    private boolean halfOpenProbeInProgress;

    MercadoPagoCircuitBreaker(AppProperties.CircuitBreaker properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    synchronized Permission acquirePermission(MercadoPagoOperation operation) {
        if (state == State.OPEN) {
            Duration elapsed = Duration.between(openedAt, clock.instant());
            if (elapsed.toMillis() >= properties.openDurationMillis()) {
                state = State.HALF_OPEN;
                halfOpenProbeInProgress = true;
                LOGGER.info("Mercado Pago circuit breaker entered HALF_OPEN operation={}", operation);
                return new Permission(true);
            }
            throw circuitOpenException();
        }
        if (state == State.HALF_OPEN) {
            if (halfOpenProbeInProgress) {
                throw circuitOpenException();
            }
            halfOpenProbeInProgress = true;
            return new Permission(true);
        }
        return new Permission(false);
    }

    synchronized void recordSuccess(Permission permission, MercadoPagoOperation operation) {
        if (permission.halfOpen()) {
            state = State.CLOSED;
            halfOpenProbeInProgress = false;
            consecutiveFailures = 0;
            openedAt = null;
            LOGGER.info("Mercado Pago circuit breaker recovered and CLOSED operation={}", operation);
            return;
        }
        consecutiveFailures = 0;
    }

    synchronized void recordFailure(Permission permission, MercadoPagoOperation operation) {
        if (permission.halfOpen()) {
            open(operation);
            return;
        }
        consecutiveFailures++;
        if (consecutiveFailures >= properties.failureThreshold()) {
            open(operation);
        }
    }

    synchronized void recordIgnored(
            Permission permission, MercadoPagoOperation operation, ExternalPaymentException exception) {
        if (!exception.hasProviderResponded()) {
            if (permission.halfOpen()) {
                open(operation);
            }
            return;
        }

        if (permission.halfOpen()) {
            recordSuccess(permission, operation);
        } else {
            consecutiveFailures = 0;
        }
    }

    synchronized State state() {
        return state;
    }

    private void open(MercadoPagoOperation operation) {
        state = State.OPEN;
        openedAt = clock.instant();
        halfOpenProbeInProgress = false;
        consecutiveFailures = 0;
        LOGGER.warn(
                "Mercado Pago circuit breaker OPEN operation={} openDurationMillis={}",
                operation,
                properties.openDurationMillis());
    }

    private static ExternalPaymentException circuitOpenException() {
        return new ExternalPaymentException(
                "Mercado Pago calls are temporarily unavailable because the circuit breaker is open.",
                null,
                FailureType.TRANSIENT,
                null,
                null,
                false,
                false,
                false);
    }
}
