package com.weddingraffle.rifa.service.impl;

import com.weddingraffle.rifa.dto.PaymentStatusResponse;
import com.weddingraffle.rifa.dto.TransactionCreateRequest;
import com.weddingraffle.rifa.dto.TransactionCreateResponse;
import com.weddingraffle.rifa.dto.TransactionQuoteRequest;
import com.weddingraffle.rifa.dto.TransactionQuoteResponse;
import com.weddingraffle.rifa.dto.TransactionRecoveryRequest;
import com.weddingraffle.rifa.dto.TransactionStatusResponse;
import com.weddingraffle.rifa.entity.PaymentStatus;
import com.weddingraffle.rifa.entity.Transaction;
import com.weddingraffle.rifa.exception.InvalidRaffleStateException;
import com.weddingraffle.rifa.exception.ResourceNotFoundException;
import com.weddingraffle.rifa.integration.CheckoutPreferenceResponse;
import com.weddingraffle.rifa.integration.PaymentProviderClient;
import com.weddingraffle.rifa.integration.PaymentProviderPayment;
import com.weddingraffle.rifa.repository.TransactionRepository;
import com.weddingraffle.rifa.service.LuckyNumberService;
import com.weddingraffle.rifa.service.OnlinePurchaseAttempt;
import com.weddingraffle.rifa.service.PaymentReconciliationService;
import com.weddingraffle.rifa.service.PurchaseIntentService;
import com.weddingraffle.rifa.service.RaffleConfigService;
import com.weddingraffle.rifa.service.TransactionService;
import com.weddingraffle.rifa.util.ParticipantNormalizer;
import com.weddingraffle.rifa.util.PurchaseRequestHasher;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class TransactionServiceImpl implements TransactionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionServiceImpl.class);

    private final RaffleConfigService raffleConfigService;
    private final TransactionRepository transactionRepository;
    private final PaymentProviderClient paymentProviderClient;
    private final LuckyNumberService luckyNumberService;
    private final PaymentReconciliationService paymentReconciliationService;
    private final PurchaseIntentService purchaseIntentService;
    private final PurchaseRequestHasher purchaseRequestHasher;

    public TransactionServiceImpl(
            RaffleConfigService raffleConfigService,
            TransactionRepository transactionRepository,
            PaymentProviderClient paymentProviderClient,
            LuckyNumberService luckyNumberService,
            PaymentReconciliationService paymentReconciliationService,
            PurchaseIntentService purchaseIntentService,
            PurchaseRequestHasher purchaseRequestHasher) {
        this.raffleConfigService = raffleConfigService;
        this.transactionRepository = transactionRepository;
        this.paymentProviderClient = paymentProviderClient;
        this.luckyNumberService = luckyNumberService;
        this.paymentReconciliationService = paymentReconciliationService;
        this.purchaseIntentService = purchaseIntentService;
        this.purchaseRequestHasher = purchaseRequestHasher;
    }

    @Override
    public TransactionQuoteResponse quote(TransactionQuoteRequest request) {
        ensureDrawIsOpen();
        String name = ParticipantNormalizer.normalizeName(request.name());
        String phone = ParticipantNormalizer.normalizePhone(request.phone());
        BigDecimal unitPrice = raffleConfigService.getCurrentUnitPrice();
        BigDecimal totalAmount = unitPrice.multiply(BigDecimal.valueOf(request.quantity()));
        return new TransactionQuoteResponse(name, phone, request.quantity(), unitPrice, totalAmount);
    }

    @Override
    public TransactionCreateResponse create(String idempotencyKey, TransactionCreateRequest request) {
        String normalizedIdempotencyKey = purchaseRequestHasher.normalizeIdempotencyKey(idempotencyKey);
        String name = ParticipantNormalizer.normalizeName(request.name());
        String phone = ParticipantNormalizer.normalizePhone(request.phone());
        String requestHash = purchaseRequestHasher.online(name, phone, request.quantity());

        OnlinePurchaseAttempt attempt = purchaseIntentService
                .findOnline(normalizedIdempotencyKey, requestHash)
                .orElseGet(() ->
                        prepareOnlineAttempt(normalizedIdempotencyKey, requestHash, name, phone, request.quantity()));
        if (attempt.isCompleted()) {
            return attempt.completedResponse();
        }

        CheckoutPreferenceResponse preference =
                paymentProviderClient.createPreference(attempt.checkoutPreferenceRequest(), normalizedIdempotencyKey);
        TransactionCreateResponse response =
                purchaseIntentService.completeOnline(normalizedIdempotencyKey, requestHash, preference);
        LOGGER.info("Completed pending transaction checkout with externalReference={}", response.externalReference());
        return response;
    }

    private OnlinePurchaseAttempt prepareOnlineAttempt(
            String idempotencyKey, String requestHash, String name, String phone, int quantity) {
        ensureDrawIsOpen();
        BigDecimal unitPrice = raffleConfigService.getCurrentUnitPrice();
        BigDecimal totalAmount = unitPrice.multiply(BigDecimal.valueOf(quantity));
        try {
            return purchaseIntentService.prepareOnline(
                    idempotencyKey, requestHash, name, phone, quantity, unitPrice, totalAmount);
        } catch (DataIntegrityViolationException exception) {
            return purchaseIntentService.findOnline(idempotencyKey, requestHash).orElseThrow(() -> exception);
        }
    }

    @Override
    public void processPaymentNotification(String paymentId) {
        PaymentProviderPayment payment = paymentProviderClient.getPayment(paymentId);
        paymentReconciliationService.reconcile(paymentId, null, payment);
    }

    @Override
    public TransactionStatusResponse getStatus(String externalReference) {
        Transaction transaction = transactionRepository
                .findByExternalReference(externalReference)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found."));

        if (transaction.getStatus() == PaymentStatus.PENDING && transaction.getMpPaymentId() != null) {
            refreshPendingTransaction(transaction);
            transaction = transactionRepository
                    .findByExternalReference(externalReference)
                    .orElseThrow(() -> new ResourceNotFoundException("Transaction not found."));
        }

        return toStatusResponse(transaction);
    }

    @Override
    public TransactionStatusResponse recover(TransactionRecoveryRequest request) {
        String phone = ParticipantNormalizer.normalizePhone(request.phone());
        List<Transaction> transactions =
                transactionRepository.findByPhoneAndRecoveryCodeOrderByCreatedAtDesc(phone, request.recoveryCode());
        if (transactions.isEmpty()) {
            throw new ResourceNotFoundException("Transaction not found.");
        }

        boolean refreshed = false;
        for (Transaction transaction : transactions) {
            if (transaction.getStatus() == PaymentStatus.PENDING && transaction.getMpPaymentId() != null) {
                refreshPendingTransaction(transaction);
                refreshed = true;
            }
        }

        if (refreshed) {
            transactions =
                    transactionRepository.findByPhoneAndRecoveryCodeOrderByCreatedAtDesc(phone, request.recoveryCode());
        }

        List<Transaction> currentTransactions = transactions;
        return currentTransactions.stream()
                .filter(transaction -> transaction.getStatus() == PaymentStatus.APPROVED)
                .findFirst()
                .map(this::toRecoveryResponse)
                .orElseGet(() -> toStatusResponse(currentTransactions.getFirst()));
    }

    private void refreshPendingTransaction(Transaction transaction) {
        PaymentProviderPayment payment = paymentProviderClient.getPayment(transaction.getMpPaymentId());
        paymentReconciliationService.reconcile(
                transaction.getMpPaymentId(), transaction.getExternalReference(), payment);
    }

    private TransactionStatusResponse toStatusResponse(Transaction transaction) {
        List<String> luckyNumbers = luckyNumberService.findNumbers(transaction.getExternalReference());
        List<String> previousLuckyNumbers = transaction.getStatus() == PaymentStatus.APPROVED
                ? luckyNumberService.findPreviousApprovedNumbers(
                        transaction.getPhone(), transaction.getExternalReference())
                : List.of();
        return new TransactionStatusResponse(
                transaction.getExternalReference(),
                transaction.getRecoveryCode(),
                PaymentStatusResponse.from(transaction.getStatus()),
                transaction.getQuantity(),
                transaction.getTotalAmount(),
                transaction.getParticipantFlagName(),
                transaction.getParticipantFlagEmoji(),
                luckyNumbers,
                previousLuckyNumbers,
                luckyNumbers.size() + previousLuckyNumbers.size());
    }

    private TransactionStatusResponse toRecoveryResponse(Transaction transaction) {
        List<String> luckyNumbers = luckyNumberService.findApprovedNumbersByPhone(transaction.getPhone());
        return new TransactionStatusResponse(
                transaction.getExternalReference(),
                transaction.getRecoveryCode(),
                PaymentStatusResponse.from(PaymentStatus.APPROVED),
                luckyNumbers.size(),
                BigDecimal.ZERO,
                transaction.getParticipantFlagName(),
                transaction.getParticipantFlagEmoji(),
                luckyNumbers,
                List.of(),
                luckyNumbers.size());
    }

    private void ensureDrawIsOpen() {
        if (raffleConfigService.isDrawClosed()) {
            throw new InvalidRaffleStateException("Draw is closed. No more numbers can be purchased.");
        }
    }
}
