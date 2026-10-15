package com.empleord.domain.repository;

import com.empleord.domain.entity.PaymentProviderRef;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentProviderRefRepository extends JpaRepository<PaymentProviderRef, PaymentProviderRef.Key> {
}
