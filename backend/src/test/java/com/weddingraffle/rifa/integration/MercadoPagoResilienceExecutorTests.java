package com.weddingraffle.rifa.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.weddingraffle.rifa.config.AppProperties;
import com.weddingraffle.rifa.exception.ExternalPaymentException;
import com.weddingraffle.rifa.exception.ExternalPaymentException.FailureType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MercadoPagoResilienceExecutorTests {

    private final List<ExecutorService> executors = new ArrayList<>();

    @AfterEach
    void shutdownExecutors() {
        executors.forEach(ExecutorService::shutdownNow);
    }

    @Test
    void doesNotRetryPermanentFailures() {
        AtomicInteger calls = new AtomicInteger();
        List<Long> delays = new ArrayList<>();
        MercadoPagoResilienceExecutor executor = executor(defaultProperties(), delays, 0.5);

        assertThatThrownBy(() -> executor.execute(MercadoPagoOperation.CREATE_PREFERENCE, () -> {
                    calls.incrementAndGet();
                    throw permanentFailure(400);
                }))
                .isInstanceOf(ExternalPaymentException.class);

        assertThat(calls).hasValue(1);
        assertThat(delays).isEmpty();
    }

    @Test
    void retriesTransientFailuresUpToTheConfiguredLimitWithExponentialBackoff() {
        AtomicInteger calls = new AtomicInteger();
        List<Long> delays = new ArrayList<>();
        MercadoPagoResilienceExecutor executor = executor(defaultProperties(), delays, 0.5);

        assertThatThrownBy(() -> executor.execute(MercadoPagoOperation.CREATE_PREFERENCE, () -> {
                    calls.incrementAndGet();
                    throw transientFailure(null);
                }))
                .isInstanceOf(ExternalPaymentException.class);

        assertThat(calls).hasValue(3);
        assertThat(delays).containsExactly(100L, 200L);
    }

    @Test
    void appliesJitterToExponentialBackoff() {
        AtomicInteger calls = new AtomicInteger();
        List<Long> delays = new ArrayList<>();
        MercadoPagoResilienceExecutor executor = executor(defaultProperties(), delays, 1.0);

        String result = executor.execute(MercadoPagoOperation.GET_PAYMENT, () -> {
            if (calls.incrementAndGet() == 1) {
                throw transientFailure(null);
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(delays).containsExactly(150L);
    }

    @Test
    void honorsRetryAfterWhenWithinTheConfiguredLimit() {
        AtomicInteger calls = new AtomicInteger();
        List<Long> delays = new ArrayList<>();
        MercadoPagoResilienceExecutor executor = executor(defaultProperties(), delays, 0.5);

        String result = executor.execute(MercadoPagoOperation.CREATE_PREFERENCE, () -> {
            if (calls.incrementAndGet() == 1) {
                throw transientFailure(400L);
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(delays).containsExactly(400L);
    }

    @Test
    void doesNotRetryEarlierThanAnExcessiveRetryAfter() {
        AtomicInteger calls = new AtomicInteger();
        List<Long> delays = new ArrayList<>();
        MercadoPagoResilienceExecutor executor = executor(defaultProperties(), delays, 0.5);

        assertThatThrownBy(() -> executor.execute(MercadoPagoOperation.CREATE_PREFERENCE, () -> {
                    calls.incrementAndGet();
                    throw transientFailure(2_000L);
                }))
                .isInstanceOf(ExternalPaymentException.class);

        assertThat(calls).hasValue(1);
        assertThat(delays).isEmpty();
    }

    @Test
    void enforcesTheTotalCallTimeout() {
        AppProperties.MercadoPago properties = properties(
                new AppProperties.Http(100, 100, 100, 50, 2),
                new AppProperties.Retry(1, 10, 2, 20, 0, 50),
                new AppProperties.CircuitBreaker(2, 1_000),
                new AppProperties.Bulkhead(2, 25));
        MercadoPagoResilienceExecutor executor = executor(properties, new ArrayList<>(), 0.5);
        long startedAt = System.nanoTime();

        assertThatThrownBy(() -> executor.execute(MercadoPagoOperation.GET_PAYMENT, () -> {
                    Thread.sleep(10_000);
                    return "late";
                }))
                .isInstanceOf(ExternalPaymentException.class)
                .hasMessageContaining("total timeout");

        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(1));
    }

    @Test
    void opensCircuitAfterConfiguredTransientFailureThreshold() {
        AppProperties.MercadoPago properties = properties(
                new AppProperties.Http(100, 100, 1_000, 50, 2),
                new AppProperties.Retry(1, 10, 2, 20, 0, 50),
                new AppProperties.CircuitBreaker(2, 1_000),
                new AppProperties.Bulkhead(2, 25));
        MercadoPagoResilienceExecutor executor = executor(properties, new ArrayList<>(), 0.5);
        AtomicInteger calls = new AtomicInteger();

        for (int index = 0; index < 2; index++) {
            assertThatThrownBy(() -> executor.execute(MercadoPagoOperation.GET_PAYMENT, () -> {
                        calls.incrementAndGet();
                        throw transientFailure(null);
                    }))
                    .isInstanceOf(ExternalPaymentException.class);
        }

        assertThat(executor.circuitState()).isEqualTo(MercadoPagoCircuitBreaker.State.OPEN);
        assertThatThrownBy(() -> executor.execute(MercadoPagoOperation.GET_PAYMENT, () -> {
                    calls.incrementAndGet();
                    return "unexpected";
                }))
                .isInstanceOf(ExternalPaymentException.class)
                .hasMessageContaining("circuit breaker is open");
        assertThat(calls).hasValue(2);
    }

    @Test
    void providerResponseResetsConsecutiveAvailabilityFailures() {
        AppProperties.MercadoPago properties = properties(
                new AppProperties.Http(100, 100, 1_000, 50, 2),
                new AppProperties.Retry(1, 10, 2, 20, 0, 50),
                new AppProperties.CircuitBreaker(2, 1_000),
                new AppProperties.Bulkhead(2, 25));
        MercadoPagoResilienceExecutor executor = executor(properties, new ArrayList<>(), 0.5);

        assertThatThrownBy(() -> executor.execute(MercadoPagoOperation.GET_PAYMENT, () -> {
                    throw transientFailure(null);
                }))
                .isInstanceOf(ExternalPaymentException.class);
        assertThatThrownBy(() -> executor.execute(MercadoPagoOperation.GET_PAYMENT, () -> {
                    throw permanentFailure(400);
                }))
                .isInstanceOf(ExternalPaymentException.class);
        assertThatThrownBy(() -> executor.execute(MercadoPagoOperation.GET_PAYMENT, () -> {
                    throw transientFailure(null);
                }))
                .isInstanceOf(ExternalPaymentException.class);

        assertThat(executor.circuitState()).isEqualTo(MercadoPagoCircuitBreaker.State.CLOSED);
    }

    @Test
    void recoversThroughHalfOpenAfterTheOpenDuration() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-24T12:00:00Z"));
        AppProperties.MercadoPago properties = properties(
                new AppProperties.Http(100, 100, 1_000, 50, 2),
                new AppProperties.Retry(1, 10, 2, 20, 0, 50),
                new AppProperties.CircuitBreaker(1, 1_000),
                new AppProperties.Bulkhead(2, 25));
        MercadoPagoResilienceExecutor executor = executor(properties, clock, new ArrayList<>(), 0.5);

        assertThatThrownBy(() -> executor.execute(MercadoPagoOperation.GET_PAYMENT, () -> {
                    throw transientFailure(null);
                }))
                .isInstanceOf(ExternalPaymentException.class);
        assertThat(executor.circuitState()).isEqualTo(MercadoPagoCircuitBreaker.State.OPEN);

        clock.advance(Duration.ofSeconds(1));
        assertThat(executor.execute(MercadoPagoOperation.GET_PAYMENT, () -> "recovered"))
                .isEqualTo("recovered");
        assertThat(executor.circuitState()).isEqualTo(MercadoPagoCircuitBreaker.State.CLOSED);
    }

    @Test
    void bulkheadLimitsConcurrentProviderCalls() throws Exception {
        AppProperties.MercadoPago properties = properties(
                new AppProperties.Http(100, 100, 2_000, 50, 1),
                new AppProperties.Retry(1, 10, 2, 20, 0, 50),
                new AppProperties.CircuitBreaker(5, 1_000),
                new AppProperties.Bulkhead(1, 25));
        MercadoPagoResilienceExecutor executor = executor(properties, new ArrayList<>(), 0.5);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<String> first =
                CompletableFuture.supplyAsync(() -> executor.execute(MercadoPagoOperation.GET_PAYMENT, () -> {
                    started.countDown();
                    release.await();
                    return "first";
                }));
        assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> executor.execute(MercadoPagoOperation.GET_PAYMENT, () -> "second"))
                .isInstanceOf(ExternalPaymentException.class)
                .hasMessageContaining("concurrency limit");

        release.countDown();
        assertThat(first.get(1, TimeUnit.SECONDS)).isEqualTo("first");
    }

    private MercadoPagoResilienceExecutor executor(
            AppProperties.MercadoPago properties, List<Long> delays, double random) {
        return executor(properties, Clock.systemUTC(), delays, random);
    }

    private MercadoPagoResilienceExecutor executor(
            AppProperties.MercadoPago properties, Clock clock, List<Long> delays, double random) {
        ExecutorService callExecutor =
                Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
        executors.add(callExecutor);
        return new MercadoPagoResilienceExecutor(properties, clock, callExecutor, delays::add, () -> random);
    }

    private static AppProperties.MercadoPago defaultProperties() {
        return properties(
                new AppProperties.Http(100, 100, 5_000, 50, 2),
                new AppProperties.Retry(3, 100, 2, 500, 0.5, 1_000),
                new AppProperties.CircuitBreaker(5, 1_000),
                new AppProperties.Bulkhead(2, 25));
    }

    private static AppProperties.MercadoPago properties(
            AppProperties.Http http,
            AppProperties.Retry retry,
            AppProperties.CircuitBreaker circuitBreaker,
            AppProperties.Bulkhead bulkhead) {
        return new AppProperties.MercadoPago(
                "token",
                "https://example.com/webhook",
                "secret",
                "https://example.com/success",
                "https://example.com/failure",
                "https://example.com/pending",
                http,
                retry,
                circuitBreaker,
                bulkhead);
    }

    private static ExternalPaymentException transientFailure(Long retryAfterMillis) {
        return new ExternalPaymentException(
                "transient failure", null, FailureType.TRANSIENT, 503, retryAfterMillis, true, true, true);
    }

    private static ExternalPaymentException permanentFailure(int status) {
        return new ExternalPaymentException(
                "permanent failure", null, FailureType.PERMANENT, status, null, false, false, true);
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
