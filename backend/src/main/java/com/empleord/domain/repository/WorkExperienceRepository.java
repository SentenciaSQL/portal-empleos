package com.empleord.domain.repository;

import com.empleord.domain.entity.WorkExperience;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkExperienceRepository extends JpaRepository<WorkExperience, UUID> {
    List<WorkExperience> findByCandidateUserIdOrderByStartDateDesc(UUID candidateId);
    void deleteByCandidateUserId(UUID candidateId);
}
