package com.empleord.web;

import com.empleord.admin.AdminService;
import com.empleord.application.ApplicationDtos;
import com.empleord.application.ApplicationService;
import com.empleord.candidate.AccountService;
import com.empleord.candidate.CandidateDtos;
import com.empleord.candidate.CandidateProfileService;
import com.empleord.candidate.ResumeService;
import com.empleord.candidate.SavedJobService;
import com.empleord.common.ApiResponse;
import com.empleord.common.PageResponse;
import com.empleord.company.CompanyDtos;
import com.empleord.company.CompanyService;
import com.empleord.domain.entity.Notification;
import com.empleord.domain.enums.ApplicationStatus;
import com.empleord.domain.enums.ContractType;
import com.empleord.domain.enums.ExperienceLevel;
import com.empleord.domain.enums.JobStatus;
import com.empleord.domain.enums.ReportStatus;
import com.empleord.domain.enums.UserStatus;
import com.empleord.domain.enums.VerificationStatus;
import com.empleord.domain.enums.WorkMode;
import com.empleord.domain.enums.WorkSchedule;
import com.empleord.job.JobCommandService;
import com.empleord.job.JobDtos;
import com.empleord.job.JobQueryService;
import com.empleord.moderation.ReportService;
import com.empleord.notification.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
class HealthController {
    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}

@RestController
@RequestMapping("/api/files")
class FileController {
    private final ResumeService resumes;

    FileController(ResumeService resumes) {
        this.resumes = resumes;
    }

    @GetMapping("/resumes")
    public ResponseEntity<Resource> resume(@RequestParam String token) {
        return resumes.stream(token);
    }
}

@RestController
@RequestMapping("/api/media")
class MediaController {
    private final CompanyService companies;

    MediaController(CompanyService companies) {
        this.companies = companies;
    }

    @GetMapping("/logos/{companyId}")
    public ResponseEntity<Resource> logo(@PathVariable UUID companyId) {
        return companies.logo(companyId);
    }
}

@RestController
@RequestMapping("/api/notifications")
class NotificationController {
    private final NotificationService notifications;

    NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public ApiResponse<NotificationPage> list() {
        List<NotificationItem> items = notifications.mine().stream()
                .map(notification -> new NotificationItem(notification.getId(), notification.getType().name(), notification.getTitle(), notification.getBody(), notification.getLink(), notification.getReadAt(), notification.getCreatedAt()))
                .toList();
        return ApiResponse.ok(new NotificationPage(items, notifications.unread()));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> read(@PathVariable UUID id) {
        notifications.markRead(id);
        return ApiResponse.message("Notificación leída.");
    }

    @PostMapping("/read-all")
    public ApiResponse<Void> readAll() {
        notifications.markAllRead();
        return ApiResponse.message("Notificaciones leídas.");
    }

    record NotificationItem(UUID id, String type, String title, String body, String link, Instant readAt, Instant createdAt) {
    }

    record NotificationPage(List<NotificationItem> items, long unread) {
    }
}

@RestController
@RequestMapping("/api/jobs")
class JobPublicController {
    private final JobQueryService queries;
    private final ApplicationService applications;
    private final ReportService reports;

    JobPublicController(JobQueryService queries, ApplicationService applications, ReportService reports) {
        this.queries = queries;
        this.applications = applications;
        this.reports = reports;
    }

