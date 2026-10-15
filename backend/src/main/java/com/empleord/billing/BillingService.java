package com.empleord.billing;

import com.empleord.common.ApiException;
import com.empleord.common.Texts;
import com.empleord.company.CompanyAccess;
import com.empleord.domain.entity.Company;
import com.empleord.domain.entity.CompanyMember;
import com.empleord.domain.entity.CompanySubscription;
import com.empleord.domain.entity.PaymentTransaction;
import com.empleord.domain.entity.SubscriptionPlan;
import com.empleord.domain.entity.User;
import com.empleord.domain.enums.NotificationType;
import com.empleord.domain.enums.SubscriptionStatus;
import com.empleord.domain.repository.CompanyMemberRepository;
import com.empleord.domain.repository.CompanySubscriptionRepository;
import com.empleord.domain.repository.PaymentTransactionRepository;
import com.empleord.domain.repository.SubscriptionPlanRepository;
import com.empleord.notification.EmailService;
import com.empleord.notification.NotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);
    private static final String PAYPAL = "PAYPAL";

    private final CompanyAccess access;
    private final PlanService plans;
    private final SubscriptionPlanRepository planRepository;
    private final CompanySubscriptionRepository subscriptions;
    private final PaymentTransactionRepository transactions;
    private final CompanyMemberRepository members;
    private final PaymentGateway gateway;
    private final ObjectProvider<PayPalClient> paypal;
    private final NotificationService notifications;
    private final EmailService email;
    private final ObjectMapper json;

    public BillingService(CompanyAccess access, PlanService plans, SubscriptionPlanRepository planRepository,
                          CompanySubscriptionRepository subscriptions, PaymentTransactionRepository transactions,
                          CompanyMemberRepository members, PaymentGateway gateway, ObjectProvider<PayPalClient> paypal,
                          NotificationService notifications, EmailService email, ObjectMapper json) {
        this.access = access;
        this.plans = plans;
        this.planRepository = planRepository;
        this.subscriptions = subscriptions;
        this.transactions = transactions;
        this.members = members;
        this.gateway = gateway;
        this.paypal = paypal;
        this.notifications = notifications;
        this.email = email;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public BillingDtos.SubscriptionView view() {
        CompanyMember member = access.requireMember();
        Company company = member.getCompany();
        CompanySubscription subscription = subscriptions.findByCompanyId(company.getId())
                .orElseThrow(() -> ApiException.notFound("La empresa no tiene suscripción."));
        SubscriptionPlan effective = plans.current(company);
        PaymentTransaction latest = transactions.findFirstByCompanyIdOrderByCreatedAtDesc(company.getId()).orElse(null);
        List<BillingDtos.PlanOffer> offers = planRepository.findAllByOrderByPriceAmountAsc().stream()
                .filter(SubscriptionPlan::isActive)
                .map(plan -> new BillingDtos.PlanOffer(plan.getCode(), plan.getName(), plan.getDescription(), plan.getPriceAmount(), plan.getCurrency(), plan.getBillingPeriod(), plan.getMaxActiveJobs(), plan.isCandidateFilters(), plan.isAdvancedStats(), plan.isPremiumProfile()))
                .toList();
        return new BillingDtos.SubscriptionView(
                effective.getCode(), effective.getName(), subscription.getStatus().name(), subscription.getPlan().getCode(),
                subscription.getEndsAt(), gateway.configured(), subscription.getProvider(), member.getMemberRole().name(),
                latest == null ? null : latest.getStatus(), latest == null ? null : latest.getEventType(), offers);
    }

    @Transactional
    public BillingDtos.CheckoutResponse checkout(String planCode, String returnUrl, String cancelUrl) {
        Company company = access.requireOwner().getCompany();
        if (!gateway.configured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, PAYPAL.equals(gateway.provider())
                    ? "Faltan credenciales de PayPal."
                    : "La pasarela de pago todavía no está configurada.");
        }
        requireHttp(returnUrl);
        requireHttp(cancelUrl);
        SubscriptionPlan plan = planRepository.findByCode(planCode).filter(SubscriptionPlan::isActive)
                .orElseThrow(() -> ApiException.badRequest("El plan no está disponible."));
        if (plan.getPriceAmount().signum() <= 0) {
            throw ApiException.badRequest("Solo se puede contratar un plan de pago desde la pasarela.");
        }
        CompanySubscription subscription = subscriptions.findByCompanyId(company.getId())
                .orElseThrow(() -> ApiException.notFound("La empresa no tiene suscripción."));
        if (plan.getCode().equals(plans.current(company).getCode()) && subscription.getStatus() == SubscriptionStatus.ACTIVE) {
            throw ApiException.conflict("Ya tienes este plan activo.");
        }
        PaymentGateway.PaymentSession session = gateway.startSubscriptionCheckout(new PaymentGateway.CheckoutRequest(
                company.getId(), plan.getName(), plan.getPriceAmount(), plan.getCurrency(), returnUrl, cancelUrl));
        if ("FREE".equals(plans.current(company).getCode())) {
            subscription.setStatus(SubscriptionStatus.PENDING);
            subscription.setProvider(session.provider());
            subscription.setProviderSubscriptionId(session.providerSubscriptionId());
            subscription.setProviderPlanId(session.providerPlanId());
        }
        PaymentTransaction checkout = new PaymentTransaction();
        checkout.setCompany(company);
        checkout.setPlan(plan);
        checkout.setProvider(session.provider());
        checkout.setProviderSubscriptionId(session.providerSubscriptionId());
        checkout.setAmount(plan.getPriceAmount());
        checkout.setCurrency(plan.getCurrency());
        checkout.setStatus("CHECKOUT");
        checkout.setEventType("CHECKOUT");
        transactions.save(checkout);
        return new BillingDtos.CheckoutResponse(session.redirectUrl(), "PENDING", Texts.choose(
                "La suscripción quedó registrada. El plan se activará cuando PayPal confirme el pago.",
                "The subscription was registered. The plan activates when PayPal confirms the payment."));
    }

    @Transactional
    public String cancel() {
        Company company = access.requireOwner().getCompany();
        CompanySubscription subscription = subscriptions.findByCompanyId(company.getId())
                .orElseThrow(() -> ApiException.notFound("La empresa no tiene suscripción."));
        if (subscription.getStatus() == SubscriptionStatus.PENDING && "FREE".equals(subscription.getPlan().getCode())) {
            String providerSubscriptionId = subscription.getProviderSubscriptionId();
            if (providerSubscriptionId != null && !providerSubscriptionId.isBlank()) {
                if (!gateway.configured()) {
                    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "No se puede cancelar en PayPal porque la pasarela no está configurada.");
                }
                gateway.cancelSubscription(providerSubscriptionId);
            }
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscription.setProvider(null);
            subscription.setProviderSubscriptionId(null);
            subscription.setProviderPlanId(null);
            return "Se canceló la contratación pendiente. Sigues en el plan gratuito.";
        }
        boolean providerBacked = subscription.getProviderSubscriptionId() != null && !subscription.getProviderSubscriptionId().isBlank();
        if (!providerBacked && "FREE".equals(plans.current(company).getCode())) {
            throw ApiException.badRequest("Ya estás en el plan gratuito.");
        }
        if (providerBacked) {
            if (!gateway.configured()) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "No se puede cancelar en PayPal porque la pasarela no está configurada.");
            }
            gateway.cancelSubscription(subscription.getProviderSubscriptionId());
            gateway.subscriptionAccessUntil(subscription.getProviderSubscriptionId()).ifPresent(subscription::setEndsAt);
            subscription.setStatus(SubscriptionStatus.CANCELLED);
            notifyMembers(company, "Suscripción cancelada", "Subscription cancelled",
                    "El plan de pago sigue disponible hasta el fin del periodo ya pagado.",
                    "The paid plan stays available until the end of the period already paid.");
            return "Solicitud de cancelación enviada. El plan sigue vigente hasta el fin del periodo ya pagado.";
        }
        SubscriptionPlan free = planRepository.findByCode("FREE").orElseThrow(() -> ApiException.notFound("No existe el plan gratuito."));
        subscription.setPlan(free);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setEndsAt(null);
        subscription.setProvider(null);
        subscription.setProviderSubscriptionId(null);
        subscription.setProviderPlanId(null);
        return "Volviste al plan gratuito.";
    }

    @Transactional
    public void handlePayPalWebhook(String transmissionId, String transmissionTime, String transmissionSig, String certUrl, String authAlgo, String body) {
        PayPalClient client = paypal.getIfAvailable();
        if (client == null || !client.credentialsPresent()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "La pasarela de pago todavía no está configurada.");
        }
        client.verifyWebhook(transmissionId, transmissionTime, transmissionSig, certUrl, authAlgo, body);
        JsonNode event;
        try {
            event = json.readTree(body);
        } catch (Exception ex) {
            throw ApiException.badRequest("La firma del webhook no es válida.");
        }
        String eventId = event.path("id").asText("");
        String eventType = event.path("event_type").asText("");
        if (eventId.isBlank() || eventType.isBlank()) {
            throw ApiException.badRequest("La firma del webhook no es válida.");
        }
        try {
            applyPayPalEvent(eventId, eventType, event.path("resource"));
        } catch (DataIntegrityViolationException ex) {
            log.info("Evento PayPal ya registrado: {}", eventId);
        }
    }

    @Transactional
    public void applyPayPalEvent(String eventId, String eventType, JsonNode resource) {
        if (transactions.existsByProviderAndProviderEventId(PAYPAL, eventId)) {
            return;
        }
        String subscriptionId = subscriptionId(eventType, resource);
        switch (eventType) {
            case "BILLING.SUBSCRIPTION.ACTIVATED" -> activate(resource, eventId, eventType, subscriptionId, false);
            case "PAYMENT.SALE.COMPLETED" -> activate(resource, eventId, eventType, subscriptionId, true);
            case "BILLING.SUBSCRIPTION.PAYMENT.FAILED" -> failed(resource, eventId, eventType, subscriptionId);
            case "BILLING.SUBSCRIPTION.CANCELLED" -> close(resource, eventId, eventType, subscriptionId, SubscriptionStatus.CANCELLED);
            case "BILLING.SUBSCRIPTION.EXPIRED", "BILLING.SUBSCRIPTION.SUSPENDED" -> close(resource, eventId, eventType, subscriptionId, SubscriptionStatus.EXPIRED);
            default -> log.info("Evento PayPal ignorado: {}", eventType);
        }
    }

    @Transactional
    public void expireDueSubscriptions() {
        subscriptions.findByEndsAtBeforeAndStatusIn(Instant.now(),
                        List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAYMENT_FAILED, SubscriptionStatus.CANCELLED))
                .forEach(subscription -> {
                    if (!"FREE".equals(subscription.getPlan().getCode())) {
                        subscription.setStatus(SubscriptionStatus.EXPIRED);
                    }
                });
    }

    private void activate(JsonNode resource, String eventId, String eventType, String subscriptionId, boolean sale) {
        CompanySubscription subscription = subscriptionFor(subscriptionId, resource);
        if (subscription == null) {
            log.info("Evento PayPal sin empresa local. evento={}", eventId);
            return;
        }
        BigDecimal amount = amount(sale ? resource.path("amount") : resource.path("billing_info").path("last_payment").path("amount"));
        String currency = currency(sale ? resource.path("amount") : resource.path("billing_info").path("last_payment").path("amount"), subscription);
        if (sale && amount.signum() <= 0) {
            record(subscription.getCompany(), subscription.getPlan(), eventId, resource.path("id").asText(null), subscriptionId, BigDecimal.ZERO, currency, "IGNORED", eventType);
            return;
        }
        PaymentTransaction checkout = transactions.findFirstByProviderAndProviderSubscriptionIdAndStatusOrderByCreatedAtDesc(PAYPAL, subscriptionId, "CHECKOUT").orElse(null);
        SubscriptionPlan plan = checkout != null && checkout.getPlan() != null ? checkout.getPlan() : subscription.getPlan();
        subscription.setPlan(plan);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setProvider(PAYPAL);
        subscription.setProviderSubscriptionId(subscriptionId);
        String providerPlanId = resource.path("plan_id").asText("");
        if (!providerPlanId.isBlank()) {
            subscription.setProviderPlanId(providerPlanId);
        }
        if (subscription.getStartedAt() == null) {
            subscription.setStartedAt(Instant.now());
        }
        gateway.subscriptionAccessUntil(subscriptionId).ifPresent(subscription::setEndsAt);
        String paymentId = sale ? resource.path("id").asText(null) : null;
        record(subscription.getCompany(), plan, eventId, paymentId, subscriptionId, amount, currency, sale ? "PAID" : "ACTIVATED", eventType);
        if (sale) {
            notifyMembers(subscription.getCompany(), "Pago confirmado", "Payment confirmed",
                    "PayPal confirmó el pago del plan " + plan.getName() + ".",
                    "PayPal confirmed the payment for the " + plan.getName() + " plan.");
        } else {
            notifyMembers(subscription.getCompany(), "Suscripción activa", "Subscription active",
                    "PayPal activó el plan " + plan.getName() + ".",
                    "PayPal activated the " + plan.getName() + " plan.");
        }
    }

    private void failed(JsonNode resource, String eventId, String eventType, String subscriptionId) {
        CompanySubscription subscription = subscriptionFor(subscriptionId, resource);
        if (subscription == null) {
            return;
        }
        boolean onPaidPlan = !"FREE".equals(subscription.getPlan().getCode());
        if (onPaidPlan) {
            subscription.setStatus(SubscriptionStatus.PAYMENT_FAILED);
        }
        record(subscription.getCompany(), subscription.getPlan(), eventId, resource.path("id").asText(null), subscriptionId, BigDecimal.ZERO, subscription.getPlan().getCurrency(), "FAILED", eventType);
        notifyMembers(subscription.getCompany(), "Pago rechazado", "Payment declined",
                "PayPal no pudo cobrar la suscripción. Un pago rechazado no activa el plan.",
                "PayPal could not charge the subscription. A declined payment does not activate the plan.");
    }

    private void close(JsonNode resource, String eventId, String eventType, String subscriptionId, SubscriptionStatus status) {
        CompanySubscription subscription = subscriptionFor(subscriptionId, resource);
        if (subscription == null) {
            return;
        }
        subscription.setStatus(status);
        if (status == SubscriptionStatus.CANCELLED) {
            gateway.subscriptionAccessUntil(subscriptionId).ifPresent(subscription::setEndsAt);
        }
        record(subscription.getCompany(), subscription.getPlan(), eventId, null, subscriptionId, BigDecimal.ZERO, subscription.getPlan().getCurrency(), status.name(), eventType);
        if (status == SubscriptionStatus.CANCELLED) {
            notifyMembers(subscription.getCompany(), "Suscripción cancelada", "Subscription cancelled",
                    "La suscripción quedó cancelada. Los beneficios pagados siguen hasta el fin del periodo.",
                    "The subscription was cancelled. Paid benefits continue until the end of the period.");
        } else {
            notifyMembers(subscription.getCompany(), "Suscripción vencida", "Subscription expired",
                    "La suscripción venció y la empresa vuelve a los límites del plan gratuito.",
                    "The subscription expired and the company is back on the free plan limits.");
        }
    }

    private CompanySubscription subscriptionFor(String subscriptionId, JsonNode resource) {
        if (subscriptionId != null && !subscriptionId.isBlank()) {
            CompanySubscription found = subscriptions.findByProviderAndProviderSubscriptionId(PAYPAL, subscriptionId).orElse(null);
            if (found != null) {
                return found;
            }
            PaymentTransaction checkout = transactions.findFirstByProviderAndProviderSubscriptionIdAndStatusOrderByCreatedAtDesc(PAYPAL, subscriptionId, "CHECKOUT").orElse(null);
            if (checkout != null) {
                return subscriptions.findByCompanyId(checkout.getCompany().getId()).orElse(null);
            }
        }
        String custom = resource.path("custom_id").asText("");
        if (custom.isBlank()) {
            return null;
        }
        try {
            return subscriptions.findByCompanyId(UUID.fromString(custom)).orElse(null);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void record(Company company, SubscriptionPlan plan, String eventId, String paymentId, String subscriptionId,
                        BigDecimal amount, String currency, String status, String eventType) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setCompany(company);
        transaction.setPlan(plan);
        transaction.setProvider(PAYPAL);
        transaction.setProviderEventId(eventId);
        transaction.setProviderPaymentId(blank(paymentId) ? null : paymentId);
        transaction.setProviderSubscriptionId(blank(subscriptionId) ? null : subscriptionId);
        transaction.setAmount(amount == null ? BigDecimal.ZERO : amount);
        transaction.setCurrency(currency == null || currency.isBlank() ? "DOP" : currency);
        transaction.setStatus(status);
        transaction.setEventType(eventType);
        transactions.saveAndFlush(transaction);
    }

    private void notifyMembers(Company company, String titleEs, String titleEn, String bodyEs, String bodyEn) {
        for (CompanyMember member : members.findByCompanyId(company.getId())) {
            User user = member.getUser();
            String title = Texts.forUser(user.getLocale(), titleEs, titleEn);
            String body = Texts.forUser(user.getLocale(), bodyEs, bodyEn);
            notifications.notify(user, NotificationType.SUBSCRIPTION, title, body, "/empresa/suscripcion");
            email.sendSubscription(user, title, body);
        }
    }

    private BigDecimal amount(JsonNode node) {
        String raw = node.path("total").asText(node.path("value").asText(""));
        if (raw.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private String currency(JsonNode node, CompanySubscription subscription) {
        String value = node.path("currency").asText(node.path("currency_code").asText(""));
        if (!value.isBlank()) {
            return value;
        }
        return subscription.getPlan().getCurrency();
    }

    private String subscriptionId(String eventType, JsonNode resource) {
        if ("PAYMENT.SALE.COMPLETED".equals(eventType)) {
            return resource.path("billing_agreement_id").asText("");
        }
        return resource.path("id").asText("");
    }

    private void requireHttp(String value) {
        if (value == null || value.length() > 255 || !(value.startsWith("https://") || value.startsWith("http://"))) {
            throw ApiException.badRequest("Los enlaces deben comenzar con http:// o https:// y tener menos de 255 caracteres.");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
