package com.empleord.application;

import com.empleord.audit.AuditService;
import com.empleord.billing.PlanService;
import com.empleord.candidate.ResumeService;
import com.empleord.common.ApiException;
import com.empleord.company.CompanyAccess;
import com.empleord.domain.entity.CandidateProfile;
import com.empleord.domain.entity.CompanyMember;
import com.empleord.domain.entity.Education;
import com.empleord.domain.entity.Job;
import com.empleord.domain.entity.JobApplication;
import com.empleord.domain.entity.Resume;
import com.empleord.domain.entity.WorkExperience;
import com.empleord.domain.enums.ApplicationStatus;
import com.empleord.domain.enums.JobStatus;
import com.empleord.domain.enums.NotificationType;
import com.empleord.domain.repository.CompanyMemberRepository;
import com.empleord.domain.repository.EducationRepository;
import com.empleord.domain.repository.JobApplicationRepository;
import com.empleord.domain.repository.JobRepository;
import com.empleord.domain.repository.WorkExperienceRepository;
import com.empleord.notification.EmailService;
import com.empleord.notification.NotificationService;
import com.empleord.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
public class ApplicationService {

    private static final EnumSet<ApplicationStatus> COMPANY_STATUSES = EnumSet.of(
            ApplicationStatus.IN_REVIEW, ApplicationStatus.SHORTLISTED, ApplicationStatus.INTERVIEW,
            ApplicationStatus.OFFER, ApplicationStatus.HIRED, ApplicationStatus.REJECTED);
    private static final EnumSet<ApplicationStatus> LOCKED = EnumSet.of(ApplicationStatus.HIRED, ApplicationStatus.WITHDRAWN);

    private final JobRepository jobs;
    private final JobApplicationRepository applications;
    private final WorkExperienceRepository experiences;
    private final EducationRepository educations;
    private final CompanyMemberRepository members;
    private final ResumeService resumes;
    private final CompanyAccess access;
    private final PlanService plans;
    private final NotificationService notifications;
    private final EmailService email;
    private final AuditService audit;

    public ApplicationService(JobRepository jobs, JobApplicationRepository applications, WorkExperienceRepository experiences, EducationRepository educations,
                              CompanyMemberRepository members, ResumeService resumes, CompanyAccess access, PlanService plans,
                              NotificationService notifications, EmailService email, AuditService audit) {
        this.jobs = jobs;
        this.applications = applications;
        this.experiences = experiences;
        this.educations = educations;
        this.members = members;
        this.resumes = resumes;
        this.access = access;
        this.plans = plans;
        this.notifications = notifications;
        this.email = email;
        this.audit = audit;
    }

    @Transactional
    public ApplicationDtos.CandidateApplication apply(UUID jobId, ApplicationDtos.ApplyRequest request, String ip) {
        Job job = jobs.findByIdAndDeletedAtIsNull(jobId).orElseThrow(() -> ApiException.notFound("La oferta no existe."));
        if (!job.isOpen(java.time.Instant.now())) {
            throw ApiException.badRequest("Esta oferta no acepta postulaciones.");
        }
        UUID candidateId = SecurityUtils.requireUser().getId();
        if (applications.findByJobIdAndCandidateUserId(jobId, candidateId).isPresent()) {
            throw ApiException.conflict("Ya enviaste una postulación a esta oferta.");
        }
        Resume resume = resumes.primaryOrOwned(request == null ? null : request.resumeId());
        JobApplication application = new JobApplication();
        application.setJob(job);
        application.setCandidate(resume.getCandidate());
        application.setResume(resume);
        application.setCoverLetter(request == null ? null : com.empleord.common.Text.clean(request.coverLetter()));
        application.setStatus(ApplicationStatus.RECEIVED);
        applications.save(application);
        String link = "/empresa/ofertas/" + job.getId() + "/candidatos";
        for (CompanyMember member : members.findByCompanyId(job.getCompany().getId())) {
            String locale = member.getUser().getLocale();
            notifications.notify(member.getUser(), NotificationType.APPLICATION_RECEIVED,
                    com.empleord.common.Texts.forUser(locale, "Nueva postulación", "New application"),
                    com.empleord.common.Texts.forUser(locale, "Hay una nueva postulación para " + job.getTitle() + ".", "There is a new application for " + job.getTitle() + "."), link);
            email.sendApplicationReceived(member.getUser(), job.getTitle());
        }
        audit.record(candidateId, "APPLY", "JobApplication", application.getId().toString(), job.getId().toString(), ip);
        return candidateView(application);
    }

