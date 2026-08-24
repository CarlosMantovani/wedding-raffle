package com.weddingraffle.rifa.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weddingraffle.rifa.dto.CashTransactionCreateResponse;
import com.weddingraffle.rifa.dto.PaymentStatusResponse;
import com.weddingraffle.rifa.dto.TransactionCreateResponse;
import com.weddingraffle.rifa.entity.LuckyNumber;
import com.weddingraffle.rifa.entity.PaymentMethod;
import com.weddingraffle.rifa.entity.PaymentStatus;
import com.weddingraffle.rifa.entity.PurchaseIntent;
import com.weddingraffle.rifa.entity.PurchaseIntentAction;
import com.weddingraffle.rifa.entity.PurchaseIntentStatus;
import com.weddingraffle.rifa.entity.Transaction;
import com.weddingraffle.rifa.exception.IdempotencyConflictException;
import com.weddingraffle.rifa.exception.InvalidTransactionStateException;
import com.weddingraffle.rifa.exception.ResourceNotFoundException;
import com.weddingraffle.rifa.integration.CheckoutPreferenceRequest;
import com.weddingraffle.rifa.integration.CheckoutPreferenceResponse;
import com.weddingraffle.rifa.repository.PurchaseIntentRepository;
import com.weddingraffle.rifa.repository.TransactionRepository;
import com.weddingraffle.rifa.service.CapacityAllocationResult;
import com.weddingraffle.rifa.service.CapacityReservationService;
import com.weddingraffle.rifa.service.LuckyNumberService;
import com.weddingraffle.rifa.service.OnlinePurchaseAttempt;
import com.weddingraffle.rifa.service.ParticipantFlagService;
import com.weddingraffle.rifa.service.PurchaseIntentService;
import com.weddingraffle.rifa.service.PurchasePrice;
import com.weddingraffle.rifa.service.RecoveryCodeService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseIntentServiceImpl implements PurchaseIntentService {

    private final PurchaseIntentRepository purchaseIntentRepository;
    private final TransactionRepository transactionRepository;
    private final CapacityReservationService capacityReservationService;
    private final LuckyNumberService luckyNumberService;
    private final ParticipantFlagService participantFlagService;
    private final RecoveryCodeService recoveryCodeService;
    private final ObjectMapper objectMapper;

    public PurchaseIntentServiceImpl(
            PurchaseIntentRepository purchaseIntentRepository,
            TransactionRepository transactionRepository,
            CapacityReservationService capacityReservationService,
            LuckyNumberService luckyNumberService,
            ParticipantFlagService participantFlagService,
            RecoveryCodeService recoveryCodeService,
            ObjectMapper objectMapper) {
        this.purchaseIntentRepository = purchaseIntentRepository;
        this.transactionRepository = transactionRepository;
        this.capacityReservationService = capacityReservationService;
        this.luckyNumberService = luckyNumberService;
        this.participantFlagService = participantFlagService;
        this.recoveryCodeService = recoveryCodeService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OnlinePurchaseAttempt> findOnline(String idempotencyKey, String requestHash) {
        return purchaseIntentRepository
                .findByIdempotencyKey(idempotencyKey)
                .map(intent ->
                        toOnlineAttempt(validate(intent, PurchaseIntentAction.MERCADO_PAGO_CHECKOUT, requestHash)));
    }

    @Override
    @Transactional
    public OnlinePurchaseAttempt prepareOnline(
            String idempotencyKey,
            String requestHash,
            String name,
            String phone,
            String giftMessage,
            int quantity,
            PurchasePrice purchasePrice) {
        Optional<PurchaseIntent> existing = purchaseIntentRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return toOnlineAttempt(validate(existing.get(), PurchaseIntentAction.MERCADO_PAGO_CHECKOUT, requestHash));
        }

        String externalReference = UUID.randomUUID().toString();
        PurchaseIntent intent = new PurchaseIntent(
                idempotencyKey, PurchaseIntentAction.MERCADO_PAGO_CHECKOUT, requestHash, externalReference);
        intent.captureOnlineRequest(
                name,
                phone,
                null,
                giftMessage,
                quantity,
                purchasePrice.unitPrice(),
                purchasePrice.totalAmount(),
                purchasePrice.combo());
        purchaseIntentRepository.saveAndFlush(intent);
        return toOnlineAttempt(intent);
    }

    @Override
    @Transactional
    public TransactionCreateResponse completeOnline(
            String idempotencyKey, String requestHash, CheckoutPreferenceResponse preference) {
        PurchaseIntent intent = purchaseIntentRepository
                .findLockedByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase intent not found."));
        validate(intent, PurchaseIntentAction.MERCADO_PAGO_CHECKOUT, requestHash);
        if (intent.getStatus() == PurchaseIntentStatus.COMPLETED) {
            return readResponse(intent, TransactionCreateResponse.class);
        }

        TransactionCreateResponse response = new TransactionCreateResponse(
                intent.getExternalReference(), "", preference.preferenceId(), preference.checkoutUrl());
        intent.completeOnlineCheckout(
                preference.preferenceId(), preference.checkoutUrl(), preference.collectorId(), writeResponse(response));
        return response;
    }

    @Override
    @Transactional
    public Transaction materializeOnlineTransaction(String externalReference) {
        Optional<Transaction> existingTransaction = transactionRepository.findByExternalReference(externalReference);
        if (existingTransaction.isPresent()) {
            return existingTransaction.get();
        }

        PurchaseIntent intent = purchaseIntentRepository
                .findLockedByExternalReference(externalReference)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase intent not found."));
        if (intent.getAction() != PurchaseIntentAction.MERCADO_PAGO_CHECKOUT
                || intent.getStatus() != PurchaseIntentStatus.COMPLETED) {
            throw new ResourceNotFoundException("Transaction not found.");
        }

        existingTransaction = transactionRepository.findByExternalReference(externalReference);
        if (existingTransaction.isPresent()) {
            return existingTransaction.get();
        }

        capacityReservationService.reserve(externalReference, intent.getQuantity());
        Transaction transaction = new Transaction(
                intent.getParticipantName(),
                intent.getParticipantPhone(),
                intent.getParticipantEmail(),
                intent.getGiftMessage(),
                intent.getQuantity(),
                intent.getUnitPrice(),
                intent.getTotalAmount(),
                PaymentStatus.PENDING,
                PaymentMethod.MERCADO_PAGO,
                externalReference);
        transaction.assignPreference(intent.getMpPreferenceId(), intent.getMpCheckoutUrl(), intent.getMpCollectorId());
        transaction.assignRaffleCombo(intent.getRaffleCombo());
        transaction.assignParticipantFlag(participantFlagService.resolveForPhone(intent.getParticipantPhone()));
        transaction.assignRecoveryCode(recoveryCodeService.resolveForPhone(intent.getParticipantPhone()));
        return transactionRepository.save(transaction);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CashTransactionCreateResponse> findCash(String idempotencyKey, String requestHash) {
        return purchaseIntentRepository.findByIdempotencyKey(idempotencyKey).map(intent -> {
            validate(intent, PurchaseIntentAction.CASH_REGISTRATION, requestHash);
            if (intent.getStatus() != PurchaseIntentStatus.COMPLETED) {
                throw new InvalidTransactionStateException("Cash purchase intent is not completed.");
            }
            return readResponse(intent, CashTransactionCreateResponse.class);
        });
    }

    @Override
    @Transactional
    public CashTransactionCreateResponse createCash(
            String idempotencyKey,
            String requestHash,
            String name,
            String phone,
            String email,
            String giftMessage,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal totalAmount) {
        Optional<PurchaseIntent> existing = purchaseIntentRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            PurchaseIntent intent = validate(existing.get(), PurchaseIntentAction.CASH_REGISTRATION, requestHash);
            if (intent.getStatus() != PurchaseIntentStatus.COMPLETED) {
                throw new InvalidTransactionStateException("Cash purchase intent is not completed.");
            }
            return readResponse(intent, CashTransactionCreateResponse.class);
        }

        String externalReference = UUID.randomUUID().toString();
        PurchaseIntent intent = new PurchaseIntent(
                idempotencyKey, PurchaseIntentAction.CASH_REGISTRATION, requestHash, externalReference);
        purchaseIntentRepository.saveAndFlush(intent);

        capacityReservationService.reserve(externalReference, quantity);
        Transaction transaction = new Transaction(
                name,
                phone,
                email,
                giftMessage,
                quantity,
                unitPrice,
                totalAmount,
                PaymentStatus.APPROVED,
                PaymentMethod.CASH,
                externalReference);
        transaction.assignParticipantFlag(participantFlagService.resolveForPhone(phone));
        transaction.assignRecoveryCode(recoveryCodeService.resolveForPhone(phone));
        transactionRepository.save(transaction);

        CapacityAllocationResult allocation = capacityReservationService.allocate(externalReference, quantity);
        if (allocation != CapacityAllocationResult.ALLOCATED) {
            throw new IllegalStateException("Cash transaction capacity was not allocated.");
        }
        List<String> luckyNumbers = luckyNumberService.generateFor(transaction).stream()
                .map(LuckyNumber::getNumber)
                .sorted()
                .toList();
        List<String> previousLuckyNumbers = luckyNumberService.findPreviousApprovedNumbers(phone, externalReference);

        CashTransactionCreateResponse response = new CashTransactionCreateResponse(
                externalReference,
                transaction.getRecoveryCode(),
                transaction.getName(),
                transaction.getPhone(),
                transaction.getEmail(),
                transaction.getPaymentMethod(),
                PaymentStatusResponse.from(transaction.getStatus()),
                transaction.getQuantity(),
                transaction.getTotalAmount(),
                transaction.getParticipantFlagName(),
                transaction.getParticipantFlagEmoji(),
                luckyNumbers,
                previousLuckyNumbers,
                luckyNumbers.size() + previousLuckyNumbers.size());
        intent.complete(writeResponse(response));
        return response;
    }

    private OnlinePurchaseAttempt toOnlineAttempt(PurchaseIntent intent) {
        TransactionCreateResponse completedResponse = intent.getStatus() == PurchaseIntentStatus.COMPLETED
                ? readResponse(intent, TransactionCreateResponse.class)
                : null;
        CheckoutPreferenceRequest preferenceRequest = new CheckoutPreferenceRequest(
                intent.getParticipantName(),
                intent.getParticipantEmail(),
                intent.getQuantity(),
                intent.getUnitPrice(),
                intent.getTotalAmount(),
                intent.getRaffleCombo() != null,
                intent.getExternalReference());
        return new OnlinePurchaseAttempt(preferenceRequest, completedResponse);
    }

    private static PurchaseIntent validate(
            PurchaseIntent intent, PurchaseIntentAction expectedAction, String expectedRequestHash) {
        if (intent.getAction() != expectedAction || !intent.getRequestHash().equals(expectedRequestHash)) {
            throw new IdempotencyConflictException(
                    "The idempotency key was already used with a different purchase request.");
        }
        return intent;
    }

    private String writeResponse(Object response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to persist purchase response.", exception);
        }
    }

    private <T> T readResponse(PurchaseIntent intent, Class<T> responseType) {
        try {
            return objectMapper.readValue(intent.getResponsePayload(), responseType);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to restore persisted purchase response.", exception);
        }
    }
}
