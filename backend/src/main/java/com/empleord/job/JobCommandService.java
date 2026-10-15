package com.empleord.job;

import com.empleord.audit.AuditService;
import com.empleord.billing.PlanService;
import com.empleord.catalog.CatalogService;
import com.empleord.common.ApiException;
import com.empleord.common.HtmlSanitizer;
import com.empleord.common.Text;
import com.empleord.company.CompanyAccess;
import com.empleord.config.AppProperties;
import com.empleord.domain.entity.Company;
import com.empleord.domain.entity.CompanyMember;
import com.empleord.domain.entity.Country;
import com.empleord.domain.entity.Job;
import com.empleord.domain.entity.Province;
import com.empleord.domain.entity.Skill;
import com.empleord.domain.enums.ExperienceLevel;
import com.empleord.domain.enums.JobStatus;
import com.empleord.domain.enums.WorkMode;
import com.empleord.domain.repository.FeaturedJobRepository;
import com.empleord.domain.repository.JobApplicationRepository;
import com.empleord.domain.repository.JobRepository;
import com.empleord.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
public class JobCommandService {

    private static final EnumSet<JobStatus> COMPANY_STATUSES = EnumSet.of(JobStatus.DRAFT, JobStatus.PUBLISHED, JobStatus.PAUSED, JobStatus.CLOSED);

    private final JobRepository jobs;
    private final JobApplicationRepository applications;
    private final FeaturedJobRepository featuredJobs;
    private final CompanyAccess access;
    private final CatalogService catalog;
    private final PlanService plans;
    private final AppProperties properties;
    private final AuditService audit;

    public JobCommandService(JobRepository jobs, JobApplicationRepository applications, FeaturedJobRepository featuredJobs, CompanyAccess access,
                             CatalogService catalog, PlanService plans, AppProperties properties, AuditService audit) {
        this.jobs = jobs;
        this.applications = applications;
        this.featuredJobs = featuredJobs;
        this.access = access;
        this.catalog = catalog;
        this.plans = plans;
        this.properties = properties;
        this.audit = audit;
    }

    @Transactional
    public JobDtos.ManagedJob create(JobDtos.JobRequest request, String ip) {
        CompanyMember member = access.requireMember();
        Job job = new Job();
        job.setCompany(member.getCompany());
        job.setCreatedBy(member.getUser());
        job.setSlug(uniqueSlug(request.title()));
        job.setViewCount(0);
        apply(job, request, true);
        jobs.save(job);
        audit.record(member.getUser().getId(), "JOB_CREATE", "Job", job.getId().toString(), job.getStatus().name(), ip);
        return managed(job);
    }

    @Transactional
    public JobDtos.ManagedJob update(UUID id, JobDtos.JobRequest request, String ip) {
        Job job = requireOwn(id);
        apply(job, request, false);
        audit.record(SecurityUtils.requireUser().getId(), "JOB_UPDATE", "Job", job.getId().toString(), job.getStatus().name(), ip);
        return managed(job);
    }

    @Transactional
    public JobDtos.ManagedJob changeStatus(UUID id, JobStatus status, String ip) {
        if (!COMPANY_STATUSES.contains(status) || status == JobStatus.DRAFT) {
            throw ApiException.badRequest("El estado solicitado no está permitido.");
        }
        Job job = requireOwn(id);
        if (job.getStatus() == JobStatus.REJECTED) {
            throw ApiException.forbidden("Esta oferta fue rechazada por moderación.");
        }
        if (status == JobStatus.PUBLISHED) {
            plans.assertCanPublish(job.getCompany(), job.getStatus() == JobStatus.PUBLISHED);
            if (job.getPublishedAt() == null) {
                job.setPublishedAt(Instant.now());
            }
            if (job.getExpiresAt() == null || !job.getExpiresAt().isAfter(Instant.now())) {
                job.setExpiresAt(Instant.now().plus(properties.getJobDefaultDays(), ChronoUnit.DAYS));
            }
        }
        job.setStatus(status);
        audit.record(SecurityUtils.requireUser().getId(), "JOB_STATUS", "Job", job.getId().toString(), status.name(), ip);
        return managed(job);
    }

    @Transactional(readOnly = true)
    public JobDtos.JobForm get(UUID id) {
        Job job = requireOwn(id);
        return new JobDtos.JobForm(
                job.getId(), job.getTitle(), job.getDescription(), job.getRequirements(), job.getResponsibilities(),
                job.getCategory().getId(), job.getContractType().name(), job.getWorkMode().name(), job.getWorkSchedule().name(),
                job.getExperienceLevel().name(), job.getProvince() == null ? null : job.getProvince().getId(),
                job.getMunicipality() == null ? null : job.getMunicipality().getId(), job.getCountry().getCode(),
                job.getLocationLabel(), job.getRemoteScope().name(), job.getGeographicRestriction(), job.getSalaryMin(),
                job.getSalaryMax(), job.getSalaryCurrency(), job.getSalaryPeriod() == null ? null : job.getSalaryPeriod().name(),
                job.isSalaryNegotiable(), job.isAcceptsNoExperience(), job.getExpiresAt(),
                job.getSkills().stream().map(Skill::getId).toList(), job.getStatus().name());
    }