    @GetMapping
    public ApiResponse<PageResponse<JobDtos.JobCard>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer provinceId,
            @RequestParam(required = false) Integer municipalityId,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) ContractType contractType,
            @RequestParam(required = false) WorkMode workMode,
            @RequestParam(required = false) WorkSchedule workSchedule,
            @RequestParam(required = false) ExperienceLevel experienceLevel,
            @RequestParam(required = false) BigDecimal salaryMin,
            @RequestParam(required = false) BigDecimal salaryMax,
            @RequestParam(required = false) Integer publishedWithinDays,
            @RequestParam(required = false) Boolean acceptsNoExperience,
            @RequestParam(defaultValue = "recent") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ApiResponse.ok(queries.search(q, provinceId, municipalityId, categoryId, contractType, workMode, workSchedule, experienceLevel, salaryMin, salaryMax, publishedWithinDays, acceptsNoExperience, sort, page, size));
    }

    @GetMapping("/{slug}")
    public ApiResponse<JobDtos.JobDetail> detail(@PathVariable String slug) {
        return ApiResponse.ok(queries.detail(slug));
    }

    @PostMapping("/{id}/applications")
    public ApiResponse<ApplicationDtos.CandidateApplication> apply(@PathVariable UUID id, @RequestBody(required = false) ApplicationDtos.ApplyRequest request, HttpServletRequest http) {
        return ApiResponse.ok(applications.apply(id, request, http.getRemoteAddr()));
    }

    @PostMapping("/{id}/reports")
    public ApiResponse<Void> report(@PathVariable UUID id, @Valid @RequestBody ReportService.ReportRequest request) {
        reports.reportJob(id, request);
        return ApiResponse.message("Recibimos el reporte. Lo revisaremos.");
    }
}

@RestController
@RequestMapping("/api/companies")
class CompanyPublicController {
    private final CompanyService companies;

    CompanyPublicController(CompanyService companies) {
        this.companies = companies;
    }

    @GetMapping("/{slug}")
    public ApiResponse<CompanyDtos.PublicCompany> company(@PathVariable String slug) {
        return ApiResponse.ok(companies.publicProfile(slug));
    }
}

@RestController
@RequestMapping("/api/candidate")
class CandidateController {
    private final CandidateProfileService profiles;
    private final ResumeService resumes;
    private final SavedJobService savedJobs;
    private final ApplicationService applications;
    private final AccountService accounts;

    CandidateController(CandidateProfileService profiles, ResumeService resumes, SavedJobService savedJobs, ApplicationService applications, AccountService accounts) {
        this.profiles = profiles;
        this.resumes = resumes;
        this.savedJobs = savedJobs;
        this.applications = applications;
        this.accounts = accounts;
    }

    @GetMapping("/profile")
    public ApiResponse<CandidateDtos.ProfileResponse> profile() {
        return ApiResponse.ok(profiles.get());
    }

    @PutMapping("/profile")
    public ApiResponse<CandidateDtos.ProfileResponse> updateProfile(@Valid @RequestBody CandidateDtos.ProfileRequest request) {
        return ApiResponse.ok(profiles.update(request));
    }

    @PostMapping("/experiences")
    public ApiResponse<CandidateDtos.ExperienceItem> addExperience(@Valid @RequestBody CandidateDtos.ExperienceRequest request) {
        return ApiResponse.ok(profiles.addExperience(request));
    }

    @PutMapping("/experiences/{id}")
    public ApiResponse<CandidateDtos.ExperienceItem> updateExperience(@PathVariable UUID id, @Valid @RequestBody CandidateDtos.ExperienceRequest request) {
        return ApiResponse.ok(profiles.updateExperience(id, request));
    }

    @DeleteMapping("/experiences/{id}")
    public ApiResponse<Void> deleteExperience(@PathVariable UUID id) {
        profiles.deleteExperience(id);
        return ApiResponse.message("Experiencia eliminada.");
    }

    @PostMapping("/education")
    public ApiResponse<CandidateDtos.EducationItem> addEducation(@Valid @RequestBody CandidateDtos.EducationRequest request) {
        return ApiResponse.ok(profiles.addEducation(request));
    }

    @PutMapping("/education/{id}")
    public ApiResponse<CandidateDtos.EducationItem> updateEducation(@PathVariable UUID id, @Valid @RequestBody CandidateDtos.EducationRequest request) {
        return ApiResponse.ok(profiles.updateEducation(id, request));
    }

    @DeleteMapping("/education/{id}")
    public ApiResponse<Void> deleteEducation(@PathVariable UUID id) {
        profiles.deleteEducation(id);
        return ApiResponse.message("Formación eliminada.");
    }

