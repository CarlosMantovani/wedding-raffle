package com.weddingraffle.rifa.integration;

import com.weddingraffle.rifa.config.AppProperties;
import com.weddingraffle.rifa.exception.ExternalPaymentException;
import com.weddingraffle.rifa.exception.ExternalPaymentException.FailureType;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.DoubleSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
class MercadoPagoResilienceExecutor {

    @FunctionalInterface
    interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(MercadoPagoResilienceExecutor.class);

    private final AppProperties.MercadoPago properties;
    private final ExecutorService callExecutor;
    private final Semaphore bulkhead;
    private final MercadoPagoCircuitBreaker circuitBreaker;
    private final Sleeper sleeper;
    private final DoubleSupplier random;

    @Autowired
    MercadoPagoResilienceExecutor(AppProperties appProperties, Clock clock) {
        this(
                appProperties.mercadoPago(),
                clock,
                Executors.newThreadPerTaskExecutor(
                        Thread.ofVirtual().name("mercado-pago-call-", 0).factory()),
                Thread::sleep,
                () -> ThreadLocalRandom.current().nextDouble());
    }

    MercadoPagoResilienceExecutor(
            AppProperties.MercadoPago properties,
            Clock clock,
            ExecutorService callExecutor,
            Sleeper sleeper,
            DoubleSupplier random) {
        this.properties = properties;
        this.callExecutor = callExecutor;
        this.bulkhead = new Semaphore(properties.bulkhead().maxConcurrentCalls(), true);
        this.circuitBreaker = new MercadoPagoCircuitBreaker(properties.circuitBreaker(), clock);
        this.sleeper = sleeper;
        this.random = random;
    }

    <T> T execute(MercadoPagoOperation operation, CheckedSupplier<T> supplier) {
        MercadoPagoCircuitBreaker.Permission permission = circuitBreaker.acquirePermission(operation);
        long deadlineNanos = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(properties.http().callTimeoutMillis());
        Future<T> future = callExecutor.submit(() -> executeWithinBulkhead(operation, supplier, deadlineNanos));
        try {
            T result = future.get(properties.http().callTimeoutMillis(), TimeUnit.MILLISECONDS);
            circuitBreaker.recordSuccess(permission, operation);
            return result;
        } catch (TimeoutException exception) {
            future.cancel(true);
            ExternalPaymentException failure = callTimeoutException(exception);
            circuitBreaker.recordFailure(permission, operation);
            logFinalFailure(operation, failure);
            throw failure;
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            ExternalPaymentException failure = interruptedException(exception);
            circuitBreaker.recordIgnored(permission, operation, failure);
            logFinalFailure(operation, failure);
            throw failure;
        } catch (ExecutionException exception) {
            ExternalPaymentException failure = asExternalPaymentException(exception.getCause());
            recordOutcome(permission, operation, failure);
            logFinalFailure(operation, failure);
            throw failure;
        } catch (CancellationException exception) {
            ExternalPaymentException failure = interruptedException(exception);
            circuitBreaker.recordIgnored(permission, operation, failure);
            logFinalFailure(operation, failure);
            throw failure;
        }
    }

    MercadoPagoCircuitBreaker.State circuitState() {
        return circuitBreaker.state();
    }

    private <T> T executeWithinBulkhead(
            MercadoPagoOperation operation, CheckedSupplier<T> supplier, long deadlineNanos) {
        boolean acquired = false;
        try {
            acquired = bulkhead.tryAcquire(properties.bulkhead().maxWaitMillis(), TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw bulkheadFullException();
            }
            return executeWithRetry(operation, supplier, deadlineNanos);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw interruptedException(exception);
        } finally {
            if (acquired) {
                bulkhead.release();
            }
        }
    }

