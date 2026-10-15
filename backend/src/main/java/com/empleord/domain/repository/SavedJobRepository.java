package com.empleord.domain.repository;

import com.empleord.domain.entity.SavedJob;
import com.empleord.domain.entity.SavedJob.SavedJobId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SavedJobRepository extends JpaRepository<SavedJob, SavedJobId> {
    List<SavedJob> findByCandidateUserIdOrderByCreatedAtDesc(UUID candidateId);
    boolean existsByIdCandidateIdAndIdJobId(UUID candidateId, UUID jobId);
    long countByCandidateUserId(UUID candidateId);
    void deleteByIdCandidateIdAndIdJobId(UUID candidateId, UUID jobId);
}
