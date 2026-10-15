package com.empleord.domain.repository;

import com.empleord.domain.entity.CompanyMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyMemberRepository extends JpaRepository<CompanyMember, UUID> {
    Optional<CompanyMember> findByUserId(UUID userId);
    List<CompanyMember> findByCompanyId(UUID companyId);
    boolean existsByUserId(UUID userId);
}
