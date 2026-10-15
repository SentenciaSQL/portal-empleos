package com.empleord.domain.repository;

import com.empleord.domain.entity.FeaturedJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FeaturedJobRepository extends JpaRepository<FeaturedJob, UUID> {
    @Query("""
            select f.job.id from FeaturedJob f
            where f.job.id in :jobIds and f.startsAt <= :now and f.endsAt > :now
            """)
    List<UUID> findActiveJobIds(@Param("jobIds") Collection<UUID> jobIds, @Param("now") Instant now);

    @Query("""
            select count(f) from FeaturedJob f
            where f.job.id = :jobId and f.startsAt <= :now and f.endsAt > :now
            """)
    long countActive(@Param("jobId") UUID jobId, @Param("now") Instant now);
}
