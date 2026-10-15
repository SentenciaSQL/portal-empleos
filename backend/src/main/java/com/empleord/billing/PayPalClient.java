package com.empleord.billing;

import com.empleord.common.ApiException;
import com.empleord.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;

@Component
@ConditionalOnProperty(prefix = "app.paypal", name = "enabled", havingValue = "true")
public class PayPalClient {

    private static final Logger log = LoggerFactory.getLogger(PayPalClient.class);
    private static final Set<String> CERT_HOSTS = Set.of(
            "api.paypal.com", "api.sandbox.paypal.com", "api-m.paypal.com", "api-m.sandbox.paypal.com");

    private final AppProperties properties;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final Object tokenLock = new Object();
    private String accessToken;
    private Instant accessTokenExpiresAt = Instant.EPOCH;

    public PayPalClient(AppProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
    }

    public boolean credentialsPresent() {
        AppProperties.Paypal paypal = properties.getPaypal();
        return paypal != null && !blank(paypal.getClientId()) && !blank(paypal.getClientSecret()) && !blank(paypal.getWebhookId());
    }

    public JsonNode createProduct() {
        ObjectNode body = json.createObjectNode();
        body.put("name", "EmpleoRD");
        body.put("description", "Planes de empresas en EmpleoRD");
        body.put("type", "SERVICE");
        body.put("category", "SOFTWARE");
        return post("/v1/catalogs/products", body);
    }

    public JsonNode createMonthlyPlan(String productId, String name, BigDecimal amount, String currency) {
        ObjectNode price = json.createObjectNode();
        price.put("value", amount.setScale(2, RoundingMode.HALF_UP).toPlainString());
        price.put("currency_code", currency);
        ObjectNode pricing = json.createObjectNode();
        pricing.set("fixed_price", price);
        ObjectNode frequency = json.createObjectNode();
        frequency.put("interval_unit", "MONTH");
        frequency.put("interval_count", 1);
        ObjectNode cycle = json.createObjectNode();
        cycle.set("frequency", frequency);
        cycle.put("tenure_type", "REGULAR");
        cycle.put("sequence", 1);
        cycle.put("total_cycles", 0);
        cycle.set("pricing_scheme", pricing);
        ObjectNode preferences = json.createObjectNode();
        preferences.put("auto_bill_outstanding", true);
        preferences.put("payment_failure_threshold", 1);
        ObjectNode body = json.createObjectNode();
        body.put("product_id", productId);
        body.put("name", name);
        body.put("description", name);
        body.putArray("billing_cycles").add(cycle);
        body.set("payment_preferences", preferences);
        return post("/v1/billing/plans", body);
    }

    public JsonNode createSubscription(String planId, String companyId, String returnUrl, String cancelUrl) {
        ObjectNode context = json.createObjectNode();
        context.put("brand_name", "EmpleoRD");
        context.put("locale", "es-DO");
        context.put("shipping_preference", "NO_SHIPPING");
        context.put("user_action", "SUBSCRIBE_NOW");
        context.put("return_url", returnUrl);
        context.put("cancel_url", cancelUrl);
        ObjectNode body = json.createObjectNode();
        body.put("plan_id", planId);
        body.put("custom_id", companyId);
        body.set("application_context", context);
        return post("/v1/billing/subscriptions", body);
    }

    public void cancelSubscription(String subscriptionId) {
        ObjectNode body = json.createObjectNode();
        body.put("reason", "Cancelado por la empresa en EmpleoRD");
        post("/v1/billing/subscriptions/" + encode(subscriptionId) + "/cancel", body);
    }

    public JsonNode subscription(String subscriptionId) {
        return get("/v1/billing/subscriptions/" + encode(subscriptionId));
    }

    public void verifyWebhook(String transmissionId, String transmissionTime, String transmissionSig, String certUrl, String authAlgo, String rawEvent) {
        if (blank(transmissionId) || blank(transmissionTime) || blank(transmissionSig) || blank(certUrl) || blank(authAlgo)) {
            throw ApiException.badRequest("La firma del webhook no es válida.");
        }
        assertPayPalCertificate(certUrl);
        ObjectNode body = json.createObjectNode();
        body.put("auth_algo", authAlgo);
        body.put("cert_url", certUrl);
        body.put("transmission_id", transmissionId);
        body.put("transmission_sig", transmissionSig);
        body.put("transmission_time", transmissionTime);
        body.put("webhook_id", properties.getPaypal().getWebhookId());
        try {
            body.set("webhook_event", json.readTree(rawEvent));
        } catch (Exception ex) {
            throw ApiException.badRequest("La firma del webhook no es válida.");
        }
        JsonNode response = post("/v1/notifications/verify-webhook-signature", body);
        if (!"SUCCESS".equals(response.path("verification_status").asText())) {
            throw ApiException.badRequest("La firma del webhook no es válida.");
        }
    }

    private void assertPayPalCertificate(String certUrl) {
        URI uri;
        try {
            uri = URI.create(certUrl);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("La firma del webhook no es válida.");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !CERT_HOSTS.contains(host)) {
            throw ApiException.badRequest("La firma del webhook no es válida.");
        }
    }

    private JsonNode get(String path) {
        return exchange("GET", path, null);
    }

    private JsonNode post(String path, JsonNode body) {
        return exchange("POST", path, body);
    }

    private JsonNode exchange(String method, String path, JsonNode body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base() + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + token())
                    .header("Content-Type", "application/json");
            if ("POST".equals(method)) {
                builder.POST(HttpRequest.BodyPublishers.ofString(body == null ? "{}" : json.writeValueAsString(body)));
            } else {
                builder.GET();
            }
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 204) {
                return json.createObjectNode();
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("PayPal respondió {} en {}", response.statusCode(), path);
                throw new ApiException(HttpStatus.BAD_GATEWAY, "PayPal rechazó la solicitud de cobro.");
            }
            if (response.body() == null || response.body().isBlank()) {
                return json.createObjectNode();
            }
            return json.readTree(response.body());
        } catch (ApiException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo contactar a PayPal.");
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo contactar a PayPal.");
        }
    }

    private String token() {
        synchronized (tokenLock) {
            if (accessToken != null && Instant.now().isBefore(accessTokenExpiresAt)) {
                return accessToken;
            }
            try {
                String basic = Base64.getEncoder().encodeToString((properties.getPaypal().getClientId() + ":" + properties.getPaypal().getClientSecret()).getBytes(StandardCharsets.UTF_8));
                HttpRequest request = HttpRequest.newBuilder(URI.create(base() + "/v1/oauth2/token"))
                        .timeout(Duration.ofSeconds(20))
                        .header("Authorization", "Basic " + basic)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
                        .build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new ApiException(HttpStatus.BAD_GATEWAY, "PayPal rechazó la solicitud de cobro.");
                }
                JsonNode node = json.readTree(response.body());
                accessToken = node.path("access_token").asText(null);
                int seconds = node.path("expires_in").asInt(300);
                accessTokenExpiresAt = Instant.now().plusSeconds(Math.max(60, seconds - 60));
                if (blank(accessToken)) {
                    throw new ApiException(HttpStatus.BAD_GATEWAY, "PayPal rechazó la solicitud de cobro.");
                }
                return accessToken;
            } catch (ApiException ex) {
                throw ex;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo contactar a PayPal.");
            } catch (Exception ex) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo contactar a PayPal.");
            }
        }
    }

    private String base() {
        return "live".equalsIgnoreCase(properties.getPaypal().getMode()) ? "https://api-m.paypal.com" : "https://api-m.sandbox.paypal.com";
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
