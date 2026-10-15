package com.empleord.company;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CompanyDtos {

    private CompanyDtos() {
    }

    public record ProfileRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 5000) String description,
            @Size(max = 255) String website,
            @Size(max = 120) String industry,
            Integer provinceId,
            Integer municipalityId,
            @Size(max = 2) String countryCode,
            @Size(max = 120) String recruiterTitle,
            @Size(max = 30) String phone
    ) {
    }

    public record MemberRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName
    ) {
    }

    public record PublicCompany(
            UUID id,
            String name,
            String slug,
            String description,
            String website,
            String industry,
            String province,
            String municipality,
            String countryCode,
            String countryName,
            boolean verified,
            String logoUrl
    ) {
    }

    public record ManagedCompany(
            PublicCompany profile,
            String memberRole,
            String recruiterTitle,
            String planCode,
            String planName,
            int maxActiveJobs,
            boolean candidateFilters,
            boolean advancedStats
    ) {
    }

    public record MemberItem(UUID userId, String name, String email, String role) {
    }

    public record CompanyStats(
            long activeJobs,
            long applications,
            Map<String, Long> byStatus,
            long views,
            List<JobPerformance> jobs,
            boolean advancedStats
    ) {
    }

    public record JobPerformance(UUID id, String title, String status, long views, long applications) {
    }
}
