package com.empleord.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "ad_slots")
@Getter
@Setter
@NoArgsConstructor
public class AdSlot {

    @Id
    @Column(name = "slot_key", length = 40)
    private String slotKey;

    @Column(name = "page_key", nullable = false, length = 40)
    private String pageKey;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "ad_unit_id", nullable = false, length = 20)
    private String adUnitId;

    @Column(nullable = false)
    private long impressions;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
