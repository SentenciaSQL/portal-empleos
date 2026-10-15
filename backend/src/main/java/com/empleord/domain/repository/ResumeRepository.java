package com.empleord.domain.repository;

import com.empleord.domain.entity.Resume;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResumeRepository extends JpaRepository<Resume, UUID> {
    List<Resume> findByCandidateUserIdOrderByCreatedAtDesc(UUID candidateId);
    Optional<Resume> findFirstByCandidateUserIdAndPrimaryResumeTrue(UUID candidateId);
    long countByCandidateUserId(UUID candidateId);
}
