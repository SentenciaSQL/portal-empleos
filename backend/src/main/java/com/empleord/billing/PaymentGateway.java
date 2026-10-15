package com.empleord.billing;

import java.util.UUID;

/**
 * Punto de integración para una pasarela compatible con cobros en República Dominicana.
 * No hay cobro real en esta versión: el administrador asigna planes desde el panel.
 */
public interface PaymentGateway {

    PaymentSession startSubscriptionCheckout(UUID companyId, String planCode, String returnUrl);

    record PaymentSession(String provider, String redirectUrl) {
    }
}
