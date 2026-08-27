package com.weddingraffle.rifa.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.weddingraffle.rifa.config.AppProperties;
import com.weddingraffle.rifa.config.SecurityConfig;
import com.weddingraffle.rifa.dto.RaffleCandidateResponse;
import com.weddingraffle.rifa.dto.RaffleDrawResponse;
import com.weddingraffle.rifa.service.RaffleService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RaffleController.class)
@Import({SecurityConfig.class, RaffleControllerTests.TestConfig.class})
class RaffleControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RaffleService raffleService;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    void drawRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/raffle/draw")).andExpect(status().isUnauthorized());
    }

    @Test
    void drawReturnsWinnerForAdmin() throws Exception {
        when(raffleService.draw())
                .thenReturn(new RaffleDrawResponse("00001", "Guest User", OffsetDateTime.now(), "Brasil", "🇧🇷"));

        mockMvc.perform(post("/raffle/draw").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MASTER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.winningNumber").value("00001"))
                .andExpect(jsonPath("$.winnerName").value("Guest User"))
                .andExpect(jsonPath("$.participantFlagName").value("Brasil"))
                .andExpect(jsonPath("$.participantFlagEmoji").value("🇧🇷"));
    }

    @Test
    void resultReturnsWinnerForAdmin() throws Exception {
        when(raffleService.getResult()).thenReturn(new RaffleDrawResponse("00001", "Guest User", OffsetDateTime.now()));

        mockMvc.perform(get("/raffle/result").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MASTER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.winningNumber").value("00001"));
    }

    @Test
    void eligibleNumbersReturnsNumbersForAdmin() throws Exception {
        when(raffleService.listEligibleNumbers())
                .thenReturn(List.of(
                        new RaffleCandidateResponse("00001", "Brasil", "🇧🇷"),
                        new RaffleCandidateResponse("00002", "Canada", "🇨🇦")));

        mockMvc.perform(get("/raffle/eligible-numbers")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MASTER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].luckyNumber").value("00001"))
                .andExpect(jsonPath("$[0].participantFlagName").value("Brasil"))
                .andExpect(jsonPath("$[0].participantFlagEmoji").value("🇧🇷"))
                .andExpect(jsonPath("$[1].luckyNumber").value("00002"));
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
