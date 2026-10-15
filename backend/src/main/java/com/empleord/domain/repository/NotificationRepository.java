package com.empleord.domain.repository;

import com.empleord.domain.entity.Notification;
import com.empleord.domain.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findTop30ByUserIdOrderByCreatedAtDesc(UUID userId);
    long countByUserIdAndReadAtIsNull(UUID userId);
    boolean existsByUserIdAndTypeAndLink(UUID userId, NotificationType type, String link);
}
