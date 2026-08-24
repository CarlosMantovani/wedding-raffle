package com.weddingraffle.rifa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weddingraffle.rifa.entity.ParticipantFlag;
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
    void preservesOfficialComboPriceNormalUnitPriceAndHistoricalQuantity() {
        RaffleCombo combo = new RaffleCombo(20, new BigDecimal("880.00"), true, 3);
        when(purchaseIntentRepository.findByIdempotencyKey("combo-key")).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(participantFlagService.resolveForPhone("11999999999"))
                .thenReturn(new ParticipantFlag("BRAZIL", "Brasil", "BR"));
        when(recoveryCodeService.resolveForPhone("11999999999")).thenReturn("4821");

        service()
                .prepareOnline(
                        "combo-key",
                        "request-hash",
                        "Guest User",
                        "11999999999",
                        20,
                        new PurchasePrice(new BigDecimal("50.00"), new BigDecimal("880.00"), combo));

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        org.mockito.Mockito.verify(transactionRepository).save(captor.capture());
        Transaction transaction = captor.getValue();
        assertThat(transaction.getQuantity()).isEqualTo(20);
        assertThat(transaction.getUnitPrice()).isEqualByComparingTo("50.00");
        assertThat(transaction.getTotalAmount()).isEqualByComparingTo("880.00");
        assertThat(transaction.getRaffleCombo()).isSameAs(combo);

        combo.update(new BigDecimal("850.00"), true, 3, false, false);
        assertThat(transaction.getQuantity()).isEqualTo(20);
        assertThat(transaction.getTotalAmount()).isEqualByComparingTo("880.00");
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
