package com.empleord.domain.entity;

import com.empleord.domain.AuditableEntity;
import com.empleord.domain.enums.ContractType;
import com.empleord.domain.enums.ExperienceLevel;
import com.empleord.domain.enums.JobStatus;
import com.empleord.domain.enums.RemoteScope;
import com.empleord.domain.enums.SalaryPeriod;
import com.empleord.domain.enums.WorkMode;
import com.empleord.domain.enums.WorkSchedule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "jobs")
@Getter
@Setter
@NoArgsConstructor
public class Job extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(nullable = false, length = 220)
    private String slug;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(columnDefinition = "text")
    private String requirements;

    @Column(columnDefinition = "text")
    private String responsibilities;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private JobCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "contract_type", nullable = false, length = 32)
    private ContractType contractType;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_mode", nullable = false, length = 32)
    private WorkMode workMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_schedule", nullable = false, length = 32)
    private WorkSchedule workSchedule;

    @Enumerated(EnumType.STRING)
    @Column(name = "experience_level", nullable = false, length = 32)
    private ExperienceLevel experienceLevel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "province_id")
    private Province province;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "municipality_id")
    private Municipality municipality;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "country_code", nullable = false)
    private Country country;

    @Column(name = "location_label", length = 160)
    private String locationLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "remote_scope", nullable = false, length = 32)
    private RemoteScope remoteScope;

    @Column(name = "geographic_restriction", length = 255)
    private String geographicRestriction;

    @Column(name = "salary_min", precision = 12, scale = 2)
    private BigDecimal salaryMin;

    @Column(name = "salary_max", precision = 12, scale = 2)
    private BigDecimal salaryMax;

    @Column(name = "salary_currency", nullable = false, length = 3)
    private String salaryCurrency = "DOP";

    @Enumerated(EnumType.STRING)
    @Column(name = "salary_period", length = 20)
    private SalaryPeriod salaryPeriod;

    @Column(name = "salary_negotiable", nullable = false)
    private boolean salaryNegotiable;

    @Column(name = "accepts_no_experience", nullable = false)
    private boolean acceptsNoExperience;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private JobStatus status;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @ManyToMany
    @JoinTable(
            name = "job_skills",
            joinColumns = @JoinColumn(name = "job_id"),
            inverseJoinColumns = @JoinColumn(name = "skill_id"))
    private Set<Skill> skills = new HashSet<>();

    public boolean isOpen(Instant now) {
        return status == JobStatus.PUBLISHED
                && deletedAt == null
                && (expiresAt == null || expiresAt.isAfter(now));
    }
}
