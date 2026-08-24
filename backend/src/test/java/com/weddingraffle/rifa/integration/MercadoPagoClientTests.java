package com.weddingraffle.rifa.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.merchantorder.MerchantOrderClient;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.resources.merchantorder.MerchantOrder;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.payment.PaymentOrder;
import com.mercadopago.resources.preference.Preference;
import com.weddingraffle.rifa.config.AppProperties;
import com.weddingraffle.rifa.exception.ExternalPaymentException;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.apache.http.protocol.HttpContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MercadoPagoClientTests {

    @Test
    void configuresSdkTimeoutsPoolAndDisablesHiddenTransportRetries() {
        AppProperties properties = appProperties();
        MercadoPagoResilienceExecutor executor = new MercadoPagoResilienceExecutor(properties, Clock.systemUTC());
        try {
            new MercadoPagoClient(properties, executor, new MercadoPagoFailureClassifier(Clock.systemUTC()));

            assertThat(MercadoPagoConfig.getConnectionTimeout()).isEqualTo(2_000);
            assertThat(MercadoPagoConfig.getConnectionRequestTimeout()).isEqualTo(500);
            assertThat(MercadoPagoConfig.getSocketTimeout()).isEqualTo(5_000);
            assertThat(MercadoPagoConfig.getMaxConnections()).isEqualTo(10);
            assertThat(MercadoPagoConfig.getRetryHandler()
                            .retryRequest(new IOException("test"), 1, mock(HttpContext.class)))
                    .isFalse();
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void sendsIdempotencyKeyWhenCreatingPreference() throws Exception {
        PreferenceClient preferenceClient = mock(PreferenceClient.class);
        Preference preference = mock(Preference.class);
        when(preference.getId()).thenReturn("preference-123");
        when(preference.getInitPoint()).thenReturn("https://checkout.example.com");
        when(preference.getCollectorId()).thenReturn(456L);
        when(preferenceClient.create(any(), any(MPRequestOptions.class))).thenReturn(preference);
        MercadoPagoClient client = new MercadoPagoClient(
                appProperties(), mock(PaymentClient.class), preferenceClient, mock(MerchantOrderClient.class));

        CheckoutPreferenceResponse response = client.createPreference(
                new CheckoutPreferenceRequest("Guest User", null, 2, new BigDecimal("10.00"), "external-reference-123"),
                "checkout-key-123");

        ArgumentCaptor<MPRequestOptions> optionsCaptor = ArgumentCaptor.forClass(MPRequestOptions.class);
        verify(preferenceClient).create(any(), optionsCaptor.capture());
        assertThat(optionsCaptor.getValue().getCustomHeaders()).containsEntry("X-Idempotency-Key", "checkout-key-123");
        assertThat(optionsCaptor.getValue().getConnectionTimeout()).isEqualTo(2_000);
        assertThat(optionsCaptor.getValue().getConnectionRequestTimeout()).isEqualTo(500);
        assertThat(optionsCaptor.getValue().getSocketTimeout()).isEqualTo(5_000);
        assertThat(response)
                .isEqualTo(new CheckoutPreferenceResponse("preference-123", "https://checkout.example.com", "456"));
    }

    @Test
    void rejectsMutablePreferenceCallWithoutAnIdempotencyKey() {
        PreferenceClient preferenceClient = mock(PreferenceClient.class);
        MercadoPagoClient client = new MercadoPagoClient(
                appProperties(), mock(PaymentClient.class), preferenceClient, mock(MerchantOrderClient.class));

        assertThatThrownBy(() -> client.createPreference(preferenceRequest(), " "))
                .isInstanceOf(ExternalPaymentException.class)
                .hasMessageContaining("requires an idempotency key");
        verifyNoInteractions(preferenceClient);
    }

    @Test
    void mapsAllFieldsRequiredForPaymentReconciliation() throws Exception {
        PaymentClient paymentClient = mock(PaymentClient.class);
        MerchantOrderClient merchantOrderClient = mock(MerchantOrderClient.class);
        Payment payment = mock(Payment.class);
        PaymentOrder paymentOrder = mock(PaymentOrder.class);
        MerchantOrder merchantOrder = mock(MerchantOrder.class);
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-08-22T11:00:00Z");
        OffsetDateTime updatedAt = OffsetDateTime.parse("2026-08-22T12:00:00Z");
        when(payment.getId()).thenReturn(123L);
        when(payment.getExternalReference()).thenReturn("external-reference-123");
        when(payment.getCollectorId()).thenReturn(456L);
        when(payment.getTransactionAmount()).thenReturn(new BigDecimal("20.00"));
        when(payment.getCurrencyId()).thenReturn("BRL");
        when(payment.getStatus()).thenReturn("approved");
        when(payment.getStatusDetail()).thenReturn("accredited");
        when(payment.getDateCreated()).thenReturn(createdAt);
        when(payment.getDateLastUpdated()).thenReturn(updatedAt);
        when(payment.getOrder()).thenReturn(paymentOrder);
        when(paymentOrder.getId()).thenReturn(789L);
        when(paymentClient.get(org.mockito.ArgumentMatchers.eq(123L), any(MPRequestOptions.class)))
                .thenReturn(payment);
        when(merchantOrder.getPreferenceId()).thenReturn("preference-123");
        when(merchantOrder.getExternalReference()).thenReturn("external-reference-123");
        when(merchantOrderClient.get(org.mockito.ArgumentMatchers.eq(789L), any(MPRequestOptions.class)))
                .thenReturn(merchantOrder);
        MercadoPagoClient client = new MercadoPagoClient(
                appProperties(), paymentClient, mock(PreferenceClient.class), merchantOrderClient);

        PaymentProviderPayment result = client.getPayment("123");

        assertThat(result)
                .isEqualTo(new PaymentProviderPayment(
                        "123",
                        "external-reference-123",
                        "external-reference-123",
                        "preference-123",
                        "456",
                        new BigDecimal("20.00"),
                        "BRL",
                        "approved",
                        "accredited",
                        createdAt,
                        updatedAt));
        verify(merchantOrderClient).get(org.mockito.ArgumentMatchers.eq(789L), any(MPRequestOptions.class));
    }

    private static AppProperties appProperties() {
        return new AppProperties(
                "http://localhost:5173",
                new AppProperties.Jwt("01234567890123456789012345678901", 3600, "raffle-api-test"),
                new AppProperties.Raffle(new BigDecimal("10.00"), "00000", "99999"),
                new AppProperties.MercadoPago(
                        "token",
                        "http://localhost:8080/payments/webhook",
                        "",
                        "http://localhost:5173/payment-return/success",
                        "http://localhost:5173/payment-return/failure",
                        "http://localhost:5173/payment-return/pending",
                        new AppProperties.Retry(1, 1, 1)));
    }

    private static CheckoutPreferenceRequest preferenceRequest() {
        return new CheckoutPreferenceRequest("Guest User", null, 2, new BigDecimal("10.00"), "external-reference-123");
    }
}
