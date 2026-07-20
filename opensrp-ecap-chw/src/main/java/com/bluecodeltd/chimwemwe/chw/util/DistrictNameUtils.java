package com.bluecodeltd.chimwemwe.chw.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Normalizes district names for display only.
 * Keep storage and comparisons using raw values.
 */
public final class DistrictNameUtils {

    private static final Map<String, String> CANONICAL_ALIASES;
    private static final Map<String, String> REGISTERED_NAMES = new ConcurrentHashMap<>();

    static {
        Map<String, String> aliases = new HashMap<>();
        aliases.put("kapirimposhi", "Kapiri Mposhi");
        aliases.put("sengahill", "Senga Hill");
        aliases.put("itezhitezhi", "Itezhi-Tezhi");
        CANONICAL_ALIASES = Collections.unmodifiableMap(aliases);
    }

    /** Registers an official district label so compact/raw variants resolve dynamically. */
    public static void registerOfficialName(String district) {
        if (district == null) return;
        String official = district.trim();
        if (official.isEmpty()) return;

        String key = normalizeKey(official);
        if (!CANONICAL_ALIASES.containsKey(key)) {
            REGISTERED_NAMES.putIfAbsent(key, official);
        }
    }

    private DistrictNameUtils() {}

    public static String display(String district) {
        if (district == null) return "";
        String raw = district.trim();
        if (raw.isEmpty()) return "";

        String key = normalizeKey(raw);
        String canonical = CANONICAL_ALIASES.get(key);
        if (canonical != null) return canonical;

        String registered = REGISTERED_NAMES.get(key);
        if (registered != null) return registered;

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