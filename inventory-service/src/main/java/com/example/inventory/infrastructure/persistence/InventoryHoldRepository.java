package com.example.inventory.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface InventoryHoldRepository extends JpaRepository<InventoryHoldEntity, UUID> {

    @EntityGraph(attributePaths = "items")
    Optional<InventoryHoldEntity> findByReservationId(UUID reservationId);

    /**
     * Locks the hold and eagerly loads items before confirm/release mutation.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "items")
    Optional<InventoryHoldEntity> findWithLockByReservationId(UUID reservationId);
}
