package com.empleord.notification;

import com.empleord.common.ApiException;
import com.empleord.domain.entity.Notification;
import com.empleord.domain.entity.User;
import com.empleord.domain.enums.NotificationType;
import com.empleord.domain.repository.NotificationRepository;
import com.empleord.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notifications;

    public NotificationService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @Transactional
    public void notify(User user, NotificationType type, String title, String body, String link) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(type);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setLink(link);
        notifications.save(notification);
    }

    public boolean alreadySent(UUID userId, NotificationType type, String link) {
        return notifications.existsByUserIdAndTypeAndLink(userId, type, link);
    }

    @Transactional(readOnly = true)
    public List<Notification> mine() {
        return notifications.findTop30ByUserIdOrderByCreatedAtDesc(SecurityUtils.requireUser().getId());
    }

    @Transactional(readOnly = true)
    public long unread() {
        return notifications.countByUserIdAndReadAtIsNull(SecurityUtils.requireUser().getId());
    }

    @Transactional
    public void markRead(UUID id) {
        Notification notification = notifications.findById(id).orElseThrow(() -> ApiException.notFound("La notificación no existe."));
        if (!notification.getUser().getId().equals(SecurityUtils.requireUser().getId())) {
            throw ApiException.forbidden("No puedes modificar esta notificación.");
        }
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
        }
    }

    @Transactional
    public void markAllRead() {
        UUID userId = SecurityUtils.requireUser().getId();
        notifications.findTop30ByUserIdOrderByCreatedAtDesc(userId).forEach(notification -> {
            if (notification.getReadAt() == null) {
                notification.setReadAt(Instant.now());
            }
        });
    }
}
