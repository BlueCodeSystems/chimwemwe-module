package com.bluecodeltd.chimwemwe.chw.util;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

public class ChimwemweFormUtilsTest {

    @Test
    public void ensureFieldValue_addsToStep1WhenMissing() throws Exception {
        JSONObject form = new JSONObject()
                .put("step1", new JSONObject()
                        .put("fields", new JSONArray()
                                .put(new JSONObject().put("key", "caregiver_first_name").put("value", "Jane"))));

        ChimwemweFormUtils.ensureFieldValue(form, "group_id", "G-123");

        JSONArray step1Fields = form.getJSONObject("step1").getJSONArray("fields");
        boolean found = false;
        for (int i = 0; i < step1Fields.length(); i++) {
            JSONObject field = step1Fields.getJSONObject(i);
            if ("group_id".equals(field.optString("key"))) {
                Assert.assertEquals("G-123", field.optString("value"));
                found = true;
                break;
            }
        }
        Assert.assertTrue(found);
    }

    @Test
    public void ensureFieldValue_updatesExistingFieldInAnyStep() throws Exception {
        JSONObject form = new JSONObject()
                .put("step1", new JSONObject().put("fields", new JSONArray()))
                .put("step2", new JSONObject()
                        .put("fields", new JSONArray()
                                .put(new JSONObject().put("key", "group_id").put("value", "OLD"))));

        ChimwemweFormUtils.ensureFieldValue(form, "group_id", "NEW");

        JSONArray step2Fields = form.getJSONObject("step2").getJSONArray("fields");
        Assert.assertEquals("NEW", step2Fields.getJSONObject(0).optString("value"));
    }
}

