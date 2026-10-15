package com.empleord.domain.entity;

import com.empleord.domain.AuditableEntity;
import com.empleord.domain.enums.AlertFrequency;
import com.empleord.domain.enums.ContractType;
import com.empleord.domain.enums.WorkMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "job_alerts")
@Getter
@Setter
@NoArgsConstructor
public class JobAlert extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    private CandidateProfile candidate;

    @Column(length = 160)
    private String keyword;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "province_id")
    private Province province;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private JobCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "municipality_id")
    private Municipality municipality;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_mode", length = 32)
    private WorkMode workMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "contract_type", length = 32)
    private ContractType contractType;

    @Column(name = "salary_min", precision = 12, scale = 2)
    private BigDecimal salaryMin;

    @Column(name = "salary_max", precision = 12, scale = 2)
    private BigDecimal salaryMax;

    @Column(name = "salary_currency", nullable = false, length = 3)
    private String salaryCurrency = "DOP";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertFrequency frequency = AlertFrequency.DAILY;

    @Column(name = "last_notified_at")
    private Instant lastNotifiedAt;

    @Column(nullable = false)
    private boolean active = true;
}
