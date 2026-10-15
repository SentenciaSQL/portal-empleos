package com.empleord.candidate;

import com.empleord.domain.enums.LanguageProficiency;
import com.empleord.domain.enums.SalaryPeriod;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class CandidateDtos {

    private CandidateDtos() {
    }

    public record ProfileRequest(
            @Size(max = 160) String headline,
            @Size(max = 4000) String summary,
            Integer provinceId,
            Integer municipalityId,
            @Size(max = 255) String linkedinUrl,
            @Size(max = 255) String portfolioUrl,
            BigDecimal salaryMin,
            BigDecimal salaryMax,
            @Size(min = 3, max = 3) String salaryCurrency,
            SalaryPeriod salaryPeriod,
            boolean openToWork
    ) {
    }

    public record ExperienceRequest(
            @NotBlank @Size(max = 160) String jobTitle,
            @NotBlank @Size(max = 160) String companyName,
            @Size(max = 160) String location,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            boolean currentJob,
            @Size(max = 4000) String description
    ) {
    }

    public record EducationRequest(
            @NotBlank @Size(max = 180) String institution,
            @NotBlank @Size(max = 160) String degree,
            @Size(max = 160) String fieldOfStudy,
            @Min(1950) @Max(2100) Integer startYear,
            @Min(1950) @Max(2100) Integer endYear,
            @Size(max = 2000) String description
    ) {
    }

    public record CertificationRequest(
            @NotBlank @Size(max = 180) String name,
            @Size(max = 180) String issuer,
            @Min(1950) @Max(2100) Integer issueYear,
            @Size(max = 255) String credentialUrl
    ) {
    }

    public record LanguageRequest(
            @NotBlank @Size(max = 60) String language,
            @NotNull LanguageProficiency proficiency
    ) {
    }

    public record SkillSelection(@NotNull List<Integer> skillIds) {
    }

    public record SaveJobRequest(@NotNull UUID jobId) {
    }

    public record ProfileResponse(
            String headline,
            String summary,
            Integer provinceId,
            String province,
            Integer municipalityId,
            String municipality,
            String linkedinUrl,
            String portfolioUrl,
            BigDecimal salaryMin,
            BigDecimal salaryMax,
            String salaryCurrency,
            String salaryPeriod,
            boolean openToWork,
            List<ExperienceItem> experiences,
            List<EducationItem> education,
            List<CertificationItem> certifications,
            List<LanguageItem> languages,
            List<CatalogSkill> skills,
            List<ResumeItem> resumes
    ) {
    }

    public record ExperienceItem(UUID id, String jobTitle, String companyName, String location, LocalDate startDate, LocalDate endDate, boolean currentJob, String description) {
    }

    public record EducationItem(UUID id, String institution, String degree, String fieldOfStudy, Integer startYear, Integer endYear, String description) {
    }

    public record CertificationItem(UUID id, String name, String issuer, Integer issueYear, String credentialUrl) {
    }

    public record LanguageItem(UUID id, String language, String proficiency) {
    }

    public record CatalogSkill(Integer id, String name) {
    }

    public record ResumeItem(UUID id, String filename, long sizeBytes, boolean primary, Instant createdAt) {
    }

    public record CandidateStats(long applications, long inProgress, long savedJobs, long activeAlerts) {
    }
}
