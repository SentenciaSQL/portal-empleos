package com.empleord.application;

import com.empleord.domain.enums.ApplicationStatus;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class ApplicationDtos {

    private ApplicationDtos() {
    }

    public record ApplyRequest(UUID resumeId, @Size(max = 4000) String coverLetter) {
    }

    public record UpdateRequest(ApplicationStatus status, @Size(max = 4000) String internalNote, Boolean favorite) {
    }

    public record CandidateApplication(
            UUID id,
            UUID jobId,
            String jobSlug,
            String jobTitle,
            String companyName,
            String status,
            Instant appliedAt,
            boolean open
    ) {
    }

    public record CompanyApplication(
            UUID id,
            UUID candidateId,
            String candidateName,
            String headline,
            String province,
            String email,
            String phone,
            int yearsExperience,
            java.util.List<String> skills,
            java.util.List<String> education,
            String status,
            String coverLetter,
            String internalNote,
            boolean favorite,
            boolean resumeAvailable,
            Instant appliedAt
    ) {
    }
}
