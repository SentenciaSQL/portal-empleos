package com.empleord.domain.repository;

import com.empleord.domain.entity.Job;
import com.empleord.domain.enums.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID>, JpaSpecificationExecutor<Job> {
    Optional<Job> findBySlugAndDeletedAtIsNull(String slug);
    Optional<Job> findByIdAndDeletedAtIsNull(UUID id);
    boolean existsBySlug(String slug);
    long countByCompanyIdAndStatusAndDeletedAtIsNull(UUID companyId, JobStatus status);
    List<Job> findByCompanyIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID companyId);
    long countByStatusAndDeletedAtIsNull(JobStatus status);

    @Query("""
            select j from Job j
            where j.deletedAt is null and j.status = com.empleord.domain.enums.JobStatus.PUBLISHED
            and j.expiresAt is not null and j.expiresAt < :now
            """)
    List<Job> findExpiredPublished(@Param("now") Instant now);

    @Query("""
            select j from Job j
            where j.deletedAt is null and j.status = com.empleord.domain.enums.JobStatus.PUBLISHED
            and j.expiresAt is not null and j.expiresAt > :now and j.expiresAt < :until
            """)
    List<Job> findExpiringBetween(@Param("now") Instant now, @Param("until") Instant until);

    @Modifying
    @Query("update Job j set j.viewCount = j.viewCount + 1 where j.id = :id")
    void incrementViews(@Param("id") UUID id);

    @Query("""
            select j from Job j
            where j.deletedAt is null
            and (:status is null or j.status = :status)
            and (:q is null or lower(j.title) like :q or lower(j.company.name) like :q)
            """)
    Page<Job> searchAdmin(@Param("q") String q, @Param("status") JobStatus status, Pageable pageable);

    @Query("""
            select j.slug from Job j
            where j.deletedAt is null and j.status = com.empleord.domain.enums.JobStatus.PUBLISHED
            and (j.expiresAt is null or j.expiresAt > :now)
            """)
    List<String> findOpenSlugs(@Param("now") Instant now);
}
