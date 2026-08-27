package com.weddingraffle.rifa.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.weddingraffle.rifa.config.AppProperties;
import com.weddingraffle.rifa.config.SecurityConfig;
import com.weddingraffle.rifa.dto.RaffleConfigResponse;
import com.weddingraffle.rifa.service.RaffleConfigService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RaffleConfigController.class)
@Import({SecurityConfig.class, RaffleConfigControllerTests.TestConfig.class})
class RaffleConfigControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RaffleConfigService raffleConfigService;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    void getConfigRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/admin/raffle-config")).andExpect(status().isUnauthorized());
    }

    @Test
    void getConfigReturnsCurrentUnitPriceForAdmin() throws Exception {
        when(raffleConfigService.getConfig())
                .thenReturn(new RaffleConfigResponse(
                        new BigDecimal("10.00"), null, OffsetDateTime.parse("2026-08-14T18:00:00-03:00")));

        mockMvc.perform(get("/admin/raffle-config").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MASTER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitPrice").value(10.00))
                .andExpect(jsonPath("$.updatedAt").value("2026-08-14T18:00:00-03:00"));
    }

    @Test
    void updateUnitPriceRequiresAuthentication() throws Exception {
        mockMvc.perform(put("/admin/raffle-config/unit-price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unitPrice\":15.00}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateUnitPriceRejectsInvalidValue() throws Exception {
        mockMvc.perform(put("/admin/raffle-config/unit-price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unitPrice\":0}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MASTER"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateUnitPriceReturnsUpdatedConfigForAdmin() throws Exception {
        when(raffleConfigService.updateUnitPrice(any()))
                .thenReturn(new RaffleConfigResponse(
                        new BigDecimal("15.00"), null, OffsetDateTime.parse("2026-08-14T18:00:00-03:00")));

        mockMvc.perform(put("/admin/raffle-config/unit-price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unitPrice\":15.00}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MASTER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitPrice").value(15.00));
    }

    @Test
    void updateScheduledDrawAtReturnsUpdatedConfigForAdmin() throws Exception {
        when(raffleConfigService.updateScheduledDrawAt(any()))
                .thenReturn(new RaffleConfigResponse(
                        new BigDecimal("10.00"),
                        OffsetDateTime.parse("2026-09-05T20:00:00-03:00"),
                        OffsetDateTime.parse("2026-08-14T18:00:00-03:00")));

        mockMvc.perform(put("/admin/raffle-config/scheduled-at")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledDrawAt\":\"2026-09-05T20:00:00-03:00\"}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MASTER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledDrawAt").value("2026-09-05T20:00:00-03:00"));
    }

    @Test
    void updateWeddingEventAtReturnsUpdatedConfigForAdmin() throws Exception {
        when(raffleConfigService.updateWeddingEventAt(any()))
                .thenReturn(new RaffleConfigResponse(
                        new BigDecimal("10.00"),
                        null,
                        OffsetDateTime.parse("2026-09-05T18:00:00-03:00"),
                        OffsetDateTime.parse("2026-08-14T18:00:00-03:00")));

        mockMvc.perform(put("/admin/raffle-config/wedding-event-at")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weddingEventAt\":\"2026-09-05T18:00:00-03:00\"}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MASTER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weddingEventAt").value("2026-09-05T18:00:00-03:00"));
    }

    @Test
    void updateWeddingEventAtRejectsMissingValue() throws Exception {
        mockMvc.perform(put("/admin/raffle-config/wedding-event-at")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MASTER"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateWeddingEventAtRejectsCashierRole() throws Exception {
        mockMvc.perform(put("/admin/raffle-config/wedding-event-at")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weddingEventAt\":\"2026-09-05T18:00:00-03:00\"}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CASHIER"))))
                .andExpect(status().isForbidden());
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
