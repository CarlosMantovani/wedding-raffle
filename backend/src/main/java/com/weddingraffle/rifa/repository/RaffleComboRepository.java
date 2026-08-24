package com.weddingraffle.rifa.repository;

import com.weddingraffle.rifa.entity.RaffleCombo;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RaffleComboRepository extends JpaRepository<RaffleCombo, Long> {

    List<RaffleCombo> findAllByOrderByDisplayOrderAscIdAsc();

    List<RaffleCombo> findByActiveTrueOrderByDisplayOrderAscIdAsc();
}