    @PostMapping("/certifications")
    public ApiResponse<CandidateDtos.CertificationItem> addCertification(@Valid @RequestBody CandidateDtos.CertificationRequest request) {
        return ApiResponse.ok(profiles.addCertification(request));
    }

    @PutMapping("/certifications/{id}")
    public ApiResponse<CandidateDtos.CertificationItem> updateCertification(@PathVariable UUID id, @Valid @RequestBody CandidateDtos.CertificationRequest request) {
        return ApiResponse.ok(profiles.updateCertification(id, request));
    }

    @DeleteMapping("/certifications/{id}")
    public ApiResponse<Void> deleteCertification(@PathVariable UUID id) {
        profiles.deleteCertification(id);
        return ApiResponse.message("Certificación eliminada.");
    }

    @PostMapping("/languages")
    public ApiResponse<CandidateDtos.LanguageItem> addLanguage(@Valid @RequestBody CandidateDtos.LanguageRequest request) {
        return ApiResponse.ok(profiles.addLanguage(request));
    }

    @DeleteMapping("/languages/{id}")
    public ApiResponse<Void> deleteLanguage(@PathVariable UUID id) {
        profiles.deleteLanguage(id);
        return ApiResponse.message("Idioma eliminado.");
    }

    @PutMapping("/skills")
    public ApiResponse<List<CandidateDtos.CatalogSkill>> skills(@Valid @RequestBody CandidateDtos.SkillSelection request) {
        return ApiResponse.ok(profiles.replaceSkills(request.skillIds()));
    }

    @PostMapping("/resumes")
    public ApiResponse<CandidateDtos.ResumeItem> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(resumes.upload(file));
    }

    @DeleteMapping("/resumes/{id}")
    public ApiResponse<Void> deleteResume(@PathVariable UUID id) {
        resumes.delete(id);
        return ApiResponse.message("Currículum eliminado.");
    }

    @PostMapping("/resumes/{id}/primary")
    public ApiResponse<Void> primary(@PathVariable UUID id) {
        resumes.makePrimary(id);
        return ApiResponse.message("Currículum principal actualizado.");
    }

    @GetMapping("/resumes/{id}/link")
    public ApiResponse<Map<String, String>> link(@PathVariable UUID id) {
        return ApiResponse.ok(Map.of("url", resumes.signedLinkForCurrentUser(id)));
    }

    @GetMapping("/applications")
    public ApiResponse<List<ApplicationDtos.CandidateApplication>> applications() {
        return ApiResponse.ok(applications.mine());
    }

    @PostMapping("/applications/{id}/withdraw")
    public ApiResponse<Void> withdraw(@PathVariable UUID id, HttpServletRequest http) {
        applications.withdraw(id, http.getRemoteAddr());
        return ApiResponse.message("Postulación retirada.");
    }

    @GetMapping("/saved-jobs")
    public ApiResponse<List<JobDtos.JobCard>> saved() {
        return ApiResponse.ok(savedJobs.list());
    }

    @PostMapping("/saved-jobs")
    public ApiResponse<Void> save(@Valid @RequestBody CandidateDtos.SaveJobRequest request) {
        savedJobs.save(request.jobId());
        return ApiResponse.message("Oferta guardada.");
    }

    @DeleteMapping("/saved-jobs/{jobId}")
    public ApiResponse<Void> unsave(@PathVariable UUID jobId) {
        savedJobs.remove(jobId);
        return ApiResponse.message("Oferta quitada de guardadas.");
    }

    @GetMapping("/stats")
    public ApiResponse<CandidateDtos.CandidateStats> stats() {
        return ApiResponse.ok(profiles.stats());
    }

    @DeleteMapping("/account")
    public ApiResponse<Void> deleteAccount(HttpServletRequest http) {
        accounts.deleteCandidate(http.getRemoteAddr());
        return ApiResponse.message("Tu cuenta y tus datos personales fueron eliminados.");
    }
}

