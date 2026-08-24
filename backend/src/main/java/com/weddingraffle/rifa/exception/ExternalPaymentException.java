package com.weddingraffle.rifa.exception;

public class ExternalPaymentException extends RuntimeException {

    public enum FailureType {
        PERMANENT,
        TRANSIENT
    }

    private final FailureType failureType;
    private final Integer httpStatus;
    private final Long retryAfterMillis;
    private final boolean retryAllowed;
    private final boolean circuitBreakerFailure;
    private final boolean providerResponded;

    public ExternalPaymentException(String message, Throwable cause) {
        this(message, cause, FailureType.PERMANENT, null, null, false, false, false);
    }

    public ExternalPaymentException(
            String message,
            Throwable cause,
            FailureType failureType,
            Integer httpStatus,
            Long retryAfterMillis,
            boolean retryAllowed,
            boolean circuitBreakerFailure,
            boolean providerResponded) {
        super(message, cause);
        this.failureType = failureType;
        this.httpStatus = httpStatus;
        this.retryAfterMillis = retryAfterMillis;
        this.retryAllowed = retryAllowed;
        this.circuitBreakerFailure = circuitBreakerFailure;
        this.providerResponded = providerResponded;
    }

    public FailureType getFailureType() {
        return failureType;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public Long getRetryAfterMillis() {
        return retryAfterMillis;
    }

    public boolean isRetryAllowed() {
        return retryAllowed;
    }

    public boolean isCircuitBreakerFailure() {
        return circuitBreakerFailure;
    }

    public boolean hasProviderResponded() {
        return providerResponded;
    }
}
