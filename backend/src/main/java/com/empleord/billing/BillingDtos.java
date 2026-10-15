package com.empleord.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class BillingDtos {

    private BillingDtos() {
    }

    public record PlanOffer(
            String code,
            String name,
            String description,
            BigDecimal priceAmount,
            String currency,
            String billingPeriod,
            int maxActiveJobs,
            boolean candidateFilters,
            boolean advancedStats,
            boolean premiumProfile
    ) {
    }

    public record SubscriptionView(
            String effectivePlanCode,
            String effectivePlanName,
            String status,
            String storedPlanCode,
            Instant endsAt,
            boolean gatewayConfigured,
            String provider,
            String memberRole,
            String latestPaymentStatus,
            String latestEventType,
            List<PlanOffer> plans
    ) {
    }

    public record CheckoutRequest(String planCode, String returnUrl, String cancelUrl) {
    }

    public record CheckoutResponse(String redirectUrl, String status, String message) {
    }
}
