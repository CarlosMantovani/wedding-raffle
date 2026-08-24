package com.weddingraffle.rifa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weddingraffle.rifa.dto.RaffleComboUpdateRequest;
import com.weddingraffle.rifa.entity.RaffleCombo;
import com.weddingraffle.rifa.exception.ResourceNotFoundException;
import com.weddingraffle.rifa.repository.RaffleComboRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RaffleComboServiceImplTests {

    @Mock
    private RaffleComboRepository repository;

    @Test
    void rejectsUnknownCombo() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getActiveCombo(99L, 20)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectsInactiveCombo() {
        when(repository.findById(1L)).thenReturn(Optional.of(new RaffleCombo(5, new BigDecimal("240.00"), false, 1)));

        assertThatThrownBy(() -> service().getActiveCombo(1L, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void rejectsQuantityThatDoesNotMatchCombo() {
        when(repository.findById(1L)).thenReturn(Optional.of(new RaffleCombo(5, new BigDecimal("240.00"), true, 1)));

        assertThatThrownBy(() -> service().getActiveCombo(1L, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void updateChangesOnlyPriceStatusAndOrder() {
        RaffleCombo combo = new RaffleCombo(20, new BigDecimal("880.00"), true, 3);
        when(repository.findById(3L)).thenReturn(Optional.of(combo));
        when(repository.saveAndFlush(combo)).thenReturn(combo);

        var response = service()
                .updateCombo(
                        3L,
                        new RaffleComboUpdateRequest(new BigDecimal("870.00"), false, 7, true, false),
                        new BigDecimal("50.00"));

        assertThat(response.quantity()).isEqualTo(20);
        assertThat(response.price()).isEqualByComparingTo("870.00");
        assertThat(response.active()).isFalse();
        assertThat(response.displayOrder()).isEqualTo(7);
        assertThat(response.highlightMostChosen()).isTrue();
        assertThat(response.highlightBestValue()).isFalse();
    }

    @Test
    void rejectsComboPriceEqualToRegularPrice() {
        RaffleCombo combo = new RaffleCombo(5, new BigDecimal("240.00"), true, 1);
        when(repository.findById(1L)).thenReturn(Optional.of(combo));

        assertThatThrownBy(() -> service()
                        .updateCombo(
                                1L,
                                new RaffleComboUpdateRequest(new BigDecimal("250.00"), true, 1, false, false),
                                new BigDecimal("50.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lower than");
        verify(repository, never()).saveAndFlush(combo);
    }

    @Test
    void blocksUnitPriceThatInvalidatesActiveCombosAndListsQuantities() {
        when(repository.findByActiveTrueOrderByDisplayOrderAscIdAsc())
                .thenReturn(List.of(
                        new RaffleCombo(5, new BigDecimal("240.00"), true, 1),
                        new RaffleCombo(10, new BigDecimal("460.00"), true, 2),
                        new RaffleCombo(20, new BigDecimal("880.00"), true, 3)));

        assertThatThrownBy(() -> service().validateUnitPrice(new BigDecimal("45.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5, 10");
    }

    private RaffleComboServiceImpl service() {
        return new RaffleComboServiceImpl(repository);
    }
}
