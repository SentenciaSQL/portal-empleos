package com.empleord.config;

import com.empleord.ads.AdIds;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class ProductionGuard {

    private final AppProperties properties;

    public ProductionGuard(AppProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void verify() {
        if (!properties.isProduction()) {
            return;
        }
        if (unsafe(properties.getJwt().getSecret()) || unsafe(properties.getStorage().getSigningSecret())) {
            throw new IllegalStateException("Configura JWT_SECRET y FILE_SIGNING_SECRET antes de usar el perfil de producción.");
        }
        if (!properties.isCookieSecure()) {
            throw new IllegalStateException("COOKIE_SECURE debe ser true en producción.");
        }
        if (properties.getPaypal().isEnabled()) {
            AppProperties.Paypal paypal = properties.getPaypal();
            if (blank(paypal.getClientId()) || blank(paypal.getClientSecret()) || blank(paypal.getWebhookId())) {
                throw new IllegalStateException("PAYPAL_ENABLED exige PAYPAL_CLIENT_ID, PAYPAL_CLIENT_SECRET y PAYPAL_WEBHOOK_ID.");
            }
            if (!"live".equalsIgnoreCase(paypal.getMode())) {
                throw new IllegalStateException("En producción PAYPAL_MODE debe ser live.");
            }
        }
        AppProperties.Adsense adsense = properties.getAdsense();
        if (adsense.isEnabled() && !adsense.getPublisherId().isBlank() && !AdIds.publisher(adsense.getPublisherId())) {
            throw new IllegalStateException("ADSENSE_PUBLISHER_ID debe tener el formato ca-pub- seguido de 16 dígitos.");
        }
        if (!validSlot(adsense.getSlots().getHomeTop()) || !validSlot(adsense.getSlots().getHomeFeed())
                || !validSlot(adsense.getSlots().getJobSearchInline()) || !validSlot(adsense.getSlots().getJobDetailBottom())
                || !validSlot(adsense.getSlots().getCategoryContent()) || !validSlot(adsense.getSlots().getInfoContent())
                || !validSlot(adsense.getSlots().getResourceContent())) {
            throw new IllegalStateException("Un ADSENSE_SLOT_* definido debe ser el identificador numérico del bloque.");
        }
    }

    private boolean validSlot(String value) {
        return value == null || value.isBlank() || AdIds.unit(value);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private boolean unsafe(String secret) {
        return secret == null || secret.length() < 32 || secret.startsWith("dev-only");
    }
}
