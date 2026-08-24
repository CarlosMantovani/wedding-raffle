package com.weddingraffle.rifa.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.weddingraffle.rifa.config.AppProperties;
import com.weddingraffle.rifa.config.SecurityConfig;
import com.weddingraffle.rifa.dto.AdminGiftMessageResponse;
import com.weddingraffle.rifa.dto.AdminTransactionResponse;
import com.weddingraffle.rifa.dto.AdminTransactionSummaryResponse;
import com.weddingraffle.rifa.dto.CapacityReviewDecision;
import com.weddingraffle.rifa.dto.CashTransactionCreateResponse;
import com.weddingraffle.rifa.dto.PaymentStatusResponse;
import com.weddingraffle.rifa.entity.PaymentMethod;
import com.weddingraffle.rifa.service.AdminTransactionService;
import com.weddingraffle.rifa.service.LuckyNumberPdfService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminTransactionController.class)
@Import({SecurityConfig.class, AdminTransactionControllerTests.TestConfig.class})
class AdminTransactionControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminTransactionService adminTransactionService;

    @MockBean
    private LuckyNumberPdfService luckyNumberPdfService;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/transactions")).andExpect(status().isUnauthorized());
    }

    @Test
    void summaryReturnsGlobalMetricsForAdmin() throws Exception {
        when(adminTransactionService.getSummary())
                .thenReturn(new AdminTransactionSummaryResponse(12, 48, new BigDecimal("480.00")));

        mockMvc.perform(get("/transactions/summary").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTransactions").value(12))
                .andExpect(jsonPath("$.approvedLuckyNumbers").value(48))
                .andExpect(jsonPath("$.approvedRevenue").value(480.00));
    }

    @Test
    void listReturnsPagedTransactionsForAdmin() throws Exception {
        when(adminTransactionService.list(eq("guest"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(new AdminTransactionResponse(
                        "external",
                        OffsetDateTime.parse("2026-08-14T18:00:00-03:00"),
                        "Guest User",
                        "11999999999",
                        "guest@example.com",
                        PaymentMethod.MERCADO_PAGO,
                        null,
                        2,
                        new BigDecimal("20.00"),
                        PaymentStatusResponse.APROVADO,
                        List.of("00001", "00002")))));

        mockMvc.perform(get("/transactions")
                        .param("query", "guest")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].externalReference").value("external"))
                .andExpect(jsonPath("$.content[0].createdAt").value("2026-08-14T18:00:00-03:00"))
                .andExpect(jsonPath("$.content[0].name").value("Guest User"))
                .andExpect(jsonPath("$.content[0].status").value("APROVADO"))
                .andExpect(jsonPath("$.content[0].luckyNumbers[0]").value("00001"));
    }

    @Test
    void listGiftMessagesReturnsPagedMessagesForAdmin() throws Exception {
        when(adminTransactionService.listGiftMessages(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(new AdminGiftMessageResponse(
                        "external",
                        OffsetDateTime.parse("2026-08-14T18:00:00-03:00"),
                        "Guest User",
                        "Felicidades ao casal!"))));

        mockMvc.perform(get("/transactions/messages").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].externalReference").value("external"))
                .andExpect(jsonPath("$.content[0].name").value("Guest User"))
                .andExpect(jsonPath("$.content[0].giftMessage").value("Felicidades ao casal!"));
    }

    @Test
    void listUsesNewestTransactionsFirstByDefault() throws Exception {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(adminTransactionService.list(eq(null), pageableCaptor.capture())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/transactions").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());

        Sort.Order order = pageableCaptor.getValue().getSort().getOrderFor("createdAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void listAcceptsAdminSortFilter() throws Exception {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(adminTransactionService.list(eq(null), pageableCaptor.capture())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/transactions")
                        .param("sort", "totalAmount,desc")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());

        Sort.Order order = pageableCaptor.getValue().getSort().getOrderFor("totalAmount");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void createCashTransactionRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/transactions/cash")
                        .contentType("application/json")
                        .content("{\"name\":\"Guest User\",\"phone\":\"(11) 99999-9999\",\"quantity\":2}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createCashTransactionReturnsApprovedNumbersForAdmin() throws Exception {
        when(adminTransactionService.createCashTransaction(eq("cash-key-123"), any()))
                .thenReturn(new CashTransactionCreateResponse(
                        "external",
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
                        List.of("00003", "00004"),
                        List.of("00001", "00002"),
                        4));

        mockMvc.perform(post("/transactions/cash")
                        .header("Idempotency-Key", "cash-key-123")
                        .contentType("application/json")
                        .content("{\"name\":\"Guest User\",\"phone\":\"(11) 99999-9999\",\"quantity\":2}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalReference").value("external"))
                .andExpect(jsonPath("$.recoveryCode").value("4821"))
                .andExpect(jsonPath("$.paymentMethod").value("CASH"))
                .andExpect(jsonPath("$.status").value("APROVADO"))
                .andExpect(jsonPath("$.participantFlagName").value("Brasil"))
                .andExpect(jsonPath("$.participantFlagEmoji").value("🇧🇷"))
                .andExpect(jsonPath("$.luckyNumbers[0]").value("00003"))
                .andExpect(jsonPath("$.previousLuckyNumbers[0]").value("00001"))
                .andExpect(jsonPath("$.totalLuckyNumbers").value(4));
    }

    @Test
    void createCashTransactionRequiresIdempotencyKeyForAdmin() throws Exception {
        mockMvc.perform(post("/transactions/cash")
                        .contentType("application/json")
                        .content("{\"name\":\"Guest User\",\"phone\":\"(11) 99999-9999\",\"quantity\":2}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void downloadParticipantLuckyNumbersPdfRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/transactions/external/participant-lucky-numbers.pdf"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void downloadsParticipantLuckyNumbersPdfForAdmin() throws Exception {
        when(luckyNumberPdfService.generateForParticipant("external-reference-123"))
                .thenReturn("%PDF".getBytes());

        mockMvc.perform(get("/transactions/external-reference-123/participant-lucky-numbers.pdf")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string(
                                "Content-Disposition",
                                org.hamcrest.Matchers.containsString("Numeros_do_participante_external.pdf")));
    }

    @Test
    void deleteCashTransactionRequiresAuthentication() throws Exception {
        mockMvc.perform(delete("/transactions/external")).andExpect(status().isUnauthorized());
    }

    @Test
    void deleteCashTransactionReturnsNoContentForAdmin() throws Exception {
        mockMvc.perform(delete("/transactions/cash-reference")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNoContent());

        verify(adminTransactionService).deleteCashTransaction("cash-reference");
    }

    @Test
    void capacityReviewDecisionRequiresAuthentication() throws Exception {
        mockMvc.perform(put("/transactions/external/capacity-review")
                        .contentType("application/json")
                        .content("{\"decision\":\"REFUND_COMPLETED\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resolvesCapacityReviewForAdmin() throws Exception {
        mockMvc.perform(put("/transactions/external/capacity-review")
                        .contentType("application/json")
                        .content("{\"decision\":\"CONTRIBUTION_WITHOUT_NUMBERS\"}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNoContent());

        verify(adminTransactionService)
                .resolveCapacityReview("external", CapacityReviewDecision.CONTRIBUTION_WITHOUT_NUMBERS);
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        AppProperties appProperties() {
            return new AppProperties(
                    "http://localhost:5173",
                    new AppProperties.Jwt("01234567890123456789012345678901", 3600, "raffle-api-test"),
                    new AppProperties.Raffle(new BigDecimal("10.00"), "00000", "99999"),
                    new AppProperties.MercadoPago(
                            "token",
                            "http://localhost:8080/payments/webhook",
                            "",
                            "http://localhost:5173/payment-return/success",
                            "http://localhost:5173/payment-return/failure",
                            "http://localhost:5173/payment-return/pending",
                            new AppProperties.Retry(3, 500, 2)));
        }
    }
}
