package com.empleord.billing;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments/paypal")
public class PayPalWebhookController {

    private final BillingService billing;

    public PayPalWebhookController(BillingService billing) {
        this.billing = billing;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestHeader(value = "Paypal-Transmission-Id", required = false) String transmissionId,
            @RequestHeader(value = "Paypal-Transmission-Time", required = false) String transmissionTime,
            @RequestHeader(value = "Paypal-Transmission-Sig", required = false) String transmissionSig,
            @RequestHeader(value = "Paypal-Cert-Url", required = false) String certUrl,
            @RequestHeader(value = "Paypal-Auth-Algo", required = false) String authAlgo,
            @RequestBody String body) {
        billing.handlePayPalWebhook(transmissionId, transmissionTime, transmissionSig, certUrl, authAlgo, body);
        return ResponseEntity.ok().build();
    }
}
