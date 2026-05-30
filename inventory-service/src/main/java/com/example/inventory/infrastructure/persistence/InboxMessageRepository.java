package com.example.inventory.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InboxMessageRepository extends JpaRepository<InboxMessageEntity, UUID> {
}
