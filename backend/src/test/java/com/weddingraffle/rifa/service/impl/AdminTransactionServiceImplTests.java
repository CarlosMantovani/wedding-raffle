package com.weddingraffle.rifa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.weddingraffle.rifa.repository.AdminTransactionSummaryProjection;
import com.weddingraffle.rifa.repository.LuckyNumberRepository;
import com.weddingraffle.rifa.repository.TransactionRepository;
import com.weddingraffle.rifa.service.CapacityReservationService;
import com.weddingraffle.rifa.service.PurchaseIntentService;
import com.weddingraffle.rifa.service.RaffleConfigService;
import com.weddingraffle.rifa.util.PurchaseRequestHasher;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class AdminTransactionServiceImplTests {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private LuckyNumberRepository luckyNumberRepository;

    @Mock
    private RaffleConfigService raffleConfigService;

    @Mock
    private CapacityReservationService capacityReservationService;

    @Mock
    private PurchaseIntentService purchaseIntentService;

    private final PurchaseRequestHasher purchaseRequestHasher = new PurchaseRequestHasher();

    @Test
    void listsTransactionsWithLuckyNumbers() {
        AdminTransactionServiceImpl service = service();
        Transaction transaction =
                new Transaction("guest@example.com", 2, new BigDecimal("20.00"), PaymentStatus.APPROVED, "external");
        LuckyNumber first = new LuckyNumber("00001", "guest@example.com", transaction);
        LuckyNumber second = new LuckyNumber("00002", "guest@example.com", transaction);
        PageRequest pageable = PageRequest.of(0, 20);
        when(transactionRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(transaction), pageable, 1));
        when(luckyNumberRepository.findByTransactionInOrderByNumberAsc(List.of(transaction)))
                .thenReturn(List.of(first, second));

        var response = service.list(null, pageable);

        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getContent().getFirst().externalReference()).isEqualTo("external");
        assertThat(response.getContent().getFirst().luckyNumbers()).containsExactly("00001", "00002");
    }

    @Test
    void returnsGlobalTransactionSummary() {
        AdminTransactionServiceImpl service = service();
        AdminTransactionSummaryProjection summary = new AdminTransactionSummaryProjection() {
            @Override
            public long getTotalTransactions() {
                return 12;
            }

            @Override
            public long getApprovedLuckyNumbers() {
                return 48;
            }

            @Override
            public BigDecimal getApprovedRevenue() {
                return new BigDecimal("480.00");
            }
        };
        when(transactionRepository.getAdminSummary()).thenReturn(summary);

        assertThat(service.getSummary())
                .isEqualTo(new AdminTransactionSummaryResponse(12, 48, new BigDecimal("480.00")));
    }

    @Test
    void filtersTransactionsByNameOrPhoneWhenProvided() {
        AdminTransactionServiceImpl service = service();
        Transaction transaction =
                new Transaction("guest@example.com", 1, new BigDecimal("10.00"), PaymentStatus.PENDING, "external");
        PageRequest pageable = PageRequest.of(0, 20);
        when(transactionRepository.findByNameOrPhone("(11) 99999-9999", "11999999999", pageable))
                .thenReturn(new PageImpl<>(List.of(transaction), pageable, 1));
        when(luckyNumberRepository.findByTransactionInOrderByNumberAsc(List.of(transaction)))
                .thenReturn(List.of());

        var response = service.list("(11) 99999-9999", pageable);

        assertThat(response.getContent().getFirst().email()).isEqualTo("guest@example.com");
        assertThat(response.getContent().getFirst().luckyNumbers()).isEmpty();
    }

    @Test
    void createsApprovedCashTransactionWithLuckyNumbers() {
        AdminTransactionServiceImpl service = service();
        String idempotencyKey = "cash-key-123";
        String requestHash = purchaseRequestHasher.cash("Guest User", "11999999999", "guest@example.com", 2);
        CashTransactionCreateResponse expectedResponse = new CashTransactionCreateResponse(
                "cash-reference",
                "4821",
                "Guest User",
                "11999999999",
                "guest@example.com",
                PaymentMethod.CASH,
                PaymentStatusResponse.APROVADO,
                2,
                new BigDecimal("20.00"),
                "Brasil",
                "🇧🇷",
                List.of("00001"),
                List.of("00090", "00091"),
                3);
        when(purchaseIntentService.findCash(idempotencyKey, requestHash)).thenReturn(Optional.empty());
        when(raffleConfigService.getCurrentUnitPrice()).thenReturn(new BigDecimal("10.00"));
        when(purchaseIntentService.createCash(
                        idempotencyKey,
                        requestHash,
                        "Guest User",
                        "11999999999",
                        "guest@example.com",
                        2,
                        new BigDecimal("10.00"),
                        new BigDecimal("20.00")))
                .thenReturn(expectedResponse);

        var response = service.createCashTransaction(
                idempotencyKey,
                new CashTransactionCreateRequest("Guest User", "(11) 99999-9999", "GUEST@example.com", 2));

        assertThat(response.name()).isEqualTo("Guest User");
        assertThat(response.phone()).isEqualTo("11999999999");
        assertThat(response.email()).isEqualTo("guest@example.com");
        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(response.recoveryCode()).isEqualTo("4821");
        assertThat(response.status()).isEqualTo(PaymentStatusResponse.APROVADO);
        assertThat(response.totalAmount()).isEqualByComparingTo("20.00");
        assertThat(response.luckyNumbers()).containsExactly("00001");
        assertThat(response.previousLuckyNumbers()).containsExactly("00090", "00091");
        assertThat(response.totalLuckyNumbers()).isEqualTo(3);
        verify(purchaseIntentService)
                .createCash(
                        idempotencyKey,
                        requestHash,
                        "Guest User",
                        "11999999999",
                        "guest@example.com",
                        2,
                        new BigDecimal("10.00"),
                        new BigDecimal("20.00"));
    }

    @Test
    void createCashTransactionRejectsPurchaseAfterDrawIsClosed() {
        AdminTransactionServiceImpl service = service();
        String requestHash = purchaseRequestHasher.cash("Guest User", "11999999999", "guest@example.com", 2);
        when(purchaseIntentService.findCash("cash-key-123", requestHash)).thenReturn(Optional.empty());
        when(raffleConfigService.isDrawClosed()).thenReturn(true);

        assertThatThrownBy(() -> service.createCashTransaction(
                        "cash-key-123",
                        new CashTransactionCreateRequest("Guest User", "(11) 99999-9999", "GUEST@example.com", 2)))
                .isInstanceOf(InvalidRaffleStateException.class)
                .hasMessage("Draw is closed. No more numbers can be purchased.");
    }

    @Test
    void cashRetryReturnsPersistedResponseWithoutCreatingNumbersAgain() {
        AdminTransactionServiceImpl service = service();
        String requestHash = purchaseRequestHasher.cash("Guest User", "11999999999", null, 2);
        CashTransactionCreateResponse persistedResponse = new CashTransactionCreateResponse(
                "cash-reference",
                "4821",
                "Guest User",
                "11999999999",
                null,
                PaymentMethod.CASH,
                PaymentStatusResponse.APROVADO,
                2,
                new BigDecimal("20.00"),
                "Brasil",
                "🇧🇷",
                List.of("00001", "00002"),
                List.of(),
                2);
        when(purchaseIntentService.findCash("cash-key-123", requestHash)).thenReturn(Optional.of(persistedResponse));

        CashTransactionCreateResponse response = service.createCashTransaction(
                "cash-key-123", new CashTransactionCreateRequest("Guest User", "(11) 99999-9999", null, 2));

        assertThat(response).isEqualTo(persistedResponse);
        verify(raffleConfigService, never()).isDrawClosed();
        verify(purchaseIntentService, never())
                .createCash(
                        "cash-key-123",
                        requestHash,
                        "Guest User",
                        "11999999999",
                        null,
                        2,
                        new BigDecimal("10.00"),
                        new BigDecimal("20.00"));
    }

    @Test
    void deletesCashTransactionWithLuckyNumbers() {
        AdminTransactionServiceImpl service = service();
        Transaction transaction = new Transaction(
                "Guest User",
                "11999999999",
                null,
                1,
                new BigDecimal("10.00"),
                PaymentStatus.APPROVED,
                PaymentMethod.CASH,
                "cash-reference");
        when(transactionRepository.findByExternalReference("cash-reference")).thenReturn(Optional.of(transaction));

        service.deleteCashTransaction("cash-reference");

        verify(luckyNumberRepository).deleteByTransaction(transaction);
        verify(capacityReservationService).releaseAllocation("cash-reference");
        verify(transactionRepository).delete(transaction);
    }

    @Test
    void deleteCashTransactionRejectsMercadoPagoTransaction() {
        AdminTransactionServiceImpl service = service();
        Transaction transaction = new Transaction(
                "Guest User",
                "11999999999",
                "guest@example.com",
                1,
                new BigDecimal("10.00"),
                PaymentStatus.APPROVED,
                PaymentMethod.MERCADO_PAGO,
                "mp-reference");
        when(transactionRepository.findByExternalReference("mp-reference")).thenReturn(Optional.of(transaction));

        assertThatThrownBy(() -> service.deleteCashTransaction("mp-reference"))
                .isInstanceOf(InvalidTransactionStateException.class);
    }

    @Test
    void recordsManualRefundForPendingCapacityReview() {
        AdminTransactionServiceImpl service = service();
        Transaction transaction = reviewedTransaction();
        when(transactionRepository.findByExternalReference("mp-reference")).thenReturn(Optional.of(transaction));

        service.resolveCapacityReview("mp-reference", CapacityReviewDecision.REFUND_COMPLETED);

        assertThat(transaction.getCapacityReviewStatus()).isEqualTo(CapacityReviewStatus.REFUND_COMPLETED);
        verify(transactionRepository).save(transaction);
    }

    @Test
    void recordsContributionWithoutNumbersForPendingCapacityReview() {
        AdminTransactionServiceImpl service = service();
        Transaction transaction = reviewedTransaction();
        when(transactionRepository.findByExternalReference("mp-reference")).thenReturn(Optional.of(transaction));

        service.resolveCapacityReview("mp-reference", CapacityReviewDecision.CONTRIBUTION_WITHOUT_NUMBERS);

        assertThat(transaction.getCapacityReviewStatus()).isEqualTo(CapacityReviewStatus.CONTRIBUTION_WITHOUT_NUMBERS);
        verify(transactionRepository).save(transaction);
    }

    private static Transaction reviewedTransaction() {
        Transaction transaction = new Transaction(
                "Guest User",
                "11999999999",
                null,
                1,
                new BigDecimal("10.00"),
                PaymentStatus.APPROVED,
                PaymentMethod.MERCADO_PAGO,
                "mp-reference");
        transaction.markCapacityReviewPending();
        return transaction;
    }

    private AdminTransactionServiceImpl service() {
        return new AdminTransactionServiceImpl(
                raffleConfigService,
                transactionRepository,
                luckyNumberRepository,
                capacityReservationService,
                purchaseIntentService,
                purchaseRequestHasher);
    }
}
