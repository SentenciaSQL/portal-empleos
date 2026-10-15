package com.empleord.billing;

import com.empleord.common.ApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
@ConditionalOnProperty(prefix = "app.paypal", name = "enabled", havingValue = "false", matchIfMissing = true)
public class UnconfiguredPaymentGateway implements PaymentGateway {

    @Override
    public boolean configured() {
        return false;
    }

    @Override
    public String provider() {
        return "NONE";
    }

    @Override
    public PaymentSession startSubscriptionCheckout(CheckoutRequest request) {
        throw unavailable();
    }

    @Override
    public void cancelSubscription(String providerSubscriptionId) {
        throw unavailable();
    }

    @Override
    public Optional<Instant> subscriptionAccessUntil(String providerSubscriptionId) {
        return Optional.empty();
    }

    private ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "La pasarela de pago todavía no está configurada.");
    }
}
