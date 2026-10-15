package com.empleord.audit;

import com.empleord.domain.entity.AuditLog;
import com.empleord.domain.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuditService {

    private final AuditLogRepository logs;

    public AuditService(AuditLogRepository logs) {
        this.logs = logs;
    }

    @Transactional
    public void record(UUID actorId, String action, String entityType, String entityId, String metadata, String ip) {
        AuditLog log = new AuditLog();
        log.setActorId(actorId);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setMetadata(trim(metadata));
        log.setIpAddress(ip);
        logs.save(log);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> latest(Pageable pageable) {
        return logs.findAllByOrderByCreatedAtDesc(pageable);
    }

    private String trim(String metadata) {
        if (metadata == null) {
            return null;
        }
        String value = metadata.replaceAll("(?i)(password|token|secret)=\\S+", "$1=***");
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
