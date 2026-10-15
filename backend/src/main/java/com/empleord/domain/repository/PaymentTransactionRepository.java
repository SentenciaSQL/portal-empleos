package com.empleord.domain.repository;

import com.empleord.domain.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {
    boolean existsByProviderAndProviderEventId(String provider, String providerEventId);

    Optional<PaymentTransaction> findFirstByProviderAndProviderSubscriptionIdAndStatusOrderByCreatedAtDesc(
            String provider, String providerSubscriptionId, String status);

    Optional<PaymentTransaction> findFirstByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    boolean existsByProviderAndProviderSubscriptionIdAndStatus(String provider, String providerSubscriptionId, String status);
}
