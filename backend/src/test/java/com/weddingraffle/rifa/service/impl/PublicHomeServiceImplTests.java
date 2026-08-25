package com.weddingraffle.rifa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weddingraffle.rifa.repository.FlagRankingProjection;
import com.weddingraffle.rifa.repository.LuckyNumberRepository;
import com.weddingraffle.rifa.repository.RaffleConfigRepository;
import com.weddingraffle.rifa.repository.RaffleDrawRepository;
import com.weddingraffle.rifa.repository.TransactionRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class PublicHomeServiceImplTests {

    @Mock
    private RaffleConfigRepository raffleConfigRepository;

    @Mock
    private RaffleDrawRepository raffleDrawRepository;

    @Mock
    private LuckyNumberRepository luckyNumberRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Test
    void returnsTopFifteenFlagRanking() {
        var pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(transactionRepository.findApprovedFlagRanking(pageableCaptor.capture()))
                .thenReturn(List.of(
                        flagRanking("BRAZIL", "Brasil", "BR", 12), flagRanking("NICARAGUA", "Nicarágua", "NI", 8)));
        when(transactionRepository.sumApprovedQuantity()).thenReturn(30L);

        var ranking = service().getFlagRanking();

        assertThat(ranking).hasSize(2);
        assertThat(ranking.getFirst().code()).isEqualTo("BRAZIL");
        assertThat(ranking.getFirst().name()).isEqualTo("Brasil");
        assertThat(ranking.getFirst().emoji()).isEqualTo("BR");
        assertThat(ranking.getFirst().position()).isEqualTo(1);
        assertThat(ranking.getFirst().progressPercent()).isEqualByComparingTo("40.00");
        assertThat(ranking.get(1).position()).isEqualTo(2);
        assertThat(ranking.get(1).progressPercent()).isEqualByComparingTo("26.67");
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(15);
        verify(transactionRepository).findApprovedFlagRanking(pageableCaptor.getValue());
        verify(transactionRepository).sumApprovedQuantity();
    }

    private PublicHomeServiceImpl service() {
        return new PublicHomeServiceImpl(
                raffleConfigRepository, raffleDrawRepository, luckyNumberRepository, transactionRepository);
    }

    private static FlagRankingProjection flagRanking(String code, String name, String emoji, long totalNumbers) {
        return new FlagRankingProjection() {
            @Override
            public String getCode() {
                return code;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getEmoji() {
                return emoji;
            }

            @Override
            public long getTotalNumbers() {
                return totalNumbers;
            }
        };
    }
}
