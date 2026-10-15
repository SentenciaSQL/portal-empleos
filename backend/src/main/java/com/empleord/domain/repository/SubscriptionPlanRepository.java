package com.empleord.domain.repository;

import com.empleord.domain.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Integer> {
    Optional<SubscriptionPlan> findByCode(String code);
    List<SubscriptionPlan> findAllByOrderByPriceAmountAsc();
}