    private <T> T executeWithRetry(MercadoPagoOperation operation, CheckedSupplier<T> supplier, long deadlineNanos) {
        AppProperties.Retry retry = properties.retry();
        for (int attempt = 1; attempt <= retry.maxAttempts(); attempt++) {
            try {
                return supplier.get();
            } catch (ExternalPaymentException exception) {
                if (!exception.isRetryAllowed() || attempt >= retry.maxAttempts()) {
                    throw exception;
                }
                long delayMillis = retryDelayMillis(exception, attempt);
                if (delayMillis < 0 || !fitsCallBudget(delayMillis, deadlineNanos)) {
                    throw exception;
                }
                LOGGER.warn(
                        "Retrying Mercado Pago call operation={} nextAttempt={} maxAttempts={} status={} delayMillis={} failureType={}",
                        operation,
                        attempt + 1,
                        retry.maxAttempts(),
                        exception.getHttpStatus(),
                        delayMillis,
                        exception.getFailureType());
                sleep(delayMillis);
            } catch (Exception exception) {
                throw new ExternalPaymentException(
                        "Unexpected Mercado Pago client failure.",
                        exception,
                        FailureType.PERMANENT,
                        null,
                        null,
                        false,
                        false,
                        false);
            }
        }
        throw new IllegalStateException("Mercado Pago retry loop ended unexpectedly.");
    }

    private long retryDelayMillis(ExternalPaymentException exception, int failedAttempt) {
        Long retryAfterMillis = exception.getRetryAfterMillis();
        if (retryAfterMillis != null) {
            if (retryAfterMillis > properties.retry().maxRetryAfterMillis()) {
                LOGGER.warn(
                        "Mercado Pago Retry-After exceeds synchronous retry limit status={} retryAfterMillis={} maxRetryAfterMillis={}",
                        exception.getHttpStatus(),
                        retryAfterMillis,
                        properties.retry().maxRetryAfterMillis());
                return -1;
            }
            return retryAfterMillis;
        }

        AppProperties.Retry retry = properties.retry();
        double exponentialDelay = retry.delayMillis() * Math.pow(retry.multiplier(), failedAttempt - 1);
        long cappedDelay = Math.min(retry.maxDelayMillis(), Math.round(exponentialDelay));
        double boundedRandom = Math.max(0, Math.min(1, random.getAsDouble()));
        double jitterMultiplier = 1 + ((boundedRandom * 2 - 1) * retry.jitterFactor());
        return Math.max(0, Math.round(cappedDelay * jitterMultiplier));
    }

    private static boolean fitsCallBudget(long delayMillis, long deadlineNanos) {
        long remainingNanos = deadlineNanos - System.nanoTime();
        return remainingNanos > TimeUnit.MILLISECONDS.toNanos(delayMillis);
    }

    private void sleep(long delayMillis) {
        try {
            sleeper.sleep(delayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw interruptedException(exception);
        }
    }

    private void recordOutcome(
            MercadoPagoCircuitBreaker.Permission permission,
            MercadoPagoOperation operation,
            ExternalPaymentException failure) {
        if (failure.isCircuitBreakerFailure()) {
            circuitBreaker.recordFailure(permission, operation);
        } else {
            circuitBreaker.recordIgnored(permission, operation, failure);
        }
    }

    private static ExternalPaymentException asExternalPaymentException(Throwable cause) {
        if (cause instanceof ExternalPaymentException exception) {
            return exception;
        }
        return new ExternalPaymentException(
                "Unexpected Mercado Pago execution failure.",
                cause,
                FailureType.PERMANENT,
                null,
                null,
                false,
                false,
                false);
    }

    private static ExternalPaymentException callTimeoutException(Throwable cause) {
        return new ExternalPaymentException(
                "Mercado Pago call exceeded its total timeout.",
                cause,
                FailureType.TRANSIENT,
                null,
                null,
                false,
                true,
                false);
    }

    private static ExternalPaymentException bulkheadFullException() {
        return new ExternalPaymentException(
                "Mercado Pago concurrency limit is currently exhausted.",
                null,
                FailureType.TRANSIENT,
                null,
                null,
                false,
                false,
                false);
    }

    private static ExternalPaymentException interruptedException(Throwable cause) {
        return new ExternalPaymentException(
                "Mercado Pago call was interrupted.", cause, FailureType.TRANSIENT, null, null, false, false, false);
    }

    private static void logFinalFailure(MercadoPagoOperation operation, ExternalPaymentException exception) {
        LOGGER.warn(
                "Mercado Pago call failed operation={} status={} failureType={} retryAllowed={} reason={}",
                operation,
                exception.getHttpStatus(),
                exception.getFailureType(),
                exception.isRetryAllowed(),
                exception.getMessage());
    }

    @PreDestroy
    void shutdown() {
        callExecutor.shutdownNow();
    }
}