    @Transactional(readOnly = true)
    public List<ApplicationDtos.CandidateApplication> mine() {
        return applications.findByCandidateUserIdOrderByCreatedAtDesc(SecurityUtils.requireUser().getId()).stream().map(this::candidateView).toList();
    }

    @Transactional
    public void withdraw(UUID id, String ip) {
        JobApplication application = applications.findById(id).orElseThrow(() -> ApiException.notFound("La postulación no existe."));
        if (!application.getCandidate().getUserId().equals(SecurityUtils.requireUser().getId())) {
            throw ApiException.forbidden("No puedes modificar esta postulación.");
        }
        if (LOCKED.contains(application.getStatus()) || application.getStatus() == ApplicationStatus.REJECTED) {
            throw ApiException.badRequest("Esta postulación ya no se puede retirar.");
        }
        application.setStatus(ApplicationStatus.WITHDRAWN);
        audit.record(SecurityUtils.requireUser().getId(), "WITHDRAW", "JobApplication", id.toString(), null, ip);
    }

    @Transactional(readOnly = true)
    public List<ApplicationDtos.CompanyApplication> forJob(UUID jobId, Integer minYears, Integer skillId, String education, Boolean favoriteOnly) {
        Job job = jobs.findByIdAndDeletedAtIsNull(jobId).orElseThrow(() -> ApiException.notFound("La oferta no existe."));
        access.requireJob(job);
        boolean advanced = plans.current(job.getCompany()).isCandidateFilters();
        if (!advanced && (minYears != null || skillId != null || (education != null && !education.isBlank()))) {
            throw ApiException.forbidden("Los filtros por experiencia, habilidades y formación están incluidos en el plan Profesional.");
        }
        String educationQuery = education == null ? "" : education.trim().toLowerCase();
        return applications.findByJobIdOrderByCreatedAtDesc(jobId).stream()
                .filter(application -> favoriteOnly == null || !favoriteOnly || application.isFavorite())
                .filter(application -> matches(application, minYears, skillId, educationQuery))
                .map(this::companyView)
                .toList();
    }

    @Transactional
    public ApplicationDtos.CompanyApplication update(UUID id, ApplicationDtos.UpdateRequest request, String ip) {
        JobApplication application = applications.findById(id).orElseThrow(() -> ApiException.notFound("La postulación no existe."));
        access.requireJob(application.getJob());
        if (request.internalNote() != null) {
            application.setInternalNote(com.empleord.common.Text.clean(request.internalNote()));
        }
        if (request.favorite() != null) {
            application.setFavorite(request.favorite());
        }
        if (request.status() != null && request.status() != application.getStatus()) {
            if (!COMPANY_STATUSES.contains(request.status())) {
                throw ApiException.badRequest("El estado solicitado no está permitido.");
            }
            if (application.getStatus() == ApplicationStatus.WITHDRAWN) {
                throw ApiException.badRequest("La persona candidata retiró esta postulación.");
            }
            application.setStatus(request.status());
            String locale = application.getCandidate().getUser().getLocale();
            notifications.notify(application.getCandidate().getUser(), NotificationType.APPLICATION_STATUS,
                    com.empleord.common.Texts.forUser(locale, "Actualización de tu postulación", "Update on your application"),
                    com.empleord.common.Texts.forUser(locale, "Tu postulación a " + application.getJob().getTitle() + " cambió de estado.", "Your application to " + application.getJob().getTitle() + " has a new status."),
                    "/candidato/postulaciones");
            email.sendApplicationStatus(application.getCandidate().getUser(), application.getJob().getTitle(), application.getJob().getCompany().getName());
            audit.record(SecurityUtils.requireUser().getId(), "APPLICATION_STATUS", "JobApplication", id.toString(), request.status().name(), ip);
        }
        return companyView(application);
    }

