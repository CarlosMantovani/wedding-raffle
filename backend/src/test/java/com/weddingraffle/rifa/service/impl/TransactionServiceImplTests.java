package com.weddingraffle.rifa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weddingraffle.rifa.dto.PaymentStatusResponse;
import com.weddingraffle.rifa.dto.TransactionCreateRequest;
import com.weddingraffle.rifa.dto.TransactionCreateResponse;
import com.weddingraffle.rifa.dto.TransactionQuoteRequest;
import com.weddingraffle.rifa.dto.TransactionQuoteResponse;
import com.weddingraffle.rifa.dto.TransactionRecoveryRequest;
import com.weddingraffle.rifa.entity.PaymentMethod;
import com.weddingraffle.rifa.entity.PaymentStatus;
import com.weddingraffle.rifa.entity.Transaction;
import com.weddingraffle.rifa.exception.ExternalPaymentException;
import com.weddingraffle.rifa.exception.InvalidRaffleStateException;
import com.weddingraffle.rifa.integration.CheckoutPreferenceRequest;
import com.weddingraffle.rifa.integration.CheckoutPreferenceResponse;
import com.weddingraffle.rifa.integration.PaymentProviderClient;
import com.weddingraffle.rifa.integration.PaymentProviderPayment;
import com.weddingraffle.rifa.repository.TransactionRepository;
import com.weddingraffle.rifa.service.LuckyNumberService;
import com.weddingraffle.rifa.service.OnlinePurchaseAttempt;
import com.weddingraffle.rifa.service.PaymentReconciliationService;
import com.weddingraffle.rifa.service.PurchaseIntentService;
import com.weddingraffle.rifa.service.PurchasePrice;
import com.weddingraffle.rifa.service.RaffleConfigService;
import com.weddingraffle.rifa.service.RafflePricingService;
import com.weddingraffle.rifa.util.PurchaseRequestHasher;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTests {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private PaymentProviderClient paymentProviderClient;

    @Mock
    private LuckyNumberService luckyNumberService;

    @Mock
    private RaffleConfigService raffleConfigService;

    @Mock
    private PaymentReconciliationService paymentReconciliationService;

    @Mock
    private PendingPaymentReconciliationCoordinator pendingPaymentReconciliationCoordinator;

    @Mock
    private PurchaseIntentService purchaseIntentService;

    @Mock
    private RafflePricingService rafflePricingService;

    private final PurchaseRequestHasher purchaseRequestHasher = new PurchaseRequestHasher();

    @Test
    void calculatesQuoteFromConfiguredUnitPrice() {
        TransactionServiceImpl transactionService = transactionService();
        when(rafflePricingService.calculate(3, null))
                .thenReturn(new PurchasePrice(new BigDecimal("10.00"), new BigDecimal("30.00"), null));
        when(rafflePricingService.getActiveCombos()).thenReturn(List.of());

        TransactionQuoteResponse response =
                transactionService.quote(new TransactionQuoteRequest("Guest User", "(11) 99999-9999", 3));

        assertThat(response.name()).isEqualTo("Guest User");
        assertThat(response.phone()).isEqualTo("11999999999");
        assertThat(response.quantity()).isEqualTo(3);
        assertThat(response.unitPrice()).isEqualByComparingTo("10.00");
        assertThat(response.totalAmount()).isEqualByComparingTo("30.00");
    }

    @Test
    void quoteRejectsPurchaseAfterDrawIsClosed() {
        TransactionServiceImpl transactionService = transactionService();
        when(raffleConfigService.isDrawClosed()).thenReturn(true);

        assertThatThrownBy(
                        () -> transactionService.quote(new TransactionQuoteRequest("Guest User", "(11) 99999-9999", 3)))
                .isInstanceOf(InvalidRaffleStateException.class)
                .hasMessage("Draw is closed. No more numbers can be purchased.");
    }

    @Test
    void createsPendingTransactionWithCheckoutPreference() {
        TransactionServiceImpl transactionService = transactionService();
        String idempotencyKey = "checkout-key-123";
        String requestHash = purchaseRequestHasher.online("Guest User", "11999999999", 2);
        CheckoutPreferenceRequest preferenceRequest = new CheckoutPreferenceRequest(
                "Guest User", "11999999999", null, 2, new BigDecimal("10.00"), "external-reference-123");
        TransactionCreateResponse expectedResponse = new TransactionCreateResponse(
                "external-reference-123", "4821", "preference-123", "https://checkout.example.com");
        when(purchaseIntentService.findOnline(idempotencyKey, requestHash)).thenReturn(Optional.empty());
        when(rafflePricingService.calculate(2, null)).thenReturn(regularPurchasePrice());
        when(purchaseIntentService.prepareOnline(
                        idempotencyKey,
                        requestHash,
                        "Guest User",
                        "11999999999",
                        null,
                        null,
                        2,
                        regularPurchasePrice()))
                .thenReturn(new OnlinePurchaseAttempt(preferenceRequest, null));
        when(paymentProviderClient.createPreference(preferenceRequest, idempotencyKey))
                .thenReturn(new CheckoutPreferenceResponse(
                        "preference-123", "https://checkout.example.com", "collector-123"));
        when(purchaseIntentService.completeOnline(
                        idempotencyKey,
                        requestHash,
                        new CheckoutPreferenceResponse(
                                "preference-123", "https://checkout.example.com", "collector-123")))
                .thenReturn(expectedResponse);

        TransactionCreateResponse response = transactionService.create(
                idempotencyKey, new TransactionCreateRequest("Guest User", "(11) 99999-9999", 2));

        assertThat(response.externalReference()).isNotBlank();
        assertThat(response.recoveryCode()).isEqualTo("4821");
        assertThat(response.preferenceId()).isEqualTo("preference-123");
        assertThat(response.checkoutUrl()).isEqualTo("https://checkout.example.com");

        ArgumentCaptor<CheckoutPreferenceRequest> preferenceCaptor =
                ArgumentCaptor.forClass(CheckoutPreferenceRequest.class);
        verify(paymentProviderClient).createPreference(preferenceCaptor.capture(), eq(idempotencyKey));
        assertThat(preferenceCaptor.getValue().name()).isEqualTo("Guest User");
        assertThat(preferenceCaptor.getValue().phone()).isEqualTo("11999999999");
        assertThat(preferenceCaptor.getValue().email()).isNull();
        assertThat(preferenceCaptor.getValue().quantity()).isEqualTo(2);
        assertThat(preferenceCaptor.getValue().unitPrice()).isEqualByComparingTo("10.00");
        assertThat(preferenceCaptor.getValue().externalReference()).isEqualTo(response.externalReference());

        verify(purchaseIntentService)
                .completeOnline(
                        idempotencyKey,
                        requestHash,
                        new CheckoutPreferenceResponse(
                                "preference-123", "https://checkout.example.com", "collector-123"));
    }

    @Test
    void createsCheckoutPreferenceWithOptionalEmail() {
        TransactionServiceImpl transactionService = transactionService();
        String idempotencyKey = "checkout-key-123";
        String requestHash = purchaseRequestHasher.online("Guest User", "11999999999", "guest@example.com", null, 2);
        CheckoutPreferenceRequest preferenceRequest = new CheckoutPreferenceRequest(
                "Guest User", "11999999999", "guest@example.com", 2, new BigDecimal("10.00"), "external-reference-123");
        TransactionCreateResponse expectedResponse = new TransactionCreateResponse(
                "external-reference-123", "4821", "preference-123", "https://checkout.example.com");
        when(purchaseIntentService.findOnline(idempotencyKey, requestHash)).thenReturn(Optional.empty());
        when(rafflePricingService.calculate(2, null)).thenReturn(regularPurchasePrice());
        when(purchaseIntentService.prepareOnline(
                        idempotencyKey,
                        requestHash,
                        "Guest User",
                        "11999999999",
                        "guest@example.com",
                        null,
                        2,
                        regularPurchasePrice()))
                .thenReturn(new OnlinePurchaseAttempt(preferenceRequest, null));
        when(paymentProviderClient.createPreference(preferenceRequest, idempotencyKey))
                .thenReturn(new CheckoutPreferenceResponse(
                        "preference-123", "https://checkout.example.com", "collector-123"));
        when(purchaseIntentService.completeOnline(
                        idempotencyKey,
                        requestHash,
                        new CheckoutPreferenceResponse(
                                "preference-123", "https://checkout.example.com", "collector-123")))
                .thenReturn(expectedResponse);

        transactionService.create(
                idempotencyKey,
                new TransactionCreateRequest("Guest User", "(11) 99999-9999", "guest@example.com", null, 2, null));

        ArgumentCaptor<CheckoutPreferenceRequest> preferenceCaptor =
                ArgumentCaptor.forClass(CheckoutPreferenceRequest.class);
        verify(paymentProviderClient).createPreference(preferenceCaptor.capture(), eq(idempotencyKey));
        assertThat(preferenceCaptor.getValue().email()).isEqualTo("guest@example.com");
    }

    @Test
    void trimsGiftMessageWhenCreatingCheckout() {
        TransactionServiceImpl transactionService = transactionService();
        String idempotencyKey = "checkout-key-123";
        String requestHash = purchaseRequestHasher.online("Guest User", "11999999999", "Felicidades!", 2);
        CheckoutPreferenceRequest preferenceRequest = new CheckoutPreferenceRequest(
                "Guest User", "11999999999", null, 2, new BigDecimal("10.00"), "external-reference-123");
        TransactionCreateResponse expectedResponse = new TransactionCreateResponse(
                "external-reference-123", "4821", "preference-123", "https://checkout.example.com");
        when(purchaseIntentService.findOnline(idempotencyKey, requestHash)).thenReturn(Optional.empty());
        when(rafflePricingService.calculate(2, null)).thenReturn(regularPurchasePrice());
        when(purchaseIntentService.prepareOnline(
                        idempotencyKey,
                        requestHash,
                        "Guest User",
                        "11999999999",
                        null,
                        "Felicidades!",
                        2,
                        regularPurchasePrice()))
                .thenReturn(new OnlinePurchaseAttempt(preferenceRequest, null));
        when(paymentProviderClient.createPreference(preferenceRequest, idempotencyKey))
                .thenReturn(new CheckoutPreferenceResponse(
                        "preference-123", "https://checkout.example.com", "collector-123"));
        when(purchaseIntentService.completeOnline(
                        idempotencyKey,
                        requestHash,
                        new CheckoutPreferenceResponse(
                                "preference-123", "https://checkout.example.com", "collector-123")))
                .thenReturn(expectedResponse);

        TransactionCreateResponse response = transactionService.create(
                idempotencyKey, new TransactionCreateRequest("Guest User", "(11) 99999-9999", "  Felicidades!  ", 2));

        assertThat(response).isEqualTo(expectedResponse);
    }

    @Test
    void createRejectsPurchaseAfterDrawIsClosed() {
        TransactionServiceImpl transactionService = transactionService();
        String requestHash = purchaseRequestHasher.online("Guest User", "11999999999", 2);
        when(purchaseIntentService.findOnline("checkout-key-123", requestHash)).thenReturn(Optional.empty());
        when(raffleConfigService.isDrawClosed()).thenReturn(true);

        assertThatThrownBy(() -> transactionService.create(
                        "checkout-key-123", new TransactionCreateRequest("Guest User", "(11) 99999-9999", 2)))
                .isInstanceOf(InvalidRaffleStateException.class)
                .hasMessage("Draw is closed. No more numbers can be purchased.");
        verify(paymentProviderClient, never()).createPreference(any(), any());
        verify(purchaseIntentService, never())
                .prepareOnline(any(), any(), any(), any(), any(), any(), any(Integer.class), any());
    }

    @Test
    void retryReturnsPersistedCheckoutWithoutCallingProviderAgain() {
        TransactionServiceImpl transactionService = transactionService();
        String idempotencyKey = "checkout-key-123";
        String requestHash = purchaseRequestHasher.online("Guest User", "11999999999", 2);
        TransactionCreateResponse persistedResponse = new TransactionCreateResponse(
                "external-reference-123", "4821", "preference-123", "https://checkout.example.com");
        CheckoutPreferenceRequest preferenceRequest = new CheckoutPreferenceRequest(
                "Guest User", "11999999999", null, 2, new BigDecimal("10.00"), "external-reference-123");
        when(purchaseIntentService.findOnline(idempotencyKey, requestHash))
                .thenReturn(Optional.of(new OnlinePurchaseAttempt(preferenceRequest, persistedResponse)));

        TransactionCreateResponse response = transactionService.create(
                idempotencyKey, new TransactionCreateRequest("Guest User", "(11) 99999-9999", 2));

        assertThat(response).isEqualTo(persistedResponse);
        verify(paymentProviderClient, never()).createPreference(any(), any());
        verify(raffleConfigService, never()).isDrawClosed();
    }

    @Test
    void databaseFailureBeforeExternalCallDoesNotCreateCheckout() {
        TransactionServiceImpl transactionService = transactionService();
        String idempotencyKey = "checkout-key-123";
        String requestHash = purchaseRequestHasher.online("Guest User", "11999999999", 2);
        when(purchaseIntentService.findOnline(idempotencyKey, requestHash))
                .thenReturn(Optional.empty(), Optional.empty());
        when(rafflePricingService.calculate(2, null)).thenReturn(regularPurchasePrice());
        when(purchaseIntentService.prepareOnline(
                        idempotencyKey,
                        requestHash,
                        "Guest User",
                        "11999999999",
                        null,
                        null,
                        2,
                        regularPurchasePrice()))
                .thenThrow(new DataIntegrityViolationException("database unavailable"));

        assertThatThrownBy(() -> transactionService.create(
                        idempotencyKey, new TransactionCreateRequest("Guest User", "(11) 99999-9999", 2)))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(paymentProviderClient, never()).createPreference(any(), any());
    }

    @Test
    void retryAfterLostProviderResponseResumesThePersistedIntent() {
        TransactionServiceImpl transactionService = transactionService();
        String idempotencyKey = "checkout-key-123";
        String requestHash = purchaseRequestHasher.online("Guest User", "11999999999", 2);
        CheckoutPreferenceRequest preferenceRequest = new CheckoutPreferenceRequest(
                "Guest User", "11999999999", null, 2, new BigDecimal("10.00"), "external-reference-123");
        OnlinePurchaseAttempt pendingAttempt = new OnlinePurchaseAttempt(preferenceRequest, null);
        CheckoutPreferenceResponse preference =
                new CheckoutPreferenceResponse("preference-123", "https://checkout.example.com", "collector-123");
        TransactionCreateResponse persistedResponse = new TransactionCreateResponse(
                "external-reference-123", "4821", "preference-123", "https://checkout.example.com");
        when(purchaseIntentService.findOnline(idempotencyKey, requestHash))
                .thenReturn(Optional.empty(), Optional.of(pendingAttempt));
        when(rafflePricingService.calculate(2, null)).thenReturn(regularPurchasePrice());
        when(purchaseIntentService.prepareOnline(
                        idempotencyKey,
                        requestHash,
                        "Guest User",
                        "11999999999",
                        null,
                        null,
                        2,
                        regularPurchasePrice()))
                .thenReturn(pendingAttempt);
        when(paymentProviderClient.createPreference(preferenceRequest, idempotencyKey))
                .thenThrow(new ExternalPaymentException("response lost", new java.net.SocketTimeoutException()))
                .thenReturn(preference);
        when(purchaseIntentService.completeOnline(idempotencyKey, requestHash, preference))
                .thenReturn(persistedResponse);

        assertThatThrownBy(() -> transactionService.create(
                        idempotencyKey, new TransactionCreateRequest("Guest User", "(11) 99999-9999", 2)))
                .isInstanceOf(ExternalPaymentException.class);

        assertThat(transactionService.create(
                        idempotencyKey, new TransactionCreateRequest("Guest User", "(11) 99999-9999", 2)))
                .isEqualTo(persistedResponse);
        verify(paymentProviderClient, times(2)).createPreference(preferenceRequest, idempotencyKey);
        verify(purchaseIntentService, times(1))
                .prepareOnline(
                        idempotencyKey,
                        requestHash,
                        "Guest User",
                        "11999999999",
                        null,
                        null,
                        2,
                        regularPurchasePrice());
    }

    @Test
    void retryAfterDatabaseFailureFollowingExternalCreationPersistsTheSameCheckout() {
        TransactionServiceImpl transactionService = transactionService();
        String idempotencyKey = "checkout-key-123";
        String requestHash = purchaseRequestHasher.online("Guest User", "11999999999", 2);
        CheckoutPreferenceRequest preferenceRequest = new CheckoutPreferenceRequest(
                "Guest User", "11999999999", null, 2, new BigDecimal("10.00"), "external-reference-123");
        OnlinePurchaseAttempt pendingAttempt = new OnlinePurchaseAttempt(preferenceRequest, null);
        CheckoutPreferenceResponse preference =
                new CheckoutPreferenceResponse("preference-123", "https://checkout.example.com", "collector-123");
        TransactionCreateResponse persistedResponse = new TransactionCreateResponse(
                "external-reference-123", "4821", "preference-123", "https://checkout.example.com");
        when(purchaseIntentService.findOnline(idempotencyKey, requestHash))
                .thenReturn(Optional.empty(), Optional.of(pendingAttempt));
        when(rafflePricingService.calculate(2, null)).thenReturn(regularPurchasePrice());
        when(purchaseIntentService.prepareOnline(
                        idempotencyKey,
                        requestHash,
                        "Guest User",
                        "11999999999",
                        null,
                        null,
                        2,
                        regularPurchasePrice()))
                .thenReturn(pendingAttempt);
        when(paymentProviderClient.createPreference(preferenceRequest, idempotencyKey))
                .thenReturn(preference);
        when(purchaseIntentService.completeOnline(idempotencyKey, requestHash, preference))
                .thenThrow(new DataIntegrityViolationException("commit failed"))
                .thenReturn(persistedResponse);

        assertThatThrownBy(() -> transactionService.create(
                        idempotencyKey, new TransactionCreateRequest("Guest User", "(11) 99999-9999", 2)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(transactionService.create(
                        idempotencyKey, new TransactionCreateRequest("Guest User", "(11) 99999-9999", 2)))
                .isEqualTo(persistedResponse);
        verify(paymentProviderClient, times(2)).createPreference(preferenceRequest, idempotencyKey);
    }

    @Test
    void fetchesPaymentOutsideTheDatabaseProcessorAndDelegatesReconciliation() {
        TransactionServiceImpl transactionService = transactionService();
        PaymentProviderPayment payment = payment("123", "external-reference-123", "approved");
        when(paymentProviderClient.getPayment("123")).thenReturn(payment);

        transactionService.processPaymentNotification("123");

        verify(paymentReconciliationService).reconcile("123", null, payment);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void returnsCurrentStatusWithLuckyNumbers() {
        TransactionServiceImpl transactionService = transactionService();
        Transaction transaction = new Transaction(
                "guest@example.com", 2, new BigDecimal("20.00"), PaymentStatus.APPROVED, "external-reference-123");
        transaction.assignRecoveryCode("4821");
        when(transactionRepository.findByExternalReference("external-reference-123"))
                .thenReturn(Optional.of(transaction));
        when(luckyNumberService.findNumbers("external-reference-123")).thenReturn(List.of("00001", "00002"));
        when(luckyNumberService.findPreviousApprovedNumbers("0000000000", "external-reference-123"))
                .thenReturn(List.of("00090", "00091"));

        var response = transactionService.getStatus("external-reference-123", null);

        assertThat(response.externalReference()).isEqualTo("external-reference-123");
        assertThat(response.recoveryCode()).isEqualTo("4821");
        assertThat(response.status()).isEqualTo(PaymentStatusResponse.APROVADO);
        assertThat(response.quantity()).isEqualTo(2);
        assertThat(response.totalAmount()).isEqualByComparingTo("20.00");
        assertThat(response.luckyNumbers()).containsExactly("00001", "00002");
        assertThat(response.previousLuckyNumbers()).containsExactly("00090", "00091");
        assertThat(response.totalLuckyNumbers()).isEqualTo(4);
        assertThat(response.checkoutUrl()).isNull();
    }

    @Test
    void returnsCheckoutUrlForPendingTransactionStatus() {
        TransactionServiceImpl transactionService = transactionService();
        Transaction transaction = new Transaction(
                "guest@example.com", 2, new BigDecimal("20.00"), PaymentStatus.PENDING, "external-reference-123");
        transaction.assignPreference("preference-123", "https://checkout.example.com", "collector-123");
        transaction.assignRecoveryCode("4821");
        when(transactionRepository.findByExternalReference("external-reference-123"))
                .thenReturn(Optional.of(transaction));
        when(luckyNumberService.findNumbers("external-reference-123")).thenReturn(List.of());

        var response = transactionService.getStatus("external-reference-123", null);

        assertThat(response.status()).isEqualTo(PaymentStatusResponse.PENDENTE);
        assertThat(response.checkoutUrl()).isEqualTo("https://checkout.example.com");
    }

    @Test
    void statusFallbackGeneratesLuckyNumbersWhenPaymentBecomesApproved() {
        TransactionServiceImpl transactionService = transactionService();
        Transaction transaction = new Transaction(
                "guest@example.com", 2, new BigDecimal("20.00"), PaymentStatus.PENDING, "external-reference-123");
        transaction.markPaymentState(
                PaymentStatus.PENDING, "123", java.time.OffsetDateTime.parse("2026-08-22T12:00:00Z"), (short) 10, 1L);
        when(transactionRepository.findByExternalReference("external-reference-123"))
                .thenReturn(Optional.of(transaction));
        PaymentProviderPayment payment = payment("123", "external-reference-123", "approved");
        when(pendingPaymentReconciliationCoordinator.reconcileIfDue(transaction))
                .thenAnswer(invocation -> {
                    transaction.markPaymentState(
                            PaymentStatus.APPROVED, "123", payment.dateLastUpdated(), (short) 40, 2L);
                    return true;
                });
        when(luckyNumberService.findNumbers("external-reference-123")).thenReturn(List.of("00001", "00002"));
        when(luckyNumberService.findPreviousApprovedNumbers("0000000000", "external-reference-123"))
                .thenReturn(List.of());

        var response = transactionService.getStatus("external-reference-123", null);

        assertThat(response.status()).isEqualTo(PaymentStatusResponse.APROVADO);
        verify(pendingPaymentReconciliationCoordinator).reconcileIfDue(transaction);
    }

    @Test
    void statusMaterializesTransactionAndReconcilesRedirectPayment() {
        TransactionServiceImpl transactionService = transactionService();
        Transaction materializedTransaction = new Transaction(
                "Guest User",
                "11999999999",
                null,
                2,
                new BigDecimal("10.00"),
                new BigDecimal("20.00"),
                PaymentStatus.PENDING,
                PaymentMethod.MERCADO_PAGO,
                "external-reference-123");
        materializedTransaction.assignRecoveryCode("4821");
        PaymentProviderPayment payment = payment("123", "external-reference-123", "approved");
        when(transactionRepository.findByExternalReference("external-reference-123"))
                .thenReturn(Optional.empty(), Optional.of(materializedTransaction));
        when(purchaseIntentService.materializeOnlineTransaction("external-reference-123"))
                .thenReturn(materializedTransaction);
        when(paymentProviderClient.getPayment("123")).thenReturn(payment);
        when(luckyNumberService.findNumbers("external-reference-123")).thenReturn(List.of());

        var response = transactionService.getStatus("external-reference-123", " 123 ");

        assertThat(response.externalReference()).isEqualTo("external-reference-123");
        assertThat(response.recoveryCode()).isEqualTo("4821");
        verify(purchaseIntentService).materializeOnlineTransaction("external-reference-123");
        verify(paymentReconciliationService).reconcile("123", "external-reference-123", payment);
    }

    @Test
    void recoversTransactionByPhoneAndRecoveryCode() {
        TransactionServiceImpl transactionService = transactionService();
        Transaction transaction = new Transaction(
                "Guest User",
                "11999999999",
                null,
                1,
                new BigDecimal("10.00"),
                PaymentStatus.APPROVED,
                PaymentMethod.MERCADO_PAGO,
                "external-reference-123");
        transaction.assignRecoveryCode("4821");
        when(transactionRepository.findByPhoneAndRecoveryCodeOrderByCreatedAtDesc("11999999999", "4821"))
                .thenReturn(List.of(transaction));
        when(luckyNumberService.findApprovedNumbersByPhone("11999999999"))
                .thenReturn(List.of("00042", "00090", "00091"));

        var response = transactionService.recover(new TransactionRecoveryRequest("(11) 99999-9999", "4821"));

        assertThat(response.externalReference()).isEqualTo("external-reference-123");
        assertThat(response.recoveryCode()).isEqualTo("4821");
        assertThat(response.status()).isEqualTo(PaymentStatusResponse.APROVADO);
        assertThat(response.luckyNumbers()).containsExactly("00042", "00090", "00091");
        assertThat(response.previousLuckyNumbers()).isEmpty();
        assertThat(response.totalLuckyNumbers()).isEqualTo(3);
    }

    private TransactionServiceImpl transactionService() {
        return new TransactionServiceImpl(
                raffleConfigService,
                transactionRepository,
                paymentProviderClient,
                luckyNumberService,
                paymentReconciliationService,
                pendingPaymentReconciliationCoordinator,
                purchaseIntentService,
                purchaseRequestHasher,
                rafflePricingService);
    }

    private static PurchasePrice regularPurchasePrice() {
        return new PurchasePrice(new BigDecimal("10.00"), new BigDecimal("20.00"), null);
    }

    private static PaymentProviderPayment payment(String paymentId, String externalReference, String status) {
        return new PaymentProviderPayment(
                paymentId,
                externalReference,
                externalReference,
                "preference-123",
                "collector-123",
                new BigDecimal("20.00"),
                "BRL",
                status,
                "accredited",
                java.time.OffsetDateTime.parse("2026-08-22T11:00:00Z"),
                java.time.OffsetDateTime.parse("2026-08-22T12:00:00Z"));
    }
}
