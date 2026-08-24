package com.weddingraffle.rifa.repository;

import com.weddingraffle.rifa.entity.RaffleCapacity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RaffleCapacityRepository extends JpaRepository<RaffleCapacity, Short> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select capacity from RaffleCapacity capacity where capacity.id = :id")
    Optional<RaffleCapacity> findLockedById(@Param("id") short id);
}
