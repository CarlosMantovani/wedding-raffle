package com.weddingraffle.rifa.integration;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.common.IdentificationRequest;
import com.mercadopago.client.common.PhoneRequest;
import com.mercadopago.client.merchantorder.MerchantOrderClient;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceCategoryDescriptorRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferencePayerRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.merchantorder.MerchantOrder;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preference.Preference;
import com.weddingraffle.rifa.config.AppProperties;
import com.weddingraffle.rifa.exception.ExternalPaymentException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class MercadoPagoClient implements PaymentProviderClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(MercadoPagoClient.class);
    private static final String FALLBACK_EMAIL_DOMAIN = "@wedding-raffle.com";
    private static final String COMBO_ITEM_TITLE = "Combo %d números da sorte";
    private static final String ITEM_TITLE = "Número(s) da sorte";
    private static final String ITEM_ID = "wedding-lucky-number";
    private static final String ITEM_CATEGORY_ID = "entertainment";
    private static final String ITEM_DESCRIPTION = "%d número(s) da sorte para presente de casamento";
    private static final String CURRENCY_ID = "BRL";
    private static final String ADDITIONAL_INFO = "Compra de %d número(s) da sorte para presente de casamento.";

    private final AppProperties appProperties;
    private final PaymentClient paymentClient;
    private final MerchantOrderClient merchantOrderClient;
    private final PreferenceClient preferenceClient;
    private final MercadoPagoResilienceExecutor resilienceExecutor;
    private final MercadoPagoFailureClassifier failureClassifier;

    @Autowired
    public MercadoPagoClient(
            AppProperties appProperties,
            MercadoPagoResilienceExecutor resilienceExecutor,
            MercadoPagoFailureClassifier failureClassifier) {
        this.appProperties = appProperties;
        configureSdk(appProperties.mercadoPago());
        this.paymentClient = new PaymentClient();
        this.preferenceClient = new PreferenceClient();
        this.merchantOrderClient = new MerchantOrderClient();
        this.resilienceExecutor = resilienceExecutor;
        this.failureClassifier = failureClassifier;
    }

    MercadoPagoClient(
            AppProperties appProperties,
            PaymentClient paymentClient,
            PreferenceClient preferenceClient,
            MerchantOrderClient merchantOrderClient) {
        this(
                appProperties,
                paymentClient,
                preferenceClient,
                merchantOrderClient,
                testResilienceExecutor(appProperties),
                new MercadoPagoFailureClassifier(Clock.systemUTC()));
    }

    MercadoPagoClient(
            AppProperties appProperties,
            PaymentClient paymentClient,
            PreferenceClient preferenceClient,
            MerchantOrderClient merchantOrderClient,
            MercadoPagoResilienceExecutor resilienceExecutor,
            MercadoPagoFailureClassifier failureClassifier) {
        this.appProperties = appProperties;
        this.paymentClient = paymentClient;
        this.preferenceClient = preferenceClient;
        this.merchantOrderClient = merchantOrderClient;
        this.resilienceExecutor = resilienceExecutor;
        this.failureClassifier = failureClassifier;
    }

    @Override
    public CheckoutPreferenceResponse createPreference(CheckoutPreferenceRequest request, String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new ExternalPaymentException("Mercado Pago preference creation requires an idempotency key.", null);
        }
        return resilienceExecutor.execute(
                MercadoPagoOperation.CREATE_PREFERENCE, () -> createPreferenceOnce(request, idempotencyKey));
    }

    private CheckoutPreferenceResponse createPreferenceOnce(CheckoutPreferenceRequest request, String idempotencyKey) {
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("X-Idempotency-Key", idempotencyKey);
            if (StringUtils.hasText(request.deviceId())) {
                headers.put("X-meli-session-id", request.deviceId().trim());
            }
            MPRequestOptions requestOptions = requestOptions(Map.copyOf(headers));
            Preference preference = preferenceClient.create(toPreferenceRequest(request), requestOptions);
            return new CheckoutPreferenceResponse(
                    preference.getId(), preference.getInitPoint(), asString(preference.getCollectorId()));
        } catch (MPApiException | MPException exception) {
            throw failureClassifier.classify(
                    MercadoPagoOperation.CREATE_PREFERENCE, exception, "Unable to create Mercado Pago preference");
        }
    }

    @Override
    public PaymentProviderPayment getPayment(String paymentId) {
        LOGGER.debug("Mercado Pago payment status request paymentId={}", paymentId);
        PaymentProviderPayment result =
                resilienceExecutor.execute(MercadoPagoOperation.GET_PAYMENT, () -> getPaymentOnce(paymentId));
        LOGGER.debug(
                "Mercado Pago payment status response paymentId={} externalReference={} status={}",
                result.paymentId(),
                result.externalReference(),
                result.status());
        return result;
    }

    private PaymentProviderPayment getPaymentOnce(String paymentId) {
        try {
            MPRequestOptions requestOptions = requestOptions(Map.of());
            Payment payment = paymentClient.get(Long.valueOf(paymentId), requestOptions);
            MerchantOrder merchantOrder = getMerchantOrder(payment, requestOptions);
            return new PaymentProviderPayment(
                    asString(payment.getId()),
                    payment.getExternalReference(),
                    merchantOrder != null ? merchantOrder.getExternalReference() : null,
                    merchantOrder != null ? merchantOrder.getPreferenceId() : null,
                    asString(payment.getCollectorId()),
                    payment.getTransactionAmount(),
                    payment.getCurrencyId(),
                    payment.getStatus(),
                    payment.getStatusDetail(),
                    payment.getDateCreated(),
                    payment.getDateLastUpdated());
        } catch (MPApiException | MPException | NumberFormatException exception) {
            throw failureClassifier.classify(
                    MercadoPagoOperation.GET_PAYMENT, exception, "Unable to get Mercado Pago payment");
        }
    }

    private MerchantOrder getMerchantOrder(Payment payment, MPRequestOptions requestOptions)
            throws MPException, MPApiException {
        if (payment.getOrder() == null || payment.getOrder().getId() == null) {
            return null;
        }
        return merchantOrderClient.get(payment.getOrder().getId(), requestOptions);
    }

    private MPRequestOptions requestOptions(Map<String, String> customHeaders) {
        AppProperties.Http http = appProperties.mercadoPago().http();
        return MPRequestOptions.builder()
                .connectionTimeout(http.connectTimeoutMillis())
                .connectionRequestTimeout(http.connectionRequestTimeoutMillis())
                .socketTimeout(http.readTimeoutMillis())
                .customHeaders(customHeaders)
                .build();
    }

    private static void configureSdk(AppProperties.MercadoPago properties) {
        MercadoPagoConfig.setAccessToken(properties.accessToken());
        MercadoPagoConfig.setConnectionTimeout(properties.http().connectTimeoutMillis());
        MercadoPagoConfig.setConnectionRequestTimeout(properties.http().connectionRequestTimeoutMillis());
        MercadoPagoConfig.setSocketTimeout(properties.http().readTimeoutMillis());
        MercadoPagoConfig.setMaxConnections(properties.http().maxConnections());
        MercadoPagoConfig.setRetryHandler(new DefaultHttpRequestRetryHandler(0, false));
    }

    private static MercadoPagoResilienceExecutor testResilienceExecutor(AppProperties appProperties) {
        return new MercadoPagoResilienceExecutor(
                appProperties.mercadoPago(),
                Clock.systemUTC(),
                Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory()),
                Thread::sleep,
                () -> 0.5);
    }

    private static String asString(Long value) {
        return value != null ? String.valueOf(value) : null;
    }

    private PreferenceRequest toPreferenceRequest(CheckoutPreferenceRequest request) {
        String itemTitle = request.promotionalCombo() ? COMBO_ITEM_TITLE.formatted(request.quantity()) : ITEM_TITLE;
        int itemQuantity = request.promotionalCombo() ? 1 : request.quantity();
        BigDecimal itemUnitPrice = request.promotionalCombo() ? request.totalAmount() : request.unitPrice();
        PreferenceItemRequest.PreferenceItemRequestBuilder itemBuilder = PreferenceItemRequest.builder()
                .id(ITEM_ID)
                .title(itemTitle)
                .description(ITEM_DESCRIPTION.formatted(request.quantity()))
                .categoryId(ITEM_CATEGORY_ID)
                .currencyId(CURRENCY_ID)
                .quantity(itemQuantity)
                .unitPrice(itemUnitPrice);
        if (request.weddingEventAt() != null) {
            itemBuilder.categoryDescriptor(PreferenceCategoryDescriptorRequest.builder()
                    .eventDate(request.weddingEventAt().withOffsetSameInstant(ZoneOffset.UTC))
                    .build());
        }
        PreferenceItemRequest item = itemBuilder.build();

        PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                .success(appProperties.mercadoPago().successUrl())
                .failure(appProperties.mercadoPago().failureUrl())
                .pending(appProperties.mercadoPago().pendingUrl())
                .build();

        PayerName payerName = splitPayerName(request.name());
        PreferencePayerRequest.PreferencePayerRequestBuilder payerBuilder = PreferencePayerRequest.builder()
                .name(payerName.firstName())
                .surname(payerName.surname())
                .email(resolvePayerEmail(request.phone(), request.email()))
                .phone(toPhoneRequest(request.phone()));
        if (StringUtils.hasText(request.cpf())) {
            payerBuilder.identification(IdentificationRequest.builder()
                    .type("CPF")
                    .number(request.cpf())
                    .build());
        }
        PreferencePayerRequest payer = payerBuilder.build();

        PreferenceRequest.PreferenceRequestBuilder builder = PreferenceRequest.builder()
                .items(List.of(item))
                .backUrls(backUrls)
                .notificationUrl(appProperties.mercadoPago().webhookUrl())
                .additionalInfo(ADDITIONAL_INFO.formatted(request.quantity()))
                .externalReference(request.externalReference());

        builder.payer(payer);

        return builder.build();
    }

    private static String resolvePayerEmail(String phone, String email) {
        if (StringUtils.hasText(email)) {
            return email;
        }
        return phone.replaceAll("\\D", "") + FALLBACK_EMAIL_DOMAIN;
    }

    private static PhoneRequest toPhoneRequest(String phone) {
        String digits = phone.replaceAll("\\D", "");
        return PhoneRequest.builder()
                .areaCode(digits.substring(0, 2))
                .number(digits.substring(2))
                .build();
    }

    private static PayerName splitPayerName(String name) {
        String normalizedName = name.trim().replaceAll("\\s+", " ");
        int surnameStart = normalizedName.indexOf(' ');
        if (surnameStart < 0) {
            return new PayerName(normalizedName, null);
        }
        return new PayerName(normalizedName.substring(0, surnameStart), normalizedName.substring(surnameStart + 1));
    }

    private record PayerName(String firstName, String surname) {}
}
