package com.bluecodeltd.chimwemwe.chw.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Normalizes district names for display only.
 * Keep storage and comparisons using raw values.
 */
public final class DistrictNameUtils {

    private static final Map<String, String> DISPLAY_ALIASES = new ConcurrentHashMap<>();

    static {
        DISPLAY_ALIASES.put("kapirimposhi", "Kapiri Mposhi");
        DISPLAY_ALIASES.put("sengahill", "Senga Hill");
        DISPLAY_ALIASES.put("itezhitezhi", "Itezhi-Tezhi");
    }

    /** Registers an official district label so compact/raw variants resolve dynamically. */
    public static void registerOfficialName(String district) {
        if (district == null) return;
        String official = district.trim();
        if (!official.isEmpty()) DISPLAY_ALIASES.put(normalizeKey(official), official);
    }

    private DistrictNameUtils() {}

    public static String display(String district) {
        if (district == null) return "";
        String raw = district.trim();
        if (raw.isEmpty()) return "";

        String alias = DISPLAY_ALIASES.get(normalizeKey(raw));
        if (alias != null) return alias;
        return toTitleCase(raw);
    }

    private static String normalizeKey(String value) {
        return value.trim().toLowerCase(Locale.US).replaceAll("[^a-z0-9]", "");
    }

    private static String toTitleCase(String value) {
        StringBuilder out = new StringBuilder(value.length());
        boolean startWord = true;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetter(c)) {
                out.append(startWord ? Character.toUpperCase(c) : Character.toLowerCase(c));
                startWord = false;
            } else {
                out.append(c);
                startWord = c == ' ' || c == '-' || c == '/' || c == '_';
            }
        }
        return out.toString();
    }
}
