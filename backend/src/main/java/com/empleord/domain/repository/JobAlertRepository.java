package com.empleord.domain.repository;

import com.empleord.domain.entity.JobAlert;
import com.empleord.domain.enums.AlertFrequency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobAlertRepository extends JpaRepository<JobAlert, UUID> {
    long countByCandidateUserIdAndActiveTrue(UUID candidateId);

    long countByCandidateUserId(UUID candidateId);

    List<JobAlert> findByCandidateUserIdOrderByCreatedAtDesc(UUID candidateId);

    Optional<JobAlert> findByIdAndCandidateUserId(UUID id, UUID candidateId);

    List<JobAlert> findByActiveTrueAndFrequency(AlertFrequency frequency);
}
