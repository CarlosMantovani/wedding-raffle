package com.weddingraffle.rifa.integration;

import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPJsonParseException;
import com.mercadopago.net.MPResponse;
import com.weddingraffle.rifa.exception.ExternalPaymentException;
import com.weddingraffle.rifa.exception.ExternalPaymentException.FailureType;
import java.io.IOException;
import java.net.SocketException;
import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.net.ssl.SSLException;
import org.springframework.stereotype.Component;

@Component
class MercadoPagoFailureClassifier {

    private static final Set<Integer> TRANSIENT_HTTP_STATUSES = Set.of(408, 429, 500, 502, 503, 504);
    private static final String RETRY_AFTER = "Retry-After";

    private final Clock clock;

    MercadoPagoFailureClassifier(Clock clock) {
        this.clock = clock;
    }

    ExternalPaymentException classify(MercadoPagoOperation operation, Exception exception, String action) {
        if (exception instanceof MPApiException apiException) {
            return classifyApiFailure(operation, apiException, action);
        }

        boolean retrySafe = operation.isRetrySafe() && !Thread.currentThread().isInterrupted();
        boolean transientFailure = exception instanceof MPJsonParseException || isTransientTransportFailure(exception);
        if (transientFailure) {
            return new ExternalPaymentException(
                    action + " failed due to a transient Mercado Pago communication error.",
                    exception,
                    FailureType.TRANSIENT,
                    null,
                    null,
                    retrySafe,
                    true,
                    false);
        }

        return new ExternalPaymentException(
                action + " failed due to a permanent Mercado Pago client error.",
                exception,
                FailureType.PERMANENT,
                null,
                null,
                false,
                false,
                false);
    }

    private ExternalPaymentException classifyApiFailure(
            MercadoPagoOperation operation, MPApiException exception, String action) {
        int status = exception.getStatusCode();
        boolean transientFailure = TRANSIENT_HTTP_STATUSES.contains(status);
        Long retryAfterMillis = transientFailure ? parseRetryAfter(exception.getApiResponse()) : null;
        return new ExternalPaymentException(
                action + " failed with Mercado Pago HTTP status " + status + ".",
                exception,
                transientFailure ? FailureType.TRANSIENT : FailureType.PERMANENT,
                status,
                retryAfterMillis,
                transientFailure && operation.isRetrySafe(),
                transientFailure,
                true);
    }

    private Long parseRetryAfter(MPResponse response) {
        if (response == null || response.getHeaders() == null) {
            return null;
        }
        String value = firstHeaderValue(response.getHeaders(), RETRY_AFTER);
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim();
        try {
            long seconds = Long.parseLong(normalized);
            if (seconds < 0) {
                return null;
            }
            return Math.multiplyExact(seconds, 1_000L);
        } catch (ArithmeticException | NumberFormatException ignored) {
            // HTTP-date is the other valid Retry-After representation.
        }

        try {
            ZonedDateTime retryAt = ZonedDateTime.parse(normalized, DateTimeFormatter.RFC_1123_DATE_TIME);
            return Math.max(
                    0, Duration.between(clock.instant(), retryAt.toInstant()).toMillis());
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static String firstHeaderValue(Map<String, List<String>> headers, String expectedName) {
        return headers.entrySet().stream()
                .filter(entry -> expectedName.equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .filter(values -> values != null && !values.isEmpty())
                .map(List::getFirst)
                .findFirst()
                .orElse(null);
    }

    private static boolean isTransientTransportFailure(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof SSLException) {
                return false;
            }
        }
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof SocketException || current instanceof IOException) {
                return true;
            }
        }
        return false;
    }
}
