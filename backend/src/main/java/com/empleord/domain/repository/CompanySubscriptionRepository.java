package com.empleord.domain.repository;

import com.empleord.domain.entity.CompanySubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CompanySubscriptionRepository extends JpaRepository<CompanySubscription, UUID> {
    Optional<CompanySubscription> findByCompanyId(UUID companyId);
}
