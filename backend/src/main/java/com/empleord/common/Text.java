package com.empleord.common;

import java.text.Normalizer;
import java.util.Locale;

public final class Text {

    private Text() {
    }

    public static String clean(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.replaceAll("(?is)<[^>]*>", "").replace('\u0000', ' ').trim();
        return stripped.isEmpty() ? null : stripped;
    }

    public static String required(String value, String label) {
        String cleaned = clean(value);
        if (cleaned == null) {
            throw ApiException.badRequest(label + " es obligatorio.");
        }
        return cleaned;
    }

    public static String url(String value) {
        String cleaned = clean(value);
        if (cleaned == null) {
            return null;
        }
        if (!(cleaned.startsWith("https://") || cleaned.startsWith("http://")) || cleaned.length() > 255) {
            throw ApiException.badRequest("Los enlaces deben comenzar con http:// o https:// y tener menos de 255 caracteres.");
        }
        return cleaned;
    }

    public static String slug(String value) {
        String base = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (base.isBlank()) {
            base = "item";
        }
        return base.length() > 70 ? base.substring(0, 70).replaceAll("-$", "") : base;
    }
}