    @Transactional(readOnly = true)
    public String resumeLink(UUID applicationId) {
        JobApplication application = applications.findById(applicationId).orElseThrow(() -> ApiException.notFound("La postulación no existe."));
        access.requireJob(application.getJob());
        if (application.getResume() == null) {
            throw ApiException.notFound("Esta postulación no tiene currículum.");
        }
        return resumes.signedLinkForCompany(application.getResume().getId(), SecurityUtils.requireUser().getId());
    }

    private boolean matches(JobApplication application, Integer minYears, Integer skillId, String educationQuery) {
        CandidateProfile candidate = application.getCandidate();
        if (minYears != null && years(candidate.getUserId()) < minYears) {
            return false;
        }
        if (skillId != null && candidate.getSkills().stream().noneMatch(skill -> skill.getId().equals(skillId))) {
            return false;
        }
        if (!educationQuery.isBlank()) {
            boolean found = educations.findByCandidateUserIdOrderByEndYearDesc(candidate.getUserId()).stream().anyMatch(item ->
                    contains(item.getInstitution(), educationQuery) || contains(item.getDegree(), educationQuery) || contains(item.getFieldOfStudy(), educationQuery));
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private int years(UUID candidateId) {
        long months = 0;
        for (WorkExperience experience : experiences.findByCandidateUserIdOrderByStartDateDesc(candidateId)) {
            LocalDate end = experience.isCurrentJob() || experience.getEndDate() == null ? LocalDate.now() : experience.getEndDate();
            if (!end.isBefore(experience.getStartDate())) {
                months += ChronoUnit.MONTHS.between(experience.getStartDate(), end);
            }
        }
        return (int) Math.max(0, months / 12);
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase().contains(query);
    }

    private ApplicationDtos.CandidateApplication candidateView(JobApplication application) {
        Job job = application.getJob();
        return new ApplicationDtos.CandidateApplication(application.getId(), job.getId(), job.getSlug(), job.getTitle(), job.getCompany().getName(),
                application.getStatus().name(), application.getCreatedAt(), job.getStatus() == JobStatus.PUBLISHED && job.isOpen(java.time.Instant.now()));
    }

    private ApplicationDtos.CompanyApplication companyView(JobApplication application) {
        CandidateProfile candidate = application.getCandidate();
        List<String> skills = candidate.getSkills().stream().map(skill -> skill.getName()).sorted().toList();
        List<String> education = educations.findByCandidateUserIdOrderByEndYearDesc(candidate.getUserId()).stream()
                .map(item -> item.getDegree() + " · " + item.getInstitution()).toList();
        var user = candidate.getUser();
        boolean deleted = user.getDeletedAt() != null;
        return new ApplicationDtos.CompanyApplication(
                application.getId(),
                candidate.getUserId(),
                deleted ? "Cuenta eliminada" : user.fullName(),
                candidate.getHeadline(),
                candidate.getProvince() == null ? null : candidate.getProvince().getName(),
                deleted ? null : user.getEmail(),
                deleted ? null : user.getPhone(),
                years(candidate.getUserId()),
                skills,
                education,
                application.getStatus().name(),
                application.getCoverLetter(),
                application.getInternalNote(),
                application.isFavorite(),
                application.getResume() != null,
                application.getCreatedAt());
    }
}
