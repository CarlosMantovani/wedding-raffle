package com.weddingraffle.rifa.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.net.MPResponse;
import com.weddingraffle.rifa.exception.ExternalPaymentException;
import com.weddingraffle.rifa.exception.ExternalPaymentException.FailureType;
import java.net.ConnectException;
import java.net.SocketException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import javax.net.ssl.SSLHandshakeException;
import org.apache.http.conn.ConnectTimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MercadoPagoFailureClassifierTests {

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-24T12:00:00Z"), ZoneOffset.UTC);
    private final MercadoPagoFailureClassifier classifier = new MercadoPagoFailureClassifier(clock);

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 409, 422})
    void classifiesPermanentHttpErrorsWithoutRetry(int status) {
        ExternalPaymentException result = classify(apiException(status, Map.of()));

        assertThat(result.getFailureType()).isEqualTo(FailureType.PERMANENT);
        assertThat(result.getHttpStatus()).isEqualTo(status);
        assertThat(result.isRetryAllowed()).isFalse();
        assertThat(result.isCircuitBreakerFailure()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {408, 429, 500, 502, 503, 504})
    void classifiesTransientHttpErrorsAsRetryableForSafeOperations(int status) {
        ExternalPaymentException result = classify(apiException(status, Map.of()));

        assertThat(result.getFailureType()).isEqualTo(FailureType.TRANSIENT);
        assertThat(result.getHttpStatus()).isEqualTo(status);
        assertThat(result.isRetryAllowed()).isTrue();
        assertThat(result.isCircuitBreakerFailure()).isTrue();
    }

    @Test
    void parsesRetryAfterSeconds() {
        ExternalPaymentException result = classify(apiException(429, Map.of("Retry-After", List.of("3"))));

        assertThat(result.getRetryAfterMillis()).isEqualTo(3_000);
    }

    @Test
    void parsesRetryAfterHttpDate() {
        ExternalPaymentException result =
                classify(apiException(503, Map.of("retry-after", List.of("Mon, 24 Aug 2026 12:00:04 GMT"))));

        assertThat(result.getRetryAfterMillis()).isEqualTo(4_000);
    }

    @Test
    void ignoresInvalidRetryAfter() {
        ExternalPaymentException result = classify(apiException(429, Map.of("Retry-After", List.of("not-a-delay"))));

        assertThat(result.getRetryAfterMillis()).isNull();
    }

    @ParameterizedTest
    @ValueSource(classes = {ConnectTimeoutException.class, ConnectException.class, SocketException.class})
    void classifiesTransportFailuresAsTransient(Class<? extends Exception> causeType) throws Exception {
        Exception cause = causeType.getConstructor(String.class).newInstance("network failure");

        ExternalPaymentException result = classify(new MPException(cause));

        assertThat(result.getFailureType()).isEqualTo(FailureType.TRANSIENT);
        assertThat(result.isRetryAllowed()).isTrue();
    }

    @Test
    void treatsTlsHandshakeFailureAsPermanent() {
        ExternalPaymentException result = classify(new MPException(new SSLHandshakeException("invalid peer")));

        assertThat(result.getFailureType()).isEqualTo(FailureType.PERMANENT);
        assertThat(result.isRetryAllowed()).isFalse();
    }

    private ExternalPaymentException classify(Exception exception) {
        return classifier.classify(MercadoPagoOperation.CREATE_PREFERENCE, exception, "Test action");
    }

    private static MPApiException apiException(int status, Map<String, List<String>> headers) {
        return new MPApiException("API failure", new MPResponse(status, headers, "{}"));
    }
}
