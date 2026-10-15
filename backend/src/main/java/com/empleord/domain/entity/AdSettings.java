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
@Table(name = "ad_settings")
@Getter
@Setter
@NoArgsConstructor
public class AdSettings {

    @Id
    private Short id;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "publisher_id", nullable = false, length = 32)
    private String publisherId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
