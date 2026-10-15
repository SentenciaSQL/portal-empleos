package com.empleord.admin;

import com.empleord.audit.AuditService;
import com.empleord.billing.PlanService;
import com.empleord.catalog.CatalogDtos;
import com.empleord.catalog.CatalogService;
import com.empleord.common.ApiException;
import com.empleord.common.PageResponse;
import com.empleord.domain.entity.Company;
import com.empleord.domain.entity.CompanyVerification;
import com.empleord.domain.entity.FeaturedJob;
import com.empleord.domain.entity.Job;
import com.empleord.domain.entity.Report;
import com.empleord.domain.entity.SubscriptionPlan;
import com.empleord.domain.entity.User;
import com.empleord.domain.enums.CompanyStatus;
import com.empleord.domain.enums.JobStatus;
import com.empleord.domain.enums.ReportStatus;
import com.empleord.domain.enums.UserStatus;
import com.empleord.domain.enums.VerificationStatus;
import com.empleord.domain.repository.CompanyRepository;
import com.empleord.domain.repository.CompanyVerificationRepository;
import com.empleord.domain.repository.FeaturedJobRepository;
import com.empleord.domain.repository.JobApplicationRepository;
import com.empleord.domain.repository.JobRepository;
import com.empleord.domain.repository.ReportRepository;
import com.empleord.domain.repository.SubscriptionPlanRepository;
import com.empleord.domain.repository.UserRepository;
import com.empleord.security.SecurityUtils;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AdminService {

    private final UserRepository users;
    private final CompanyRepository companies;
    private final CompanyVerificationRepository verifications;
    private final JobRepository jobs;
    private final JobApplicationRepository applications;
    private final ReportRepository reports;
    private final SubscriptionPlanRepository plans;
    private final FeaturedJobRepository featuredJobs;
    private final PlanService planService;
    private final CatalogService catalog;
    private final AuditService audit;

    public AdminService(UserRepository users, CompanyRepository companies, CompanyVerificationRepository verifications, JobRepository jobs,
                        JobApplicationRepository applications, ReportRepository reports, SubscriptionPlanRepository plans, FeaturedJobRepository featuredJobs,
                        PlanService planService, CatalogService catalog, AuditService audit) {
        this.users = users;
        this.companies = companies;
        this.verifications = verifications;
        this.jobs = jobs;
        this.applications = applications;
        this.reports = reports;
        this.plans = plans;
        this.featuredJobs = featuredJobs;
        this.planService = planService;
        this.catalog = catalog;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public AdminStats stats() {
        List<MonthPoint> months = new ArrayList<>();
        YearMonth current = YearMonth.now(ZoneOffset.UTC);
        for (int i = 5; i >= 0; i--) {
            YearMonth month = current.minusMonths(i);
            Instant from = month.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            Instant to = month.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            months.add(new MonthPoint(month.toString(), users.countCreatedBetween(from, to)));
        }
        return new AdminStats(
                users.countByDeletedAtIsNull(),
                users.countActiveByRole("CANDIDATE"),
                users.countActiveByRole("EMPLOYER"),
                companies.countByDeletedAtIsNull(),
                companies.countByDeletedAtIsNullAndVerificationStatus(VerificationStatus.VERIFIED),
                jobs.countByStatusAndDeletedAtIsNull(JobStatus.PUBLISHED),
                applications.countAll(),
                reports.countByStatus(ReportStatus.OPEN),
                months);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserItem> users(String q, int page) {
        String like = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase() + "%";
        var result = users.search(like, PageRequest.of(Math.max(page, 0), 20));
        return PageResponse.from(result.map(user -> new UserItem(user.getId(), user.getEmail(), user.fullName(), user.getRole().getName(), user.getStatus().name(), user.isEmailVerified(), user.getCreatedAt())));
    }

    @Transactional
    public void updateUserStatus(UUID id, UserStatus status, String ip) {
        if (status != UserStatus.ACTIVE && status != UserStatus.SUSPENDED) {
            throw ApiException.badRequest("Solo puedes activar o suspender una cuenta.");
        }
        User user = users.findActiveById(id).orElseThrow(() -> ApiException.notFound("La cuenta no existe."));
        if ("ADMIN".equals(user.getRole().getName())) {
            throw ApiException.forbidden("No puedes cambiar el estado de un administrador desde aquí.");
        }
        user.setStatus(status);
        audit.record(SecurityUtils.requireUser().getId(), "USER_STATUS", "User", id.toString(), status.name(), ip);
    }

    @Transactional(readOnly = true)
    public PageResponse<CompanyItem> companies(String q, int page) {
        String like = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase() + "%";
        var result = companies.search(like, PageRequest.of(Math.max(page, 0), 20));
        return PageResponse.from(result.map(company -> new CompanyItem(company.getId(), company.getName(), company.getSlug(), company.getVerificationStatus().name(), company.getStatus().name(), company.getCountry().getCode())));
    }

    @Transactional
    public void verify(UUID id, VerificationStatus status, String notes, String ip) {
        if (status != VerificationStatus.VERIFIED && status != VerificationStatus.REJECTED) {
            throw ApiException.badRequest("Indica si la empresa queda verificada o rechazada.");
        }
        Company company = companies.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> ApiException.notFound("La empresa no existe."));
        company.setVerificationStatus(status);
        CompanyVerification verification = new CompanyVerification();
        verification.setCompany(company);
        verification.setStatus(status);
        verification.setNotes(notes);
        verification.setReviewedBy(users.findActiveById(SecurityUtils.requireUser().getId()).orElseThrow());
        verification.setReviewedAt(Instant.now());
        verifications.save(verification);
        audit.record(SecurityUtils.requireUser().getId(), "VERIFY_COMPANY", "Company", id.toString(), status.name(), ip);
    }

    @Transactional
    public void assignPlan(UUID companyId, String planCode, String ip) {
        Company company = companies.findByIdAndDeletedAtIsNull(companyId).orElseThrow(() -> ApiException.notFound("La empresa no existe."));
        SubscriptionPlan plan = planService.change(company, planCode);
        audit.record(SecurityUtils.requireUser().getId(), "ASSIGN_PLAN", "Company", companyId.toString(), plan.getCode(), ip);
    }

    @Transactional(readOnly = true)
    public PageResponse<JobItem> jobs(String q, JobStatus status, int page) {
        String like = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase() + "%";
        var result = jobs.searchAdmin(like, status, PageRequest.of(Math.max(page, 0), 20));
        return PageResponse.from(result.map(job -> new JobItem(job.getId(), job.getTitle(), job.getSlug(), job.getCompany().getName(), job.getStatus().name(), job.getPublishedAt())));
    }

    @Transactional
    public void moderateJob(UUID id, JobStatus status, String ip) {
        if (status != JobStatus.REJECTED && status != JobStatus.CLOSED && status != JobStatus.PAUSED && status != JobStatus.PUBLISHED) {
            throw ApiException.badRequest("Estado de moderación no permitido.");
        }
        Job job = jobs.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> ApiException.notFound("La oferta no existe."));
        if (status == JobStatus.PUBLISHED) {
            planService.assertCanPublish(job.getCompany(), job.getStatus() == JobStatus.PUBLISHED);
            if (job.getPublishedAt() == null) {
                job.setPublishedAt(Instant.now());
            }
        }
        job.setStatus(status);
        audit.record(SecurityUtils.requireUser().getId(), "MODERATE_JOB", "Job", id.toString(), status.name(), ip);
    }

    @Transactional
    public void feature(UUID jobId, int days, String ip) {
        if (days < 1 || days > 30) {
            throw ApiException.badRequest("El destaque debe durar entre 1 y 30 días.");
        }
        Job job = jobs.findByIdAndDeletedAtIsNull(jobId).orElseThrow(() -> ApiException.notFound("La oferta no existe."));
        if (job.getStatus() != JobStatus.PUBLISHED) {
            throw ApiException.badRequest("Solo se puede destacar una oferta publicada.");
        }
        FeaturedJob featured = new FeaturedJob();
        featured.setJob(job);
        featured.setStartsAt(Instant.now());
        featured.setEndsAt(Instant.now().plus(days, ChronoUnit.DAYS));
        featured.setCreatedBy(users.findActiveById(SecurityUtils.requireUser().getId()).orElseThrow());
        featuredJobs.save(featured);
        audit.record(SecurityUtils.requireUser().getId(), "FEATURE_JOB", "Job", jobId.toString(), days + "d", ip);
    }

    @Transactional(readOnly = true)
    public List<CatalogDtos.PlanItem> plans() {
        return plans.findAllByOrderByPriceAmountAsc().stream().map(catalog::plan).toList();
    }

    @Transactional
    public CatalogDtos.PlanItem updatePlan(Integer id, PlanUpdate request, String ip) {
        SubscriptionPlan plan = plans.findById(id).orElseThrow(() -> ApiException.notFound("El plan no existe."));
        plan.setName(request.name().trim());
        plan.setDescription(request.description());
        plan.setPriceAmount(request.priceAmount());
        plan.setMaxActiveJobs(request.maxActiveJobs());
        plan.setFeaturedCredits(request.featuredCredits());
        plan.setCandidateFilters(request.candidateFilters());
        plan.setAdvancedStats(request.advancedStats());
        plan.setPremiumProfile(request.premiumProfile());
        plan.setActive(request.active());
        audit.record(SecurityUtils.requireUser().getId(), "UPDATE_PLAN", "SubscriptionPlan", id.toString(), plan.getCode(), ip);
        return catalog.plan(plan);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditItem> audit(int page) {
        return PageResponse.from(audit.latest(PageRequest.of(Math.max(page, 0), 30)).map(log ->
                new AuditItem(log.getId(), log.getActorId(), log.getAction(), log.getEntityType(), log.getEntityId(), log.getMetadata(), log.getCreatedAt())));
    }

    @Transactional(readOnly = true)
    public List<ReportItem> reports(ReportStatus status) {
        return reports.findByStatusOrderByCreatedAtDesc(status == null ? ReportStatus.OPEN : status).stream()
                .map(report -> new ReportItem(report.getId(), report.getTargetType(), report.getTargetId(), report.getReason(), report.getDetails(), report.getReporterEmail(), report.getStatus().name(), report.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void reviewReport(UUID id, ReportStatus status, String ip) {
        if (status != ReportStatus.REVIEWED && status != ReportStatus.DISMISSED) {
            throw ApiException.badRequest("Indica si el reporte queda revisado o descartado.");
        }
        Report report = reports.findById(id).orElseThrow(() -> ApiException.notFound("El reporte no existe."));
        report.setStatus(status);
        audit.record(SecurityUtils.requireUser().getId(), "REVIEW_REPORT", "Report", id.toString(), status.name(), ip);
    }

    public record AdminStats(long users, long candidates, long employers, long companies, long verifiedCompanies, long activeJobs, long applications, long openReports, List<MonthPoint> registrations) {
    }

    public record MonthPoint(String month, long users) {
    }

    public record UserItem(UUID id, String email, String name, String role, String status, boolean emailVerified, Instant createdAt) {
    }

    public record CompanyItem(UUID id, String name, String slug, String verificationStatus, String status, String countryCode) {
    }

    public record JobItem(UUID id, String title, String slug, String companyName, String status, Instant publishedAt) {
    }

    public record ReportItem(UUID id, String targetType, UUID targetId, String reason, String details, String reporterEmail, String status, Instant createdAt) {
    }

    public record AuditItem(UUID id, UUID actorId, String action, String entityType, String entityId, String metadata, Instant createdAt) {
    }

    public record PlanUpdate(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 1000) String description,
            @NotNull @DecimalMin("0") BigDecimal priceAmount,
            @NotNull Integer maxActiveJobs,
            @NotNull Integer featuredCredits,
            boolean candidateFilters,
            boolean advancedStats,
            boolean premiumProfile,
            boolean active
    ) {
    }

    public record StatusBody(@NotNull String status) {
    }
}
