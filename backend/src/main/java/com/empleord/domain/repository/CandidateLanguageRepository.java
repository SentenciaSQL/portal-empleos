package com.empleord.domain.repository;

import com.empleord.domain.entity.CandidateLanguage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CandidateLanguageRepository extends JpaRepository<CandidateLanguage, UUID> {
    List<CandidateLanguage> findByCandidateUserIdOrderByLanguageAsc(UUID candidateId);
    void deleteByCandidateUserId(UUID candidateId);
}