    @Transactional(readOnly = true)
    public List<JobDtos.ManagedJob> mine() {
        Company company = access.requireMember().getCompany();
        return jobs.findByCompanyIdAndDeletedAtIsNullOrderByCreatedAtDesc(company.getId()).stream().map(this::managed).toList();
    }

    private Job requireOwn(UUID id) {
        Job job = jobs.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> ApiException.notFound("La oferta no existe."));
        access.requireJob(job);
        return job;
    }

    private void apply(Job job, JobDtos.JobRequest request, boolean creating) {
        Country country = catalog.country(request.countryCode());
        Province province = catalog.province(request.provinceId());
        if ((request.workMode() == WorkMode.ONSITE || request.workMode() == WorkMode.HYBRID)
                && "DO".equals(country.getCode()) && province == null) {
            throw ApiException.badRequest("Selecciona la provincia para una oferta presencial o híbrida en República Dominicana.");
        }
        if (request.salaryMin() != null && request.salaryMax() != null && request.salaryMin().compareTo(request.salaryMax()) > 0) {
            throw ApiException.badRequest("El salario mínimo no puede ser mayor que el máximo.");
        }
        if (negative(request.salaryMin()) || negative(request.salaryMax())) {
            throw ApiException.badRequest("El salario no puede ser negativo.");
        }
        job.setTitle(Text.required(request.title(), "El cargo"));
        job.setDescription(HtmlSanitizer.required(request.description(), "La descripción"));
        job.setRequirements(HtmlSanitizer.clean(request.requirements()));
        job.setResponsibilities(HtmlSanitizer.clean(request.responsibilities()));
        job.setCategory(catalog.category(request.categoryId()));
        job.setContractType(request.contractType());
        job.setWorkMode(request.workMode());
        job.setWorkSchedule(request.workSchedule());
        job.setExperienceLevel(request.experienceLevel());
        job.setProvince(province);
        job.setMunicipality(catalog.municipality(request.municipalityId(), province));
        job.setCountry(country);
        job.setLocationLabel(Text.clean(request.locationLabel()));
        job.setRemoteScope(request.remoteScope());
        job.setGeographicRestriction(Text.clean(request.geographicRestriction()));
        job.setSalaryMin(request.salaryMin());
        job.setSalaryMax(request.salaryMax());
        job.setSalaryCurrency(request.salaryCurrency() == null || request.salaryCurrency().isBlank() ? "DOP" : request.salaryCurrency().trim().toUpperCase());
        job.setSalaryPeriod(request.salaryPeriod());
        job.setSalaryNegotiable(request.salaryNegotiable());
        job.setAcceptsNoExperience(request.acceptsNoExperience() || request.experienceLevel() == ExperienceLevel.NONE);
        job.setExpiresAt(request.expiresAt());
        List<Skill> skills = catalog.skills(request.skillIds());
        job.getSkills().clear();
        job.getSkills().addAll(skills);
        boolean publish = request.publish() || (!creating && job.getStatus() == JobStatus.PUBLISHED && request.publish());
        if (creating) {
            if (request.publish()) {
                plans.assertCanPublish(job.getCompany(), false);
                job.setStatus(JobStatus.PUBLISHED);
                job.setPublishedAt(Instant.now());
                if (job.getExpiresAt() == null) {
                    job.setExpiresAt(Instant.now().plus(properties.getJobDefaultDays(), ChronoUnit.DAYS));
                }
            } else {
                job.setStatus(JobStatus.DRAFT);
            }
        } else if (request.publish() && job.getStatus() != JobStatus.PUBLISHED) {
            plans.assertCanPublish(job.getCompany(), false);
            job.setStatus(JobStatus.PUBLISHED);
            if (job.getPublishedAt() == null) {
                job.setPublishedAt(Instant.now());
            }
            if (job.getExpiresAt() == null) {
                job.setExpiresAt(Instant.now().plus(properties.getJobDefaultDays(), ChronoUnit.DAYS));
            }
        }
        if (job.getExpiresAt() != null && job.getPublishedAt() != null && !job.getExpiresAt().isAfter(job.getPublishedAt())) {
            throw ApiException.badRequest("La fecha de vencimiento debe ser posterior a la publicación.");
        }
        if (publish) {
            // El estado ya quedó aplicado arriba.
        }
    }

    private JobDtos.ManagedJob managed(Job job) {
        Instant now = Instant.now();
        return new JobDtos.ManagedJob(job.getId(), job.getSlug(), job.getTitle(), job.getStatus().name(), job.getPublishedAt(), job.getExpiresAt(),
                job.getViewCount(), applications.findByJobIdOrderByCreatedAtDesc(job.getId()).size(), featuredJobs.countActive(job.getId(), now) > 0);
    }

    private String uniqueSlug(String title) {
        String base = Text.slug(title);
        String slug = base;
        int suffix = 2;
        while (jobs.existsBySlug(slug)) {
            slug = base + "-" + suffix++;
        }
        return slug;
    }

    private boolean negative(BigDecimal value) {
        return value != null && value.signum() < 0;
    }
}
