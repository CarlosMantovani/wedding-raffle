package com.weddingraffle.rifa.service.impl;

import com.weddingraffle.rifa.dto.AdminGiftMessageResponse;
import com.weddingraffle.rifa.dto.AdminTransactionResponse;
import com.weddingraffle.rifa.dto.AdminTransactionSummaryResponse;
import com.weddingraffle.rifa.dto.CapacityReviewDecision;
import com.weddingraffle.rifa.dto.CashTransactionCreateRequest;
import com.weddingraffle.rifa.dto.CashTransactionCreateResponse;
import com.weddingraffle.rifa.dto.PaymentStatusResponse;
import com.weddingraffle.rifa.entity.CapacityReviewStatus;
import com.weddingraffle.rifa.entity.LuckyNumber;
import com.weddingraffle.rifa.entity.PaymentMethod;
import com.weddingraffle.rifa.entity.PaymentStatus;
import com.weddingraffle.rifa.entity.Transaction;
import com.weddingraffle.rifa.exception.InvalidRaffleStateException;
import com.weddingraffle.rifa.exception.InvalidTransactionStateException;
import com.weddingraffle.rifa.exception.ResourceNotFoundException;
import com.weddingraffle.rifa.repository.AdminTransactionSummaryProjection;
import com.weddingraffle.rifa.repository.LuckyNumberRepository;
import com.weddingraffle.rifa.repository.TransactionRepository;
import com.weddingraffle.rifa.service.AdminTransactionService;
import com.weddingraffle.rifa.service.CapacityReservationService;
import com.weddingraffle.rifa.service.PurchaseIntentService;
import com.weddingraffle.rifa.service.RaffleConfigService;
import com.weddingraffle.rifa.util.ParticipantNormalizer;
import com.weddingraffle.rifa.util.PurchaseRequestHasher;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminTransactionServiceImpl implements AdminTransactionService {

    private final RaffleConfigService raffleConfigService;
    private final TransactionRepository transactionRepository;
    private final LuckyNumberRepository luckyNumberRepository;
    private final CapacityReservationService capacityReservationService;
    private final PurchaseIntentService purchaseIntentService;
    private final PurchaseRequestHasher purchaseRequestHasher;

    public AdminTransactionServiceImpl(
            RaffleConfigService raffleConfigService,
            TransactionRepository transactionRepository,
            LuckyNumberRepository luckyNumberRepository,
            CapacityReservationService capacityReservationService,
            PurchaseIntentService purchaseIntentService,
            PurchaseRequestHasher purchaseRequestHasher) {
        this.raffleConfigService = raffleConfigService;
        this.transactionRepository = transactionRepository;
        this.luckyNumberRepository = luckyNumberRepository;
        this.capacityReservationService = capacityReservationService;
        this.purchaseIntentService = purchaseIntentService;
        this.purchaseRequestHasher = purchaseRequestHasher;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminTransactionSummaryResponse getSummary() {
        AdminTransactionSummaryProjection summary = transactionRepository.getAdminSummary();
        return new AdminTransactionSummaryResponse(
                summary.getTotalTransactions(), summary.getApprovedLuckyNumbers(), summary.getApprovedRevenue());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminTransactionResponse> list(String query, Pageable pageable) {
        Page<Transaction> transactions = StringUtils.hasText(query)
                ? transactionRepository.findByNameOrPhone(query.trim(), normalizePhoneSearch(query), pageable)
                : transactionRepository.findAll(pageable);
        Map<Transaction, List<String>> numbersByTransaction = numbersByTransaction(transactions.getContent());
        return transactions.map(transaction -> toResponse(transaction, numbersByTransaction));
    }

    @Override
    public CashTransactionCreateResponse createCashTransaction(
            String idempotencyKey, CashTransactionCreateRequest request) {
        String normalizedIdempotencyKey = purchaseRequestHasher.normalizeIdempotencyKey(idempotencyKey);
        String name = ParticipantNormalizer.normalizeName(request.name());
        String phone = ParticipantNormalizer.normalizePhone(request.phone());
        String email = ParticipantNormalizer.normalizeEmail(request.email());
        String giftMessage = ParticipantNormalizer.normalizeGiftMessage(request.giftMessage());
        String requestHash = purchaseRequestHasher.cash(name, phone, email, giftMessage, request.quantity());

        return purchaseIntentService
                .findCash(normalizedIdempotencyKey, requestHash)
                .orElseGet(() -> createCashTransaction(
                        normalizedIdempotencyKey, requestHash, name, phone, email, giftMessage, request.quantity()));
    }

    private CashTransactionCreateResponse createCashTransaction(
            String idempotencyKey,
            String requestHash,
            String name,
            String phone,
            String email,
            String giftMessage,
            int quantity) {
        ensureDrawIsOpen();
        BigDecimal unitPrice = raffleConfigService.getCurrentUnitPrice();
        BigDecimal totalAmount = unitPrice.multiply(BigDecimal.valueOf(quantity));
        try {
            return purchaseIntentService.createCash(
                    idempotencyKey, requestHash, name, phone, email, giftMessage, quantity, unitPrice, totalAmount);
        } catch (DataIntegrityViolationException exception) {
            return purchaseIntentService.findCash(idempotencyKey, requestHash).orElseThrow(() -> exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminGiftMessageResponse> listGiftMessages(Pageable pageable) {
        return transactionRepository
                .findByGiftMessageIsNotNullAndGiftMessageNot("", pageable)
                .map(transaction -> new AdminGiftMessageResponse(
                        transaction.getExternalReference(),
                        transaction.getCreatedAt(),
                        transaction.getName(),
                        transaction.getGiftMessage()));
    }

    @Override
    @Transactional
    public void deleteCashTransaction(String externalReference) {
        Transaction transaction = transactionRepository
                .findByExternalReference(externalReference)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found."));

        if (transaction.getPaymentMethod() != PaymentMethod.CASH) {
            throw new InvalidTransactionStateException("Only cash transactions can be deleted.");
        }

        luckyNumberRepository.deleteByTransaction(transaction);
        capacityReservationService.releaseAllocation(externalReference);
        transactionRepository.delete(transaction);
    }

    @Override
    @Transactional
    public void resolveCapacityReview(String externalReference, CapacityReviewDecision decision) {
        Transaction transaction = transactionRepository
                .findByExternalReference(externalReference)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found."));
        if (transaction.getPaymentMethod() != PaymentMethod.MERCADO_PAGO
                || transaction.getStatus() != PaymentStatus.APPROVED
                || transaction.getCapacityReviewStatus() != CapacityReviewStatus.PENDING) {
            throw new InvalidTransactionStateException("Transaction is not pending capacity review.");
        }

        CapacityReviewStatus resolution =
                switch (decision) {
                    case REFUND_COMPLETED -> CapacityReviewStatus.REFUND_COMPLETED;
                    case CONTRIBUTION_WITHOUT_NUMBERS -> CapacityReviewStatus.CONTRIBUTION_WITHOUT_NUMBERS;
                };
        transaction.completeCapacityReview(resolution);
        transactionRepository.save(transaction);
    }

    private Map<Transaction, List<String>> numbersByTransaction(List<Transaction> transactions) {
        if (transactions.isEmpty()) {
            return Collections.emptyMap();
        }
        return luckyNumberRepository.findByTransactionInOrderByNumberAsc(transactions).stream()
                .collect(Collectors.groupingBy(
                        LuckyNumber::getTransaction, Collectors.mapping(LuckyNumber::getNumber, Collectors.toList())));
    }

    private void ensureDrawIsOpen() {
        if (raffleConfigService.isDrawClosed()) {
            throw new InvalidRaffleStateException("Draw is closed. No more numbers can be purchased.");
        }
    }

    private static String normalizePhoneSearch(String query) {
        return query.replaceAll("\\D", "");
    }

    private static AdminTransactionResponse toResponse(
            Transaction transaction, Map<Transaction, List<String>> numbersByTransaction) {
        return new AdminTransactionResponse(
                transaction.getExternalReference(),
                transaction.getCreatedAt(),
                transaction.getName(),
                transaction.getPhone(),
                transaction.getEmail(),
                transaction.getGiftMessage(),
                transaction.getPaymentMethod(),
                transaction.getCapacityReviewStatus(),
                transaction.getQuantity(),
                transaction.getTotalAmount(),
                PaymentStatusResponse.from(transaction.getStatus()),
                numbersByTransaction.getOrDefault(transaction, List.of()));
    }
}
