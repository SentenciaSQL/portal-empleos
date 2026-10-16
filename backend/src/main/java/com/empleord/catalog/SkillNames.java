package com.empleord.catalog;

import com.empleord.common.ApiException;

import java.util.Locale;

public final class SkillNames {

    public static final int MAX_LENGTH = 80;

    private SkillNames() {
    }

    public static String collapse(String raw) {
        if (raw == null) {
            return null;
        }
        String collapsed = raw.replace('\u0000', ' ').trim().replaceAll("\\s+", " ");
        return collapsed.isEmpty() ? null : collapsed;
    }

    public static String key(String display) {
        return display == null ? null : display.toLowerCase(Locale.ROOT);
    }

    public static String required(String raw) {
        String display = collapse(raw);
        if (display == null || display.codePoints().noneMatch(Character::isLetterOrDigit)) {
            throw ApiException.badRequest("Escribe el nombre de la habilidad.");
        }
        if (display.length() < 2) {
            throw ApiException.badRequest("La habilidad debe tener al menos 2 caracteres.");
        }
        if (display.length() > MAX_LENGTH) {
            throw ApiException.badRequest("El nombre de la habilidad no puede superar 80 caracteres.");
        }
        if (display.codePoints().anyMatch(code -> code == '<' || code == '>' || Character.isISOControl(code))) {
            throw ApiException.badRequest("El nombre de la habilidad contiene caracteres no permitidos.");
        }
        return display;
    }
}
