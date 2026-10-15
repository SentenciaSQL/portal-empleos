package com.empleord.storage;

import com.empleord.common.ApiException;
import com.empleord.config.AppProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class FileSignatureService {

    private final byte[] secret;

    public FileSignatureService(AppProperties properties) {
        this.secret = properties.getStorage().getSigningSecret().getBytes(StandardCharsets.UTF_8);
    }

    public String sign(UUID resumeId, UUID userId, Duration ttl) {
        long exp = Instant.now().plus(ttl).getEpochSecond();
        String payload = resumeId + "|" + userId + "|" + exp;
        String data = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return data + "." + hmac(data);
    }

    public SignedAccess parse(String token) {
        if (token == null || !token.contains(".")) {
            throw ApiException.unauthorized("El enlace del archivo no es válido.");
        }
        String[] parts = token.split("\\.", 2);
        if (!hmac(parts[0]).equals(parts[1])) {
            throw ApiException.unauthorized("El enlace del archivo no es válido.");
        }
        String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        String[] fields = payload.split("\\|");
        if (fields.length != 3) {
            throw ApiException.unauthorized("El enlace del archivo no es válido.");
        }
        long exp = Long.parseLong(fields[2]);
        if (Instant.now().getEpochSecond() > exp) {
            throw ApiException.unauthorized("El enlace del archivo expiró.");
        }
        return new SignedAccess(UUID.fromString(fields[0]), UUID.fromString(fields[1]));
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    public record SignedAccess(UUID resumeId, UUID userId) {
    }
}
