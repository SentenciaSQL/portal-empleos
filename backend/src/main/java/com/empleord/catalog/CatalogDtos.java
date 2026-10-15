package com.empleord.catalog;

import java.math.BigDecimal;

public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record CountryItem(String code, String name) {
    }

    public record ProvinceItem(Integer id, String name, String code) {
    }

    public record MunicipalityItem(Integer id, String name) {
    }

    public record CategoryItem(Integer id, String name, String slug) {
    }

    public record SkillItem(Integer id, String name) {
    }

    public record PlanItem(
            Integer id,
            String code,
            String name,
            String description,
            BigDecimal priceAmount,
            String currency,
            String billingPeriod,
            int maxActiveJobs,
            int featuredCredits,
            boolean candidateFilters,
            boolean advancedStats,
            boolean premiumProfile,
            boolean active
    ) {
    }
}
