package com.empleord.domain.repository;

import com.empleord.domain.entity.JobAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JobAlertRepository extends JpaRepository<JobAlert, UUID> {
    long countByCandidateUserIdAndActiveTrue(UUID candidateId);
}
