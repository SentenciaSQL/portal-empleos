package com.empleord.ads;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AdIds {

    private static final Pattern PUBLISHER = Pattern.compile("ca-pub-(\\d{16})");
    private static final Pattern UNIT = Pattern.compile("\\d{8,12}");

    private AdIds() {
    }

    public static boolean publisher(String value) {
        if (value == null) {
            return false;
        }
        Matcher matcher = PUBLISHER.matcher(value.trim());
        return matcher.matches() && !zeros(matcher.group(1));
    }

    public static boolean unit(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        return UNIT.matcher(trimmed).matches() && !zeros(trimmed);
    }

    static String normalizePublisher(String value) {
        return value == null ? "" : value.trim();
    }

    static String normalizeUnit(String value) {
        return value == null ? "" : value.trim();
    }

    static String adsTxtPublisher(String publisherId) {
        return publisherId.substring(3);
    }

    private static boolean zeros(String digits) {
        return digits.chars().allMatch(ch -> ch == '0');
    }
}
