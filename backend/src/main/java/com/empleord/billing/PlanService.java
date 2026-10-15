package com.empleord.billing;

import com.empleord.common.ApiException;
import com.empleord.common.Texts;
import com.empleord.domain.entity.Company;
import com.empleord.domain.entity.CompanySubscription;
import com.empleord.domain.entity.SubscriptionPlan;
import com.empleord.domain.enums.JobStatus;
import com.empleord.domain.enums.SubscriptionStatus;
import com.empleord.domain.repository.CompanySubscriptionRepository;
import com.empleord.domain.repository.JobRepository;
import com.empleord.domain.repository.SubscriptionPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class PlanService {

    private final SubscriptionPlanRepository plans;
    private final CompanySubscriptionRepository subscriptions;
    private final JobRepository jobs;

    public PlanService(SubscriptionPlanRepository plans, CompanySubscriptionRepository subscriptions, JobRepository jobs) {
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.jobs = jobs;
    }

    @Transactional
    public void assignFree(Company company) {
        SubscriptionPlan free = plans.findByCode("FREE").orElseThrow(() -> ApiException.notFound("No existe el plan gratuito."));
        CompanySubscription subscription = new CompanySubscription();
        subscription.setCompany(company);
        subscription.setPlan(free);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartedAt(Instant.now());
        subscriptions.save(subscription);
    }

    @Transactional(readOnly = true)
    public SubscriptionPlan current(Company company) {
        CompanySubscription subscription = subscriptions.findByCompanyId(company.getId())
                .orElseThrow(() -> ApiException.notFound("La empresa no tiene un plan asignado."));
        return effective(subscription);
    }

    private SubscriptionPlan effective(CompanySubscription subscription) {
        Instant now = Instant.now();
        boolean openEnded = subscription.getEndsAt() == null || subscription.getEndsAt().isAfter(now);
        if (subscription.getStatus() == SubscriptionStatus.ACTIVE && openEnded) {
            return subscription.getPlan();
        }
        if ((subscription.getStatus() == SubscriptionStatus.PAYMENT_FAILED || subscription.getStatus() == SubscriptionStatus.CANCELLED)
                && subscription.getEndsAt() != null && subscription.getEndsAt().isAfter(now)) {
            return subscription.getPlan();
        }
        return plans.findByCode("FREE").orElseThrow(() -> ApiException.notFound("No existe el plan gratuito."));
    }

    @Transactional(readOnly = true)
    public void assertCanPublish(Company company, boolean alreadyPublished) {
        if (alreadyPublished) {
            return;
        }
        SubscriptionPlan plan = current(company);
        long active = jobs.countByCompanyIdAndStatusAndDeletedAtIsNull(company.getId(), JobStatus.PUBLISHED);
        if (active >= plan.getMaxActiveJobs()) {
            throw ApiException.conflict(Texts.choose(
                    "Tu plan " + plan.getName() + " permite " + plan.getMaxActiveJobs() + " ofertas activas. Pausa o cierra una oferta, o pide un cambio de plan.",
                    "Your " + plan.getName() + " plan allows " + plan.getMaxActiveJobs() + " active jobs. Pause or close one, or change the plan."));
        }
    }

    @Transactional
    public SubscriptionPlan change(Company company, String code) {
        SubscriptionPlan plan = plans.findByCode(code).filter(SubscriptionPlan::isActive)
                .orElseThrow(() -> ApiException.badRequest("El plan no está disponible."));
        CompanySubscription subscription = subscriptions.findByCompanyId(company.getId())
                .orElseThrow(() -> ApiException.notFound("La empresa no tiene suscripción."));
        subscription.setPlan(plan);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartedAt(Instant.now());
        subscription.setEndsAt(null);
        return plan;
    }
}
