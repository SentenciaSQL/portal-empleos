package com.empleord.domain.repository;

import com.empleord.domain.entity.CompanySubscription;
import com.empleord.domain.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanySubscriptionRepository extends JpaRepository<CompanySubscription, UUID> {
    Optional<CompanySubscription> findByCompanyId(UUID companyId);

    Optional<CompanySubscription> findByProviderAndProviderSubscriptionId(String provider, String providerSubscriptionId);

    List<CompanySubscription> findByEndsAtBeforeAndStatusIn(Instant endsAt, Collection<SubscriptionStatus> statuses);
}
