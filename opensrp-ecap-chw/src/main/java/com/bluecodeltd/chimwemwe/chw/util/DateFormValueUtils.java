package com.bluecodeltd.chimwemwe.chw.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Locale;

public final class DateFormValueUtils {

    private static final String[] INPUT_PATTERNS = {
            "uuuu-M-d", "d-M-uuuu", "d/M/uuuu", "uuuu/M/d",
            "M/d/uuuu", "d.M.uuuu", "uuuu.M.d", "d MMM uuuu"
    };

    private DateFormValueUtils() {
    }

    public static String normalizeForDatePicker(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        if (value.isEmpty() || value.equalsIgnoreCase("null")) {
            return "";
        }

        int timeSeparator = value.indexOf('T');
        if (timeSeparator > 0) {
            value = value.substring(0, timeSeparator);
        } else if (value.matches("(?:\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}|\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{4})\\s+.*")) {
            value = value.substring(0, value.indexOf(' '));
        }

        LocalDate date = parse(value, Locale.getDefault());
        if (date == null && !Locale.ENGLISH.equals(Locale.getDefault())) {
            date = parse(value, Locale.ENGLISH);
        }
        if (date != null) {
            return date.format(DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.getDefault()));
        }
        return value;
    }

    private static LocalDate parse(String value, Locale locale) {
        for (String pattern : INPUT_PATTERNS) {
            try {
                DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                        .parseCaseInsensitive()
                        .appendPattern(pattern)
                        .toFormatter(locale);
                return LocalDate.parse(value, formatter);
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
