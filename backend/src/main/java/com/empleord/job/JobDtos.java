package com.empleord.job;

import com.empleord.domain.enums.ContractType;
import com.empleord.domain.enums.ExperienceLevel;
import com.empleord.domain.enums.JobStatus;
import com.empleord.domain.enums.RemoteScope;
import com.empleord.domain.enums.SalaryPeriod;
import com.empleord.domain.enums.WorkMode;
import com.empleord.domain.enums.WorkSchedule;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class JobDtos {

    private JobDtos() {
    }

    public record JobRequest(
            @NotBlank @Size(max = 180) String title,
            @NotBlank(message = "La descripción es obligatorio.") @Size(max = 8000) String description,
            @Size(max = 8000) String requirements,
            @Size(max = 8000) String responsibilities,
            @NotNull Integer categoryId,
            @NotNull ContractType contractType,
            @NotNull WorkMode workMode,
            @NotNull WorkSchedule workSchedule,
            @NotNull ExperienceLevel experienceLevel,
            Integer provinceId,
            Integer municipalityId,
            @Size(max = 2) String countryCode,
            @Size(max = 160) String locationLabel,
            @NotNull RemoteScope remoteScope,
            @Size(max = 255) String geographicRestriction,
            BigDecimal salaryMin,
            BigDecimal salaryMax,
            @Size(max = 3) String salaryCurrency,
            SalaryPeriod salaryPeriod,
            boolean salaryNegotiable,
            boolean acceptsNoExperience,
            Instant expiresAt,
            @Size(max = 30, message = "Puedes asociar hasta 30 habilidades.") List<Integer> skillIds,
            boolean publish
    ) {
    }

    public record StatusRequest(@NotNull JobStatus status) {
    }

    public record JobCard(
            UUID id,
            String slug,
            String title,
            String companyName,
            String companySlug,
            String logoUrl,
            boolean companyVerified,
            String countryCode,
            String countryName,
            String province,
            String municipality,
            String locationLabel,
            String workMode,
            String contractType,
            String workSchedule,
            String experienceLevel,
            String category,
            BigDecimal salaryMin,
            BigDecimal salaryMax,
            String salaryCurrency,
            String salaryPeriod,
            boolean salaryNegotiable,
            boolean acceptsNoExperience,
            String remoteScope,
            String geographicRestriction,
            Instant publishedAt,
            boolean featured,
            boolean open
    ) {
    }

    public record JobDetail(
            JobCard card,
            String description,
            String requirements,
            String responsibilities,
            String status,
            Instant expiresAt,
            long viewCount,
            List<String> skills,
            boolean saved,
            boolean applied
    ) {
    }

    public record JobForm(
            UUID id,
            String title,
            String description,
            String requirements,
            String responsibilities,
            Integer categoryId,
            String contractType,
            String workMode,
            String workSchedule,
            String experienceLevel,
            Integer provinceId,
            Integer municipalityId,
            String countryCode,
            String locationLabel,
            String remoteScope,
            String geographicRestriction,
            BigDecimal salaryMin,
            BigDecimal salaryMax,
            String salaryCurrency,
            String salaryPeriod,
            boolean salaryNegotiable,
            boolean acceptsNoExperience,
            Instant expiresAt,
            List<Integer> skillIds,
            String status
    ) {
    }

    public record ManagedJob(
            UUID id,
            String slug,
            String title,
            String status,
            Instant publishedAt,
            Instant expiresAt,
            long viewCount,
            long applications,
            boolean featured
    ) {
    }
}
