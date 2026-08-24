package com.weddingraffle.rifa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weddingraffle.rifa.entity.PurchaseIntent;
import com.weddingraffle.rifa.entity.RaffleCombo;
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
