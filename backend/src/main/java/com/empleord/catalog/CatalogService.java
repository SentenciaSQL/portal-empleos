package com.empleord.catalog;

import com.empleord.common.ApiException;
import com.empleord.domain.entity.Country;
import com.empleord.domain.entity.JobCategory;
import com.empleord.domain.entity.Municipality;
import com.empleord.domain.entity.Province;
import com.empleord.domain.entity.Skill;
import com.empleord.domain.entity.SubscriptionPlan;
import com.empleord.domain.repository.CountryRepository;
import com.empleord.domain.repository.JobCategoryRepository;
import com.empleord.domain.repository.MunicipalityRepository;
import com.empleord.domain.repository.ProvinceRepository;
import com.empleord.domain.repository.SkillRepository;
import com.empleord.domain.repository.SubscriptionPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CatalogService {

    private final CountryRepository countries;
    private final ProvinceRepository provinces;
    private final MunicipalityRepository municipalities;
    private final JobCategoryRepository categories;
    private final SkillRepository skills;
    private final SubscriptionPlanRepository plans;

    public CatalogService(CountryRepository countries, ProvinceRepository provinces, MunicipalityRepository municipalities,
                          JobCategoryRepository categories, SkillRepository skills, SubscriptionPlanRepository plans) {
        this.countries = countries;
        this.provinces = provinces;
        this.municipalities = municipalities;
        this.categories = categories;
        this.skills = skills;
        this.plans = plans;
    }

    @Transactional(readOnly = true)
    public List<CatalogDtos.CountryItem> countries() {
        return countries.findAllByOrderByNameAsc().stream().map(country -> new CatalogDtos.CountryItem(country.getCode(), country.getName())).toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogDtos.ProvinceItem> provinces() {
        return provinces.findAllByOrderByNameAsc().stream().map(province -> new CatalogDtos.ProvinceItem(province.getId(), province.getName(), province.getCode())).toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogDtos.MunicipalityItem> municipalities(Integer provinceId) {
        if (!provinces.existsById(provinceId)) {
            throw ApiException.notFound("La provincia no existe.");
        }
        return municipalities.findByProvinceIdOrderByNameAsc(provinceId).stream()
                .map(item -> new CatalogDtos.MunicipalityItem(item.getId(), item.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogDtos.CategoryItem> categories() {
        return categories.findAllByOrderByNameAsc().stream().map(category -> new CatalogDtos.CategoryItem(category.getId(), category.getName(), category.getSlug())).toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogDtos.SkillItem> skills() {
        return skills.findAllByOrderByNameAsc().stream().map(skill -> new CatalogDtos.SkillItem(skill.getId(), skill.getName())).toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogDtos.PlanItem> plans() {
        return plans.findAllByOrderByPriceAmountAsc().stream().filter(SubscriptionPlan::isActive).map(this::plan).toList();
    }

    public CatalogDtos.PlanItem plan(SubscriptionPlan plan) {
        return new CatalogDtos.PlanItem(plan.getId(), plan.getCode(), plan.getName(), plan.getDescription(), plan.getPriceAmount(),
                plan.getCurrency(), plan.getBillingPeriod(), plan.getMaxActiveJobs(), plan.getFeaturedCredits(),
                plan.isCandidateFilters(), plan.isAdvancedStats(), plan.isPremiumProfile(), plan.isActive());
    }

    public Country country(String code) {
        String normalized = code == null || code.isBlank() ? "DO" : code.trim().toUpperCase();
        return countries.findById(normalized).orElseThrow(() -> ApiException.badRequest("El país no está disponible."));
    }

    public Province province(Integer id) {
        if (id == null) {
            return null;
        }
        return provinces.findById(id).orElseThrow(() -> ApiException.badRequest("La provincia no existe."));
    }

    public Municipality municipality(Integer id, Province province) {
        if (id == null) {
            return null;
        }
        Municipality municipality = municipalities.findById(id).orElseThrow(() -> ApiException.badRequest("El municipio no existe."));
        if (province == null || !municipality.getProvince().getId().equals(province.getId())) {
            throw ApiException.badRequest("El municipio no pertenece a la provincia seleccionada.");
        }
        return municipality;
    }

    public JobCategory category(Integer id) {
        return categories.findById(id).orElseThrow(() -> ApiException.badRequest("La categoría no existe."));
    }

    public List<Skill> skills(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Skill> found = skills.findByIdIn(ids);
        if (found.size() != ids.stream().distinct().count()) {
            throw ApiException.badRequest("Hay habilidades que no existen en el catálogo.");
        }
        return found;
    }
}
