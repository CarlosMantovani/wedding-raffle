package com.weddingraffle.rifa.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonObject;
import com.mercadopago.client.merchantorder.MerchantOrderClient;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.net.MPHttpClient;
import com.mercadopago.net.MPRequest;
import com.mercadopago.net.MPResponse;
import com.weddingraffle.rifa.config.AppProperties;
import com.weddingraffle.rifa.exception.ExternalPaymentException;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MercadoPagoHttpResilienceTests {

    private static final String PREFERENCE_RESPONSE =
            """
            {"id":"preference-123","init_point":"https://checkout.example.com","collector_id":456}
            """;

    private MockWebServer server;
    private final List<ExecutorService> executors = new ArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stopServer() throws IOException {
        executors.forEach(ExecutorService::shutdownNow);
        server.getDispatcher().shutdown();
        server.shutdown();
    }

    @Test
    void returnsNormallyForHttp200() {
        server.enqueue(successResponse());

        CheckoutPreferenceResponse result = client(defaultProperties(), new ArrayList<>())
                .createPreference(preferenceRequest(), "checkout-key-123");

        assertThat(result)
                .isEqualTo(new CheckoutPreferenceResponse("preference-123", "https://checkout.example.com", "456"));
        assertThat(server.getRequestCount()).isOne();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 409, 422})
    void doesNotRetryPermanentHttpErrors(int status) {
        server.enqueue(new MockResponse().setResponseCode(status).setBody("{}"));

        assertThatThrownBy(() -> client(defaultProperties(), new ArrayList<>())
                        .createPreference(preferenceRequest(), "checkout-key-123"))
                .isInstanceOf(ExternalPaymentException.class)
                .satisfies(exception -> assertThat(((ExternalPaymentException) exception).getHttpStatus())
                        .isEqualTo(status));

        assertThat(server.getRequestCount()).isOne();
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 500, 502, 503, 504})
    void retriesTransientHttpErrors(int status) {
        server.enqueue(new MockResponse().setResponseCode(status).setBody("{}"));
        server.enqueue(successResponse());

        CheckoutPreferenceResponse result = client(defaultProperties(), new ArrayList<>())
                .createPreference(preferenceRequest(), "checkout-key-123");

        assertThat(result.preferenceId()).isEqualTo("preference-123");
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void honorsRetryAfterHeader() {
        List<Long> delays = new ArrayList<>();
        server.enqueue(new MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "1")
                .setBody("{}"));
        server.enqueue(successResponse());

        client(defaultProperties(), delays).createPreference(preferenceRequest(), "checkout-key-123");

        assertThat(delays).containsExactly(1_000L);
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void fallsBackToBackoffWhenRetryAfterIsAbsent() {
        List<Long> delays = new ArrayList<>();
        server.enqueue(new MockResponse().setResponseCode(429).setBody("{}"));
        server.enqueue(successResponse());

        client(defaultProperties(), delays).createPreference(preferenceRequest(), "checkout-key-123");

        assertThat(delays).containsExactly(10L);
    }

    @Test
    void fallsBackToBackoffWhenRetryAfterIsInvalid() {
        List<Long> delays = new ArrayList<>();
        server.enqueue(new MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "invalid")
                .setBody("{}"));
        server.enqueue(successResponse());

        client(defaultProperties(), delays).createPreference(preferenceRequest(), "checkout-key-123");

        assertThat(delays).containsExactly(10L);
    }

    @Test
    void doesNotWaitOrRetryWhenRetryAfterExceedsTheConfiguredLimit() {
        List<Long> delays = new ArrayList<>();
        server.enqueue(new MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "30")
                .setBody("{}"));

        assertThatThrownBy(() ->
                        client(defaultProperties(), delays).createPreference(preferenceRequest(), "checkout-key-123"))
                .isInstanceOf(ExternalPaymentException.class);

        assertThat(delays).isEmpty();
        assertThat(server.getRequestCount()).isOne();
    }

    @Test
    void keepsTheSameIdempotencyKeyAfterRetry() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(503).setBody("{}"));
        server.enqueue(successResponse());

        client(defaultProperties(), new ArrayList<>()).createPreference(preferenceRequest(), "checkout-key-123");

        RecordedRequest first = server.takeRequest(1, TimeUnit.SECONDS);
        RecordedRequest second = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(first.getHeader("X-Idempotency-Key")).isEqualTo("checkout-key-123");
        assertThat(second.getHeader("X-Idempotency-Key")).isEqualTo("checkout-key-123");
    }

    @Test
    void stopsAtTheMaximumAttemptCount() {
        server.enqueue(new MockResponse().setResponseCode(503).setBody("{}"));
        server.enqueue(new MockResponse().setResponseCode(503).setBody("{}"));
        server.enqueue(new MockResponse().setResponseCode(503).setBody("{}"));

        assertThatThrownBy(() -> client(defaultProperties(), new ArrayList<>())
                        .createPreference(preferenceRequest(), "checkout-key-123"))
                .isInstanceOf(ExternalPaymentException.class);

        assertThat(server.getRequestCount()).isEqualTo(3);
    }

    @Test
    void retriesAfterConnectionReset() {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
        server.enqueue(successResponse());

        CheckoutPreferenceResponse result = client(defaultProperties(), new ArrayList<>())
                .createPreference(preferenceRequest(), "checkout-key-123");

        assertThat(result.preferenceId()).isEqualTo("preference-123");
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void retriesAfterTruncatedResponse() {
        server.enqueue(new MockResponse()
                .setBody(PREFERENCE_RESPONSE)
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY));
        server.enqueue(successResponse());

        CheckoutPreferenceResponse result = client(defaultProperties(), new ArrayList<>())
                .createPreference(preferenceRequest(), "checkout-key-123");

        assertThat(result.preferenceId()).isEqualTo("preference-123");
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void retriesAfterReadTimeout() {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        server.enqueue(successResponse());

        CheckoutPreferenceResponse result = client(defaultProperties(), new ArrayList<>())
                .createPreference(preferenceRequest(), "checkout-key-123");

        assertThat(result.preferenceId()).isEqualTo("preference-123");
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(longs = {100, 1_000, 5_000})
    void acceptsSlowResponsesThatRemainInsideTheConfiguredBudget(long delayMillis) {
        server.enqueue(successResponse().setBodyDelay(delayMillis, TimeUnit.MILLISECONDS));
        AppProperties.MercadoPago properties =
                properties(Math.toIntExact(delayMillis + 1_000), Math.toIntExact(delayMillis + 2_000), 1);

        CheckoutPreferenceResponse result =
                client(properties, new ArrayList<>()).createPreference(preferenceRequest(), "checkout-key-123");

        assertThat(result.preferenceId()).isEqualTo("preference-123");
    }

    @Test
    void doesNotBlockForATenSecondResponseBeyondTheConfiguredTimeout() {
        server.enqueue(successResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        AppProperties.MercadoPago properties = properties(200, 400, 1);
        long startedAt = System.nanoTime();

        assertThatThrownBy(() ->
                        client(properties, new ArrayList<>()).createPreference(preferenceRequest(), "checkout-key-123"))
                .isInstanceOf(ExternalPaymentException.class);

        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(2));
    }

    private MercadoPagoClient client(AppProperties.MercadoPago mercadoPago, List<Long> delays) {
        AppProperties appProperties = new AppProperties(
                "http://localhost:5173",
                new AppProperties.Jwt("01234567890123456789012345678901", 3_600, "raffle-api-test"),
                new AppProperties.Raffle(new BigDecimal("10.00"), "00000", "99999"),
                mercadoPago);
        ExecutorService callExecutor =
                Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
        executors.add(callExecutor);
        MercadoPagoResilienceExecutor resilienceExecutor =
                new MercadoPagoResilienceExecutor(mercadoPago, Clock.systemUTC(), callExecutor, delays::add, () -> 0.5);
        MPHttpClient httpClient = new RewritingHttpClient(server);
        return new MercadoPagoClient(
                appProperties,
                new PaymentClient(httpClient),
                new PreferenceClient(httpClient),
                new MerchantOrderClient(httpClient),
                resilienceExecutor,
                new MercadoPagoFailureClassifier(Clock.systemUTC()));
    }

    private static AppProperties.MercadoPago defaultProperties() {
        return properties(200, 3_000, 3);
    }

    private static AppProperties.MercadoPago properties(int readTimeoutMillis, int callTimeoutMillis, int attempts) {
        return new AppProperties.MercadoPago(
                "token",
                "https://example.com/webhook",
                "secret",
                "https://example.com/success",
                "https://example.com/failure",
                "https://example.com/pending",
                new AppProperties.Http(200, readTimeoutMillis, callTimeoutMillis, 100, 10),
                new AppProperties.Retry(attempts, 10, 2, 50, 0, 2_000),
                new AppProperties.CircuitBreaker(10, 1_000),
                new AppProperties.Bulkhead(10, 50));
    }

    private static CheckoutPreferenceRequest preferenceRequest() {
        return new CheckoutPreferenceRequest("Guest User", null, 2, new BigDecimal("10.00"), "external-reference-123");
    }

    private static MockResponse successResponse() {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(PREFERENCE_RESPONSE);
    }

    private static final class RewritingHttpClient implements MPHttpClient {

        private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

        private final MockWebServer server;
        private final OkHttpClient baseClient =
                new OkHttpClient.Builder().retryOnConnectionFailure(false).build();

        private RewritingHttpClient(MockWebServer server) {
            this.server = server;
        }

        @Override
        public MPResponse send(MPRequest request) throws MPException, MPApiException {
            try {
                OkHttpClient client = baseClient
                        .newBuilder()
                        .connectTimeout(request.getConnectionTimeout(), TimeUnit.MILLISECONDS)
                        .readTimeout(request.getSocketTimeout(), TimeUnit.MILLISECONDS)
                        .build();
                URI originalUri = URI.create(request.getUri());
                Request.Builder requestBuilder = new Request.Builder().url(server.url(originalUri.getRawPath()));
                request.getHeaders().forEach(requestBuilder::header);
                JsonObject payload = request.getPayload();
                RequestBody body = payload != null ? RequestBody.create(payload.toString(), JSON) : null;
                requestBuilder.method(request.getMethod().name(), body);

                try (Response response = client.newCall(requestBuilder.build()).execute()) {
                    String content = response.body() != null ? response.body().string() : "";
                    MPResponse mpResponse =
                            new MPResponse(response.code(), response.headers().toMultimap(), content);
                    if (!response.isSuccessful()) {
                        throw new MPApiException("Mock Mercado Pago API failure", mpResponse);
                    }
                    return mpResponse;
                }
            } catch (IOException exception) {
                throw new MPException(exception);
            }
        }
    }
}
