package com.empleord.domain.repository;

import com.empleord.domain.entity.Certification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CertificationRepository extends JpaRepository<Certification, UUID> {
    List<Certification> findByCandidateUserIdOrderByIssueYearDesc(UUID candidateId);
    void deleteByCandidateUserId(UUID candidateId);
}
