package com.empleord.domain.repository;

import com.empleord.domain.entity.Education;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EducationRepository extends JpaRepository<Education, UUID> {
    List<Education> findByCandidateUserIdOrderByEndYearDesc(UUID candidateId);
    void deleteByCandidateUserId(UUID candidateId);
}
