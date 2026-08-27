package com.weddingraffle.rifa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.weddingraffle.rifa.entity.RaffleConfig;
import com.weddingraffle.rifa.entity.RaffleDraw;
import com.weddingraffle.rifa.repository.RaffleConfigRepository;
import com.weddingraffle.rifa.repository.RaffleDrawRepository;
import com.weddingraffle.rifa.service.RaffleComboService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RaffleConfigServiceImplTests {

    @Mock
    private RaffleConfigRepository raffleConfigRepository;

    @Mock
    private RaffleDrawRepository raffleDrawRepository;

    @Mock
    private RaffleComboService raffleComboService;

    @Mock
    private EntityManager entityManager;

    @Test
    void keepsPurchaseOpenAfterScheduledDrawTimeUntilResultExists() {
        when(raffleDrawRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        assertThat(service().isDrawClosed()).isFalse();
    }

    @Test
    void closesPurchaseWhenRaffleResultExists() {
        when(raffleDrawRepository.findFirstByOrderByIdAsc())
                .thenReturn(Optional.of(new RaffleDraw("00042", "Winner Guest", null)));

        assertThat(service().isDrawClosed()).isTrue();
    }

    @Test
    void returnsConfiguredWeddingEventDate() {
        RaffleConfig config = new RaffleConfig(new BigDecimal("10.00"));
        OffsetDateTime weddingEventAt = OffsetDateTime.parse("2026-09-05T18:00:00-03:00");
        config.updateWeddingEventAt(weddingEventAt);
        when(raffleConfigRepository.findById(RaffleConfig.SINGLETON_ID)).thenReturn(Optional.of(config));

        assertThat(service().getWeddingEventAt()).isEqualTo(weddingEventAt);
    }

    private RaffleConfigServiceImpl service() {
        return new RaffleConfigServiceImpl(
                raffleConfigRepository, raffleDrawRepository, raffleComboService, entityManager);
    }
}
