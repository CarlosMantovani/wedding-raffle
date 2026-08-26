package com.weddingraffle.rifa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weddingraffle.rifa.entity.PaymentStatus;
import com.weddingraffle.rifa.entity.PurchaseIntent;
import com.weddingraffle.rifa.entity.PurchaseIntentAction;
import com.weddingraffle.rifa.entity.RaffleCombo;
import com.weddingraffle.rifa.entity.Transaction;
import com.weddingraffle.rifa.repository.PurchaseIntentRepository;
import com.weddingraffle.rifa.repository.TransactionRepository;
import com.weddingraffle.rifa.service.CapacityReservationService;
import com.weddingraffle.rifa.service.LuckyNumberService;
import com.weddingraffle.rifa.service.ParticipantFlagService;
import com.weddingraffle.rifa.service.PurchasePrice;
import com.weddingraffle.rifa.service.RecoveryCodeService;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PurchaseIntentServiceImplTests {

    @Mock
    private PurchaseIntentRepository purchaseIntentRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CapacityReservationService capacityReservationService;

    @Mock
    private LuckyNumberService luckyNumberService;

    @Mock
    private ParticipantFlagService participantFlagService;

    @Mock
    private RecoveryCodeService recoveryCodeService;

    @Test
    void preservesOfficialComboPriceNormalUnitPriceAndHistoricalQuantityInPurchaseIntent() {
        RaffleCombo combo = new RaffleCombo(20, new BigDecimal("880.00"), true, 3);
        when(purchaseIntentRepository.findByIdempotencyKey("combo-key")).thenReturn(Optional.empty());

        service()
                .prepareOnline(
                        "combo-key",
                        "request-hash",
                        "Guest User",
                        "11999999999",
                        null,
                        null,
                        20,
                        new PurchasePrice(new BigDecimal("50.00"), new BigDecimal("880.00"), combo));

        ArgumentCaptor<PurchaseIntent> captor = ArgumentCaptor.forClass(PurchaseIntent.class);
        verify(purchaseIntentRepository).saveAndFlush(captor.capture());
        PurchaseIntent intent = captor.getValue();
        assertThat(intent.getQuantity()).isEqualTo(20);
        assertThat(intent.getUnitPrice()).isEqualByComparingTo("50.00");
        assertThat(intent.getTotalAmount()).isEqualByComparingTo("880.00");
        assertThat(intent.getRaffleCombo()).isSameAs(combo);
        verify(transactionRepository, never()).save(any());

        combo.update(new BigDecimal("850.00"), true, 3, false, false);
        assertThat(intent.getQuantity()).isEqualTo(20);
        assertThat(intent.getTotalAmount()).isEqualByComparingTo("880.00");
    }

    @Test
    void materializesPendingOnlineTransactionWithoutAssigningAFlag() {
        PurchaseIntent intent = new PurchaseIntent(
                "checkout-key", PurchaseIntentAction.MERCADO_PAGO_CHECKOUT, "request-hash", "external-reference");
        intent.captureOnlineRequest(
                "Guest User", "11999999999", null, null, 2, new BigDecimal("10.00"), new BigDecimal("20.00"), null);
        intent.completeOnlineCheckout("preference-123", "https://checkout.example.com", "collector-123", "{}");
        when(purchaseIntentRepository.findLockedByExternalReference("external-reference"))
                .thenReturn(Optional.of(intent));
        when(recoveryCodeService.resolveForPhone("11999999999")).thenReturn("4821");
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Transaction transaction = service().materializeOnlineTransaction("external-reference");

        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(transaction.getParticipantFlagCode()).isNull();
        assertThat(transaction.getParticipantFlagName()).isNull();
        assertThat(transaction.getParticipantFlagEmoji()).isNull();
        verify(participantFlagService, never()).resolveForPhone(any());
        verify(capacityReservationService).reserve("external-reference", 2);
    }

    @Test
    void storesOnlyProvidedEmailWhenPreparingOnlineIntent() {
        when(purchaseIntentRepository.findByIdempotencyKey("checkout-key")).thenReturn(Optional.empty());

        service()
                .prepareOnline(
                        "checkout-key",
                        "request-hash",
                        "Guest User",
                        "11999999999",
                        "guest@example.com",
                        null,
                        2,
                        new PurchasePrice(new BigDecimal("10.00"), new BigDecimal("20.00"), null));

        ArgumentCaptor<PurchaseIntent> captor = ArgumentCaptor.forClass(PurchaseIntent.class);
        verify(purchaseIntentRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getParticipantEmail()).isEqualTo("guest@example.com");
    }

    @Test
    void doesNotPersistFallbackEmailWhenOnlineEmailIsMissing() {
        when(purchaseIntentRepository.findByIdempotencyKey("checkout-key")).thenReturn(Optional.empty());

        service()
                .prepareOnline(
                        "checkout-key",
                        "request-hash",
                        "Guest User",
                        "11999999999",
                        null,
                        null,
                        2,
                        new PurchasePrice(new BigDecimal("10.00"), new BigDecimal("20.00"), null));

        ArgumentCaptor<PurchaseIntent> captor = ArgumentCaptor.forClass(PurchaseIntent.class);
        verify(purchaseIntentRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getParticipantEmail()).isNull();
    }

    private PurchaseIntentServiceImpl service() {
        return new PurchaseIntentServiceImpl(
                purchaseIntentRepository,
                transactionRepository,
                capacityReservationService,
                luckyNumberService,
                participantFlagService,
                recoveryCodeService,
                new ObjectMapper());
    }
}