@RestController
@RequestMapping("/api/company")
class CompanyManageController {
    private final CompanyService companies;
    private final JobCommandService jobs;
    private final ApplicationService applications;

    CompanyManageController(CompanyService companies, JobCommandService jobs, ApplicationService applications) {
        this.companies = companies;
        this.jobs = jobs;
        this.applications = applications;
    }

    @GetMapping
    public ApiResponse<CompanyDtos.ManagedCompany> mine() {
        return ApiResponse.ok(companies.mine());
    }

    @PutMapping
    public ApiResponse<CompanyDtos.ManagedCompany> update(@Valid @RequestBody CompanyDtos.ProfileRequest request) {
        return ApiResponse.ok(companies.update(request));
    }

    @PostMapping("/logo")
    public ApiResponse<Void> logo(@RequestParam("file") MultipartFile file) {
        companies.uploadLogo(file);
        return ApiResponse.message("Logotipo actualizado.");
    }

    @GetMapping("/members")
    public ApiResponse<List<CompanyDtos.MemberItem>> members() {
        return ApiResponse.ok(companies.members());
    }

    @PostMapping("/members")
    public ApiResponse<CompanyDtos.MemberItem> addMember(@Valid @RequestBody CompanyDtos.MemberRequest request) {
        return ApiResponse.ok(companies.addMember(request));
    }

    @GetMapping("/stats")
    public ApiResponse<CompanyDtos.CompanyStats> stats() {
        return ApiResponse.ok(companies.stats());
    }

    @GetMapping("/jobs")
    public ApiResponse<List<JobDtos.ManagedJob>> jobs() {
        return ApiResponse.ok(jobs.mine());
    }

    @GetMapping("/jobs/{id}")
    public ApiResponse<JobDtos.JobForm> job(@PathVariable UUID id) {
        return ApiResponse.ok(jobs.get(id));
    }

    @PostMapping("/jobs")
    public ApiResponse<JobDtos.ManagedJob> create(@Valid @RequestBody JobDtos.JobRequest request, HttpServletRequest http) {
        return ApiResponse.ok(jobs.create(request, http.getRemoteAddr()));
    }

    @PutMapping("/jobs/{id}")
    public ApiResponse<JobDtos.ManagedJob> update(@PathVariable UUID id, @Valid @RequestBody JobDtos.JobRequest request, HttpServletRequest http) {
        return ApiResponse.ok(jobs.update(id, request, http.getRemoteAddr()));
    }

    @PatchMapping("/jobs/{id}/status")
    public ApiResponse<JobDtos.ManagedJob> status(@PathVariable UUID id, @Valid @RequestBody JobDtos.StatusRequest request, HttpServletRequest http) {
        return ApiResponse.ok(jobs.changeStatus(id, request.status(), http.getRemoteAddr()));
    }

    @GetMapping("/jobs/{id}/applications")
    public ApiResponse<List<ApplicationDtos.CompanyApplication>> applications(
            @PathVariable UUID id,
            @RequestParam(required = false) Integer minYears,
            @RequestParam(required = false) Integer skillId,
            @RequestParam(required = false) String education,
            @RequestParam(required = false) Boolean favoriteOnly) {
        return ApiResponse.ok(applications.forJob(id, minYears, skillId, education, favoriteOnly));
    }

    @PatchMapping("/applications/{id}")
    public ApiResponse<ApplicationDtos.CompanyApplication> updateApplication(@PathVariable UUID id, @Valid @RequestBody ApplicationDtos.UpdateRequest request, HttpServletRequest http) {
        return ApiResponse.ok(applications.update(id, request, http.getRemoteAddr()));
    }

    @GetMapping("/applications/{id}/resume-link")
    public ApiResponse<Map<String, String>> resumeLink(@PathVariable UUID id) {
        return ApiResponse.ok(Map.of("url", applications.resumeLink(id)));
    }
}

@RestController
@RequestMapping("/api/admin")
class AdminController {
    private final AdminService admin;

    AdminController(AdminService admin) {
        this.admin = admin;
    }

