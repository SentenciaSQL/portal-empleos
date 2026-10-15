package com.empleord.domain.repository;

import com.empleord.domain.entity.Company;
import com.empleord.domain.enums.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {
    Optional<Company> findBySlugAndDeletedAtIsNull(String slug);
    Optional<Company> findByIdAndDeletedAtIsNull(UUID id);
    boolean existsBySlug(String slug);
    long countByDeletedAtIsNull();
    long countByDeletedAtIsNullAndVerificationStatus(VerificationStatus status);

    @Query("""
            select c from Company c
            where c.deletedAt is null
            and (:q is null or lower(c.name) like :q or lower(c.slug) like :q)
            """)
    Page<Company> search(@Param("q") String q, Pageable pageable);
}
