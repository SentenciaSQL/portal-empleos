package com.empleord.config;

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
    }

    private boolean unsafe(String secret) {
        return secret == null || secret.length() < 32 || secret.startsWith("dev-only");
    }
}