    @GetMapping("/stats")
    public ApiResponse<AdminService.AdminStats> stats() {
        return ApiResponse.ok(admin.stats());
    }

    @GetMapping("/users")
    public ApiResponse<PageResponse<AdminService.UserItem>> users(@RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page) {
        return ApiResponse.ok(admin.users(q, page));
    }

    @PatchMapping("/users/{id}/status")
    public ApiResponse<Void> userStatus(@PathVariable UUID id, @RequestBody Map<String, String> body, HttpServletRequest http) {
        admin.updateUserStatus(id, UserStatus.valueOf(body.get("status")), http.getRemoteAddr());
        return ApiResponse.message("Estado de la cuenta actualizado.");
    }

    @GetMapping("/companies")
    public ApiResponse<PageResponse<AdminService.CompanyItem>> companies(@RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page) {
        return ApiResponse.ok(admin.companies(q, page));
    }

    @PatchMapping("/companies/{id}/verification")
    public ApiResponse<Void> verify(@PathVariable UUID id, @RequestBody Map<String, String> body, HttpServletRequest http) {
        admin.verify(id, VerificationStatus.valueOf(body.get("status")), body.get("notes"), http.getRemoteAddr());
        return ApiResponse.message("Verificación actualizada.");
    }

    @PatchMapping("/companies/{id}/plan")
    public ApiResponse<Void> plan(@PathVariable UUID id, @RequestBody Map<String, String> body, HttpServletRequest http) {
        admin.assignPlan(id, body.get("planCode"), http.getRemoteAddr());
        return ApiResponse.message("Plan actualizado.");
    }

    @GetMapping("/jobs")
    public ApiResponse<PageResponse<AdminService.JobItem>> jobs(@RequestParam(required = false) String q, @RequestParam(required = false) JobStatus status, @RequestParam(defaultValue = "0") int page) {
        return ApiResponse.ok(admin.jobs(q, status, page));
    }

    @PatchMapping("/jobs/{id}/moderation")
    public ApiResponse<Void> moderate(@PathVariable UUID id, @RequestBody Map<String, String> body, HttpServletRequest http) {
        admin.moderateJob(id, JobStatus.valueOf(body.get("status")), http.getRemoteAddr());
        return ApiResponse.message("Oferta actualizada.");
    }

    @PostMapping("/jobs/{id}/feature")
    public ApiResponse<Void> feature(@PathVariable UUID id, @RequestBody Map<String, Integer> body, HttpServletRequest http) {
        admin.feature(id, body.getOrDefault("days", 7), http.getRemoteAddr());
        return ApiResponse.message("Oferta destacada.");
    }

    @GetMapping("/plans")
    public ApiResponse<List<com.empleord.catalog.CatalogDtos.PlanItem>> plans() {
        return ApiResponse.ok(admin.plans());
    }

    @PutMapping("/plans/{id}")
    public ApiResponse<com.empleord.catalog.CatalogDtos.PlanItem> updatePlan(@PathVariable Integer id, @Valid @RequestBody AdminService.PlanUpdate request, HttpServletRequest http) {
        return ApiResponse.ok(admin.updatePlan(id, request, http.getRemoteAddr()));
    }

    @GetMapping("/reports")
    public ApiResponse<List<AdminService.ReportItem>> reports(@RequestParam(required = false) ReportStatus status) {
        return ApiResponse.ok(admin.reports(status));
    }

    @PatchMapping("/reports/{id}")
    public ApiResponse<Void> review(@PathVariable UUID id, @RequestBody Map<String, String> body, HttpServletRequest http) {
        admin.reviewReport(id, ReportStatus.valueOf(body.get("status")), http.getRemoteAddr());
        return ApiResponse.message("Reporte actualizado.");
    }

    @GetMapping("/audit")
    public ApiResponse<PageResponse<AdminService.AuditItem>> audit(@RequestParam(defaultValue = "0") int page) {
        return ApiResponse.ok(admin.audit(page));
    }
}
