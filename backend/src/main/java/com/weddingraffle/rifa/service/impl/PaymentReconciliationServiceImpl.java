package com.weddingraffle.rifa.service.impl;

import com.weddingraffle.rifa.entity.PaymentEvent;
import com.weddingraffle.rifa.entity.PaymentEventProcessingStatus;
import com.weddingraffle.rifa.entity.PaymentEventReconciliationStatus;
import com.weddingraffle.rifa.entity.PaymentMethod;
import com.weddingraffle.rifa.entity.PaymentProviderName;
import com.weddingraffle.rifa.entity.PaymentStatus;
import com.weddingraffle.rifa.entity.ProviderPayment;
import com.weddingraffle.rifa.entity.Transaction;
import com.weddingraffle.rifa.integration.PaymentProviderPayment;
import com.weddingraffle.rifa.repository.PaymentEventRepository;
import com.weddingraffle.rifa.repository.ProviderPaymentRepository;
import com.weddingraffle.rifa.repository.TransactionRepository;
import com.weddingraffle.rifa.service.CapacityAllocationResult;
import com.weddingraffle.rifa.service.CapacityReservationService;
import com.weddingraffle.rifa.service.LuckyNumberService;
import com.weddingraffle.rifa.service.ParticipantFlagService;
import com.weddingraffle.rifa.service.PaymentReconciliationService;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PaymentReconciliationServiceImpl implements PaymentReconciliationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentReconciliationServiceImpl.class);
    private static final String EXPECTED_CURRENCY = "BRL";

    private final TransactionRepository transactionRepository;
    private final ProviderPaymentRepository providerPaymentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final CapacityReservationService capacityReservationService;
    private final LuckyNumberService luckyNumberService;
    private final ParticipantFlagService participantFlagService;
    private final PaymentStateMachine paymentStateMachine;
    private final PaymentEventKeyFactory paymentEventKeyFactory;
    private final Clock clock;

    public PaymentReconciliationServiceImpl(
            TransactionRepository transactionRepository,
            ProviderPaymentRepository providerPaymentRepository,
            PaymentEventRepository paymentEventRepository,
            CapacityReservationService capacityReservationService,
            LuckyNumberService luckyNumberService,
            ParticipantFlagService participantFlagService,
            PaymentStateMachine paymentStateMachine,
            PaymentEventKeyFactory paymentEventKeyFactory,
            Clock clock) {
        this.transactionRepository = transactionRepository;
        this.providerPaymentRepository = providerPaymentRepository;
        this.paymentEventRepository = paymentEventRepository;
        this.capacityReservationService = capacityReservationService;
        this.luckyNumberService = luckyNumberService;
        this.participantFlagService = participantFlagService;
        this.paymentStateMachine = paymentStateMachine;
        this.paymentEventKeyFactory = paymentEventKeyFactory;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PaymentEventProcessingStatus reconcile(
            String requestedPaymentId, String expectedExternalReference, PaymentProviderPayment payment) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Optional<Transaction> transaction = findTransaction(expectedExternalReference, payment.externalReference());
        String providerPaymentId = StringUtils.hasText(payment.paymentId()) ? payment.paymentId() : requestedPaymentId;

        providerPaymentRepository.insertIfAbsent(
                PaymentProviderName.MERCADO_PAGO.name(),
                providerPaymentId,
                transaction.map(Transaction::getId).orElse(null));
        ProviderPayment providerPayment = providerPaymentRepository
                .findLocked(PaymentProviderName.MERCADO_PAGO, providerPaymentId)
                .orElseThrow(() -> new IllegalStateException("Provider payment ledger entry was not persisted."));

        String eventKey = paymentEventKeyFactory.create(payment);
        Optional<PaymentEvent> duplicate = paymentEventRepository.findByEventKey(eventKey);
        if (duplicate.isPresent()) {
            PaymentEvent event = duplicate.get();
            event.registerDuplicate(now);
            transaction.ifPresent(this::applyConsolidatedApprovalIfEligible);
            LOGGER.info(
                    "Ignored duplicate payment event paymentId={} status={} deliveries={}",
                    providerPaymentId,
                    payment.status(),
                    event.getDeliveryCount());
            return event.getProcessingStatus();
        }

        PaymentEvent event = paymentEventRepository.saveAndFlush(
                new PaymentEvent(providerPayment, eventKey, requestedPaymentId, payment, now));
        List<PaymentReconciliationFailure> failures = validate(
                requestedPaymentId, expectedExternalReference, payment, transaction.orElse(null), providerPayment);
        if (!failures.isEmpty()) {
            event.markRejected(join(failures), now);
            LOGGER.warn(
                    "Rejected payment reconciliation paymentId={} failures={}",
                    providerPaymentId,
                    event.getFailureReasons());
            return event.getProcessingStatus();
        }

        Transaction lockedTransaction = transaction.orElseThrow();
        if (providerPayment.getTransaction() == null) {
            providerPayment.linkTo(lockedTransaction);
        }
        PaymentStatus candidate =
                paymentStateMachine.mapProviderStatus(payment.status()).orElseThrow();
        if (!paymentStateMachine.shouldApply(lockedTransaction, candidate, payment.dateLastUpdated())) {
            event.markObsolete(now);
            applyConsolidatedApprovalIfEligible(lockedTransaction);
            LOGGER.info(
                    "Ignored obsolete payment event paymentId={} status={} providerUpdatedAt={}",
                    providerPaymentId,
                    candidate,
                    payment.dateLastUpdated());
            return event.getProcessingStatus();
        }

        lockedTransaction.markPaymentState(
                candidate,
                providerPaymentId,
                payment.dateLastUpdated(),
                paymentStateMachine.priority(candidate),
                event.getId());
        event.markApplied(now);
        applyConsolidatedApprovalIfEligible(lockedTransaction);
        LOGGER.info(
                "Applied reconciled payment event paymentId={} externalReference={} status={}",
                providerPaymentId,
                lockedTransaction.getExternalReference(),
                candidate);
        return event.getProcessingStatus();
    }

    private Optional<Transaction> findTransaction(String expectedExternalReference, String providerExternalReference) {
        String reference =
                StringUtils.hasText(expectedExternalReference) ? expectedExternalReference : providerExternalReference;
        if (!StringUtils.hasText(reference)) {
            return Optional.empty();
        }
        return transactionRepository.findLockedByExternalReference(reference);
    }

    private List<PaymentReconciliationFailure> validate(
            String requestedPaymentId,
            String expectedExternalReference,
            PaymentProviderPayment payment,
            Transaction transaction,
            ProviderPayment providerPayment) {
        List<PaymentReconciliationFailure> failures = new ArrayList<>();
        addIf(
                failures,
                !Objects.equals(requestedPaymentId, payment.paymentId()),
                PaymentReconciliationFailure.PAYMENT_ID_MISMATCH);
        addIf(failures, transaction == null, PaymentReconciliationFailure.TRANSACTION_NOT_FOUND);
        addIf(failures, payment.dateLastUpdated() == null, PaymentReconciliationFailure.MISSING_PROVIDER_TIMESTAMP);
        addIf(
                failures,
                paymentStateMachine.mapProviderStatus(payment.status()).isEmpty(),
                PaymentReconciliationFailure.UNSUPPORTED_PROVIDER_STATUS);
        if (transaction == null) {
            return failures;
        }

        addIf(
                failures,
                transaction.getPaymentMethod() != PaymentMethod.MERCADO_PAGO,
                PaymentReconciliationFailure.PAYMENT_METHOD_MISMATCH);
        addIf(
                failures,
                providerPayment.getTransaction() != null
                        && !providerPayment.getTransaction().getId().equals(transaction.getId()),
                PaymentReconciliationFailure.PAYMENT_ID_ALREADY_LINKED);
        addIf(
                failures,
                payment.transactionAmount() == null
                        || transaction.getTotalAmount().compareTo(payment.transactionAmount()) != 0,
                PaymentReconciliationFailure.AMOUNT_MISMATCH);
        addIf(
                failures,
                !EXPECTED_CURRENCY.equals(payment.currencyId()),
                PaymentReconciliationFailure.CURRENCY_MISMATCH);
        addIf(
                failures,
                !transaction.getExternalReference().equals(payment.externalReference()),
                PaymentReconciliationFailure.EXTERNAL_REFERENCE_MISMATCH);
        addIf(
                failures,
                StringUtils.hasText(payment.orderExternalReference())
                        && !transaction.getExternalReference().equals(payment.orderExternalReference()),
                PaymentReconciliationFailure.ORDER_EXTERNAL_REFERENCE_MISMATCH);
        addIf(
                failures,
                !StringUtils.hasText(transaction.getMpPreferenceId())
                        || !transaction.getMpPreferenceId().equals(payment.preferenceId()),
                PaymentReconciliationFailure.PREFERENCE_ID_MISMATCH);
        addIf(
                failures,
                !StringUtils.hasText(transaction.getMpCollectorId())
                        || !transaction.getMpCollectorId().equals(payment.collectorId()),
                PaymentReconciliationFailure.COLLECTOR_ID_MISMATCH);
        if (StringUtils.hasText(expectedExternalReference)) {
            addIf(
                    failures,
                    !expectedExternalReference.equals(payment.externalReference()),
                    PaymentReconciliationFailure.EXTERNAL_REFERENCE_MISMATCH);
        }
        return failures.stream().distinct().toList();
    }

    private void applyConsolidatedApprovalIfEligible(Transaction transaction) {
        if (transaction.getStatus() != PaymentStatus.APPROVED) {
            return;
        }
        if (transaction.getParticipantFlagCode() == null) {
            transaction.assignParticipantFlag(participantFlagService.resolveForPhone(transaction.getPhone()));
        }
        if (transaction.getCapacityReviewStatus() != null) {
            return;
        }
        validateConsolidatedApprovedLedgerState(transaction);
        if (transaction.hasCompletedLuckyNumberBatch()) {
            return;
        }
        CapacityAllocationResult allocation =
                capacityReservationService.allocate(transaction.getExternalReference(), transaction.getQuantity());
        if (allocation == CapacityAllocationResult.INSUFFICIENT_CAPACITY) {
            transaction.markCapacityReviewPending();
        } else if (allocation == CapacityAllocationResult.ALLOCATED
                || allocation == CapacityAllocationResult.ALREADY_ALLOCATED) {
            luckyNumberService.generateFor(transaction);
        }
    }

    private void validateConsolidatedApprovedLedgerState(Transaction transaction) {
        Long currentEventId = transaction.getCurrentPaymentEventId();
        if (currentEventId == null) {
            throw new IllegalStateException("Approved transaction has no consolidated payment ledger event.");
        }
        PaymentEvent currentEvent = paymentEventRepository
                .findById(currentEventId)
                .orElseThrow(() -> new IllegalStateException("Consolidated payment ledger event was not found."));
        boolean approvedProviderState = paymentStateMachine
                .mapProviderStatus(currentEvent.getProviderStatus())
                .filter(status -> status == PaymentStatus.APPROVED)
                .isPresent();
        boolean sameTransaction = currentEvent.getProviderPayment().getTransaction() != null
                && currentEvent.getProviderPayment().getTransaction().getId().equals(transaction.getId());
        if (currentEvent.getReconciliationStatus() != PaymentEventReconciliationStatus.MATCHED
                || currentEvent.getProcessingStatus() != PaymentEventProcessingStatus.APPLIED
                || !approvedProviderState
                || !sameTransaction) {
            throw new IllegalStateException("Consolidated payment ledger state is not eligible for approval.");
        }
    }

    private static void addIf(
            List<PaymentReconciliationFailure> failures, boolean condition, PaymentReconciliationFailure failure) {
        if (condition) {
            failures.add(failure);
        }
    }

    private static String join(List<PaymentReconciliationFailure> failures) {
        return failures.stream().map(Enum::name).collect(Collectors.joining(","));
    }
}
