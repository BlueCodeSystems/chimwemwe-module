package com.bluecodeltd.chimwemwe.chw.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DistrictNameUtilsTest {

    @Test
    public void displayNormalizesKnownCompactDistrict() {
        assertEquals("Kapiri Mposhi", DistrictNameUtils.display("kapirimposhi"));
    }

    @Test
    public void registeredOfficialNameNormalizesAnyCompactDistrict() {
        DistrictNameUtils.registerOfficialName("Chililabombwe District");
        assertEquals("Chililabombwe District", DistrictNameUtils.display("chililabombwedistrict"));
    }

    @Test
    public void displayTitleCasesOrdinaryDistrictAndNeverUsesQuestionMarkForMissingValue() {
        assertEquals("Chongwe", DistrictNameUtils.display("chongwe"));
        assertEquals("", DistrictNameUtils.display(null));
        assertEquals("", DistrictNameUtils.display("  "));
    }
}