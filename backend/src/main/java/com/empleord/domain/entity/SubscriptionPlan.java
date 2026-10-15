package com.empleord.domain.entity;

import com.empleord.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "subscription_plans")
@Getter
@Setter
@NoArgsConstructor
public class SubscriptionPlan extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "price_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "billing_period", nullable = false, length = 20)
    private String billingPeriod;

    @Column(name = "max_active_jobs", nullable = false)
    private int maxActiveJobs;

    @Column(name = "featured_credits", nullable = false)
    private int featuredCredits;

    @Column(name = "candidate_filters", nullable = false)
    private boolean candidateFilters;

    @Column(name = "advanced_stats", nullable = false)
    private boolean advancedStats;

    @Column(name = "premium_profile", nullable = false)
    private boolean premiumProfile;

    @Column(nullable = false)
    private boolean active = true;
}
