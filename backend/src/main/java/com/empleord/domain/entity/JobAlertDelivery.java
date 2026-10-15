package com.empleord.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "job_alert_deliveries")
@Getter
@Setter
@NoArgsConstructor
public class JobAlertDelivery {

    @EmbeddedId
    private Key id;

    @Column(name = "notified_at", nullable = false)
    private Instant notifiedAt = Instant.now();

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        @Column(name = "alert_id")
        private UUID alertId;
        @Column(name = "job_id")
        private UUID jobId;

        public Key(UUID alertId, UUID jobId) {
            this.alertId = alertId;
            this.jobId = jobId;
        }
    }
}
