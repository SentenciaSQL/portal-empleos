package com.empleord.job;

import com.empleord.domain.entity.Job;
import com.empleord.domain.enums.VerificationStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class JobMapper {

    public JobDtos.JobCard toCard(Job job, boolean featured, Instant now) {
        return new JobDtos.JobCard(
                job.getId(),
                job.getSlug(),
                job.getTitle(),
                job.getCompany().getName(),
                job.getCompany().getSlug(),
                job.getCompany().getLogoStorageKey() == null ? null : "/api/media/logos/" + job.getCompany().getId(),
                job.getCompany().getVerificationStatus() == VerificationStatus.VERIFIED,
                job.getCountry().getCode(),
                job.getCountry().getName(),
                job.getProvince() == null ? null : job.getProvince().getName(),
                job.getMunicipality() == null ? null : job.getMunicipality().getName(),
                job.getLocationLabel(),
                job.getWorkMode().name(),
                job.getContractType().name(),
                job.getWorkSchedule().name(),
                job.getExperienceLevel().name(),
                job.getCategory().getName(),
                job.getSalaryMin(),
                job.getSalaryMax(),
                job.getSalaryCurrency(),
                job.getSalaryPeriod() == null ? null : job.getSalaryPeriod().name(),
                job.isSalaryNegotiable(),
                job.isAcceptsNoExperience(),
                job.getRemoteScope().name(),
                job.getGeographicRestriction(),
                job.getPublishedAt(),
                featured,
                job.isOpen(now));
    }
}
