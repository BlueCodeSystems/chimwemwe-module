package com.bluecodeltd.chimwemwe.chw.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;

public class DateFormValueUtilsTest {

    private Locale originalLocale;

    @Before
    public void setUp() {
        originalLocale = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
    }

    @After
    public void tearDown() {
        Locale.setDefault(originalLocale);
    }

    @Test
    public void normalizeForDatePickerConvertsSupportedStoredFormats() {
        assertEquals("13 Jul 2026", DateFormValueUtils.normalizeForDatePicker("2026-07-13"));
        assertEquals("13 Jul 2026", DateFormValueUtils.normalizeForDatePicker("13-07-2026"));
        assertEquals("13 Jul 2026", DateFormValueUtils.normalizeForDatePicker("2026-07-13T10:20:30Z"));
        assertEquals("13 Jul 2026", DateFormValueUtils.normalizeForDatePicker("2026-07-13 10:20:30"));
    }

    @Test
    public void normalizeForDatePickerPreservesDisplayDate() {
        assertEquals("13 Jul 2026", DateFormValueUtils.normalizeForDatePicker("13 Jul 2026"));
    }

    @Test
    public void normalizeForDatePickerHandlesMissingValues() {
        assertEquals("", DateFormValueUtils.normalizeForDatePicker(null));
        assertEquals("", DateFormValueUtils.normalizeForDatePicker(" null "));
    }
}
