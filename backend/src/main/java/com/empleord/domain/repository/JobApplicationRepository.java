package com.empleord.domain.repository;

import com.empleord.domain.entity.JobApplication;
import com.empleord.domain.enums.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobApplicationRepository extends JpaRepository<JobApplication, UUID> {
    Optional<JobApplication> findByJobIdAndCandidateUserId(UUID jobId, UUID candidateId);
    List<JobApplication> findByCandidateUserIdOrderByCreatedAtDesc(UUID candidateId);
    List<JobApplication> findByJobIdOrderByCreatedAtDesc(UUID jobId);
    long countByCandidateUserId(UUID candidateId);

    @Query("""
            select count(a) from JobApplication a
            where a.candidate.userId = :candidateId
            and a.status not in :terminal
            """)
    long countInProgress(@Param("candidateId") UUID candidateId, @Param("terminal") List<ApplicationStatus> terminal);

    long countByJobCompanyId(UUID companyId);

    @Query("""
            select a.status, count(a) from JobApplication a
            where a.job.company.id = :companyId
            group by a.status
            """)
    List<Object[]> countByStatusForCompany(@Param("companyId") UUID companyId);

    @Query("select count(a) from JobApplication a")
    long countAll();

    boolean existsByResumeId(UUID resumeId);

    boolean existsByResumeIdAndJobCompanyId(UUID resumeId, UUID companyId);
}
