package com.weddingraffle.rifa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.weddingraffle.rifa.entity.RaffleCombo;
import com.weddingraffle.rifa.service.RaffleComboService;
import com.weddingraffle.rifa.service.RaffleConfigService;
import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RafflePricingServiceImplTests {

    @Mock
    private RaffleConfigService raffleConfigService;

    @Mock
    private RaffleComboService raffleComboService;

    @Test
    void calculatesRegularPurchaseWithoutCombo() {
        when(raffleConfigService.getCurrentUnitPrice()).thenReturn(new BigDecimal("50.00"));

        var result = service().calculate(7, null);

        assertThat(result.unitPrice()).isEqualByComparingTo("50.00");
        assertThat(result.totalAmount()).isEqualByComparingTo("350.00");
        assertThat(result.combo()).isNull();
    }

    @ParameterizedTest
    @MethodSource("promotionalCombos")
    void calculatesConfiguredPromotionalCombo(long comboId, int quantity, String comboPrice) {
        RaffleCombo combo = new RaffleCombo(quantity, new BigDecimal(comboPrice), true, quantity);
        when(raffleConfigService.getCurrentUnitPrice()).thenReturn(new BigDecimal("50.00"));
        when(raffleComboService.getActiveCombo(comboId, quantity)).thenReturn(combo);

        var result = service().calculate(quantity, comboId);

        assertThat(result.unitPrice()).isEqualByComparingTo("50.00");
        assertThat(result.totalAmount()).isEqualByComparingTo(comboPrice);
        assertThat(result.combo()).isSameAs(combo);
    }

    @Test
    void rejectsComboThatIsNotCheaperThanRegularPurchase() {
        RaffleCombo combo = new RaffleCombo(5, new BigDecimal("250.00"), true, 1);
        when(raffleConfigService.getCurrentUnitPrice()).thenReturn(new BigDecimal("50.00"));
        when(raffleComboService.getActiveCombo(1L, 5)).thenReturn(combo);

        assertThatThrownBy(() -> service().calculate(5, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not cheaper");
    }

    private RafflePricingServiceImpl service() {
        return new RafflePricingServiceImpl(raffleConfigService, raffleComboService);
    }

    private static Stream<Arguments> promotionalCombos() {
        return Stream.of(
                Arguments.of(1L, 5, "240.00"),
                Arguments.of(2L, 10, "460.00"),
                Arguments.of(3L, 20, "880.00"),
                Arguments.of(4L, 30, "1275.00"));
    }
}
