package com.weddingraffle.rifa.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.weddingraffle.rifa.config.AppProperties;
import com.weddingraffle.rifa.config.SecurityConfig;
import com.weddingraffle.rifa.dto.PaymentStatusResponse;
import com.weddingraffle.rifa.dto.TransactionCreateRequest;
import com.weddingraffle.rifa.dto.TransactionCreateResponse;
import com.weddingraffle.rifa.dto.TransactionQuoteResponse;
import com.weddingraffle.rifa.dto.TransactionStatusResponse;
import com.weddingraffle.rifa.exception.IdempotencyConflictException;
import com.weddingraffle.rifa.service.LuckyNumberPdfService;
import com.weddingraffle.rifa.service.TransactionService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TransactionController.class)
@Import({SecurityConfig.class, TransactionControllerTests.TestConfig.class})
class TransactionControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransactionService transactionService;

    @MockBean
    private LuckyNumberPdfService luckyNumberPdfService;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    void quoteReturnsTotalWithoutAuthentication() throws Exception {
        when(transactionService.quote(any()))
                .thenReturn(new TransactionQuoteResponse(
                        "Guest User", "11999999999", 2, new BigDecimal("10.00"), new BigDecimal("20.00")));

        mockMvc.perform(post("/transactions/quote")
                        .contentType("application/json")
                        .content("{\"name\":\"Guest User\",\"phone\":\"(11) 99999-9999\",\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Guest User"))
                .andExpect(jsonPath("$.phone").value("11999999999"))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.unitPrice").value(10.00))
                .andExpect(jsonPath("$.totalAmount").value(20.00));
    }

    @Test
    void quoteReturnsValidationErrorForInvalidRequest() throws Exception {
        mockMvc.perform(post("/transactions/quote")
                        .contentType("application/json")
                        .content("{\"name\":\"\",\"phone\":\"\",\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void quoteAllowsNgrokPreflightRequest() throws Exception {
        mockMvc.perform(options("/transactions/quote")
                        .header("Origin", "https://3278-45-225-145-57.ngrok-free.app")
                        .header("Access-Control-Request-Method", "POST")
                        .header(
                                "Access-Control-Request-Headers",
                                "content-type,idempotency-key,ngrok-skip-browser-warning"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://3278-45-225-145-57.ngrok-free.app"))
                .andExpect(
                        header().string("Access-Control-Allow-Methods", org.hamcrest.Matchers.containsString("POST")))
                .andExpect(header().string(
                                "Access-Control-Allow-Headers", org.hamcrest.Matchers.containsString("content-type")))
                .andExpect(header().string(
                                "Access-Control-Allow-Headers",
                                org.hamcrest.Matchers.containsString("idempotency-key")))
                .andExpect(header().string(
                                "Access-Control-Allow-Headers",
                                org.hamcrest.Matchers.containsString("ngrok-skip-browser-warning")));
    }

    @Test
    void createReturnsCheckoutWithoutAuthentication() throws Exception {
        when(transactionService.create(eq("checkout-key-123"), any()))
                .thenReturn(new TransactionCreateResponse(
                        "external-reference-123", "4821", "preference-123", "https://checkout.example.com"));

        mockMvc.perform(post("/transactions")
                        .header("Idempotency-Key", "checkout-key-123")
                        .contentType("application/json")
                        .content("{\"name\":\"Guest User\",\"phone\":\"(11) 99999-9999\",\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalReference").value("external-reference-123"))
                .andExpect(jsonPath("$.recoveryCode").value("4821"))
                .andExpect(jsonPath("$.preferenceId").value("preference-123"))
                .andExpect(jsonPath("$.checkoutUrl").value("https://checkout.example.com"));
    }

    @Test
    void createIgnoresClientFinancialFieldsAndAcceptsOnlyQuantityAndComboReference() throws Exception {
        when(transactionService.create(eq("checkout-key-123"), any()))
                .thenReturn(new TransactionCreateResponse(
                        "external-reference-123", "4821", "preference-123", "https://checkout.example.com"));

        mockMvc.perform(
                        post("/transactions")
                                .header("Idempotency-Key", "checkout-key-123")
                                .contentType("application/json")
                                .content(
                                        """
                                {
                                  "name": "Guest User",
                                  "phone": "(11) 99999-9999",
                                  "quantity": 20,
                                  "comboId": 3,
                                  "totalAmount": 10,
                                  "price": 10
                                }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<TransactionCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(TransactionCreateRequest.class);
        verify(transactionService).create(eq("checkout-key-123"), requestCaptor.capture());
        assertThat(requestCaptor.getValue().quantity()).isEqualTo(20);
        assertThat(requestCaptor.getValue().comboId()).isEqualTo(3L);
    }

    @Test
    void createRequiresIdempotencyKey() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType("application/json")
                        .content("{\"name\":\"Guest User\",\"phone\":\"(11) 99999-9999\",\"quantity\":2}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRejectsGiftMessageAboveLimit() throws Exception {
        String longMessage = "a".repeat(281);

        mockMvc.perform(post("/transactions")
                        .header("Idempotency-Key", "checkout-key-123")
                        .contentType("application/json")
                        .content("{\"name\":\"Guest User\",\"phone\":\"(11) 99999-9999\",\"giftMessage\":\""
                                + longMessage
                                + "\",\"quantity\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createRejectsSameIdempotencyKeyWithDifferentPayload() throws Exception {
        when(transactionService.create(eq("checkout-key-123"), any()))
                .thenThrow(new IdempotencyConflictException(
                        "The idempotency key was already used with a different purchase request."));

        mockMvc.perform(post("/transactions")
                        .header("Idempotency-Key", "checkout-key-123")
                        .contentType("application/json")
                        .content("{\"name\":\"Guest User\",\"phone\":\"(11) 99999-9999\",\"quantity\":3}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void downloadsLuckyNumbersPdfWithoutAuthentication() throws Exception {
        when(luckyNumberPdfService.generate("external-reference-123")).thenReturn("%PDF".getBytes());

        mockMvc.perform(get("/transactions/external-reference-123/lucky-numbers.pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string(
                                "Content-Disposition",
                                org.hamcrest.Matchers.containsString("Numeros_da_sorte_external.pdf")));
    }

    @Test
    void statusReturnsPortuguesePaymentStatusWithoutAuthentication() throws Exception {
        when(transactionService.getStatus("external-reference-123", "123"))
                .thenReturn(new TransactionStatusResponse(
                        "external-reference-123",
                        "4821",
                        PaymentStatusResponse.APROVADO,
                        2,
                        new BigDecimal("20.00"),
                        "Brasil",
                        "🇧🇷",
                        List.of("00001", "00002"),
                        List.of("00099"),
                        3));

        mockMvc.perform(get("/transactions/external-reference-123/status").param("paymentId", "123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalReference").value("external-reference-123"))
                .andExpect(jsonPath("$.recoveryCode").value("4821"))
                .andExpect(jsonPath("$.status").value("APROVADO"))
                .andExpect(jsonPath("$.previousLuckyNumbers[0]").value("00099"))
                .andExpect(jsonPath("$.totalLuckyNumbers").value(3));
    }

    @Test
    void recoversLuckyNumbersWithoutAuthentication() throws Exception {
        when(transactionService.recover(any()))
                .thenReturn(new TransactionStatusResponse(
                        "external-reference-123",
                        "4821",
                        PaymentStatusResponse.APROVADO,
                        1,
                        new BigDecimal("10.00"),
                        "Brasil",
                        "🇧🇷",
                        List.of("00042"),
                        List.of(),
                        1));

        mockMvc.perform(post("/transactions/recovery")
                        .contentType("application/json")
                        .content("{\"phone\":\"(11) 99999-9999\",\"recoveryCode\":\"4821\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalReference").value("external-reference-123"))
                .andExpect(jsonPath("$.recoveryCode").value("4821"))
                .andExpect(jsonPath("$.luckyNumbers[0]").value("00042"));
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
