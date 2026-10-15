package com.empleord.billing;

import com.empleord.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UnconfiguredPaymentGateway implements PaymentGateway {

    @Override
    public PaymentSession startSubscriptionCheckout(UUID companyId, String planCode, String returnUrl) {
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "La pasarela de pago todavía no está configurada.");
    }
}
