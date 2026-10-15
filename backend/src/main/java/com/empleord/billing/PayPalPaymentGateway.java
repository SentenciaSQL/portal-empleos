package com.empleord.billing;

import com.empleord.common.ApiException;
import com.empleord.domain.entity.PaymentProviderRef;
import com.empleord.domain.repository.PaymentProviderRefRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;

@Component
@ConditionalOnProperty(prefix = "app.paypal", name = "enabled", havingValue = "true")
public class PayPalPaymentGateway implements PaymentGateway {

    private final PayPalClient paypal;
    private final PaymentProviderRefRepository refs;

    public PayPalPaymentGateway(PayPalClient paypal, PaymentProviderRefRepository refs) {
        this.paypal = paypal;
        this.refs = refs;
    }

    @Override
    public boolean configured() {
        return paypal.credentialsPresent();
    }

    @Override
    public String provider() {
        return "PAYPAL";
    }

    @Override
    public PaymentSession startSubscriptionCheckout(CheckoutRequest request) {
        if (!configured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Faltan credenciales de PayPal.");
        }
        String productId = refs.findById(new PaymentProviderRef.Key("PAYPAL", "PRODUCT"))
                .map(PaymentProviderRef::getRefValue)
                .orElseGet(this::storeProduct);
        JsonNode plan = paypal.createMonthlyPlan(productId, request.planName(), request.amount(), request.currency());
        String planId = plan.path("id").asText("");
        if (planId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PayPal rechazó la solicitud de cobro.");
        }
        JsonNode subscription = paypal.createSubscription(planId, request.companyId().toString(), request.returnUrl(), request.cancelUrl());
        String subscriptionId = subscription.path("id").asText("");
        String approval = approvalUrl(subscription);
        if (subscriptionId.isBlank() || approval.isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PayPal rechazó la solicitud de cobro.");
        }
        return new PaymentSession("PAYPAL", subscriptionId, planId, approval);
    }

    @Override
    public void cancelSubscription(String providerSubscriptionId) {
        if (!configured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Faltan credenciales de PayPal.");
        }
        paypal.cancelSubscription(providerSubscriptionId);
    }

    @Override
    public Optional<Instant> subscriptionAccessUntil(String providerSubscriptionId) {
        if (!configured() || providerSubscriptionId == null || providerSubscriptionId.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode subscription = paypal.subscription(providerSubscriptionId);
            String raw = subscription.path("billing_info").path("next_billing_time").asText("");
            if (raw.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(Instant.parse(raw));
        } catch (DateTimeParseException | ApiException ex) {
            return Optional.empty();
        }
    }

    private String storeProduct() {
        String productId = paypal.createProduct().path("id").asText("");
        if (productId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PayPal rechazó la solicitud de cobro.");
        }
        PaymentProviderRef ref = new PaymentProviderRef();
        ref.setId(new PaymentProviderRef.Key("PAYPAL", "PRODUCT"));
        ref.setRefValue(productId);
        refs.save(ref);
        return productId;
    }

    private String approvalUrl(JsonNode subscription) {
        for (JsonNode link : subscription.path("links")) {
            if ("approve".equals(link.path("rel").asText())) {
                return link.path("href").asText("");
            }
        }
        return "";
    }
}
