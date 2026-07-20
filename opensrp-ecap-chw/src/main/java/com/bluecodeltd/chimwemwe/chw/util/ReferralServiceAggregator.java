package com.bluecodeltd.chimwemwe.chw.util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ReferralServiceAggregator {

    private ReferralServiceAggregator() {}

    public static String aggregate(JSONObject form) {
        List<String> selected = new ArrayList<>();
        if (form == null) return "";
        java.util.Iterator<String> keys = form.keys();
        while (keys.hasNext()) {
            String stepKey = keys.next();
            if (stepKey == null || !stepKey.startsWith("step")) continue;
            JSONObject step = form.optJSONObject(stepKey);
            if (step == null) continue;
            JSONArray fields = step.optJSONArray("fields");
            if (fields == null) continue;
            for (int i = 0; i < fields.length(); i++) {
                JSONObject field = fields.optJSONObject(i);
                if (field == null) continue;
                String key = field.optString("key", "");
                if (!isReferralServiceField(key)) continue;

                if (key.endsWith("_other_specify")) {
                    String value = field.optString("value", "").trim();
                    if (!value.isEmpty()) selected.add(sectionName(key) + ": Other - " + value);
                    continue;
                }

                List<String> states = selectedCheckboxLabels(field);
                if (!states.isEmpty()) {
                    String label = field.optString("label", key).trim();
                    selected.add(sectionName(key) + ": " + label + " [" + join(states) + "]");
                }
            }
        }
        return join(selected);
    }

    private static boolean isReferralServiceField(String key) {
        if (key == null) return false;
        if (!(key.startsWith("caseworker_") || key.startsWith("external_"))) return false;
        return !key.endsWith("_label");
    }

    private static List<String> selectedCheckboxLabels(JSONObject field) {
        List<String> selected = new ArrayList<>();
        String rawValue = field.optString("value", "").toLowerCase(Locale.US);
        JSONArray options = field.optJSONArray("options");
        if (options == null) return selected;
        for (int i = 0; i < options.length(); i++) {
            JSONObject option = options.optJSONObject(i);
            if (option == null) continue;
            String optionKey = option.optString("key", "");
            String optionText = option.optString("text", optionKey);
            boolean checked = option.optBoolean("value", false)
                    || (!optionKey.isEmpty() && rawValue.contains(optionKey.toLowerCase(Locale.US)))
                    || (!optionText.isEmpty() && rawValue.contains(optionText.toLowerCase(Locale.US)));
            if (checked) selected.add(optionText);
        }
        return selected;
    }

    private static String sectionName(String key) {
        String source = key.startsWith("caseworker_") ? "Caseworker" : "External Person";
        String domain = "Service";
        String lower = key.toLowerCase(Locale.US);
        if (lower.contains("_healthy_")) domain = "HEALTHY";
        else if (lower.contains("_schooled_")) domain = "SCHOOLED";
        else if (lower.contains("_safe_")) domain = "SAFE";
        else if (lower.contains("_stable_")) domain = "STABLE";
        else if (lower.contains("_hh_")) domain = "Other HH Level Services";
        return source + " - " + domain;
    }

    private static String join(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) continue;
            if (out.length() > 0) out.append("; ");
            out.append(value.trim());
        }
        return out.toString();
    }
}
