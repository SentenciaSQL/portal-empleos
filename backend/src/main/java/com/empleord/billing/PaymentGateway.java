package com.empleord.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Abstracción de cobro. La lógica comercial no depende de un proveedor concreto.
 * No se almacenan números de tarjeta ni códigos de seguridad.
 */
public interface PaymentGateway {

    boolean configured();

    String provider();

    PaymentSession startSubscriptionCheckout(CheckoutRequest request);

    void cancelSubscription(String providerSubscriptionId);

    Optional<Instant> subscriptionAccessUntil(String providerSubscriptionId);

    record CheckoutRequest(
            UUID companyId,
            String planName,
            BigDecimal amount,
            String currency,
            String returnUrl,
            String cancelUrl
    ) {
    }

    record PaymentSession(String provider, String providerSubscriptionId, String providerPlanId, String redirectUrl) {
    }
}
