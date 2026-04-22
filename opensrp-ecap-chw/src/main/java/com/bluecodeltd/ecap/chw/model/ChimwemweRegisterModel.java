package com.bluecodeltd.ecap.chw.model;

import static com.vijay.jsonwizard.utils.FormUtils.fields;
import static com.vijay.jsonwizard.utils.FormUtils.getFieldJSONObject;
import static org.smartregister.util.JsonFormUtils.ENCOUNTER_LOCATION;

import androidx.annotation.Nullable;

import com.bluecodeltd.ecap.chw.contract.ChimwemweRegisterContract;

import org.json.JSONArray;
import org.json.JSONObject;
import org.smartregister.domain.tag.FormTag;
import org.smartregister.opd.utils.OpdUtils;

import java.time.LocalDate;
import java.util.Random;

import timber.log.Timber;

public class ChimwemweRegisterModel implements ChimwemweRegisterContract.Model {

    @Nullable
    @Override
    public ChimwemweRegisterContract.RegistrationData processRegistration(String jsonString,
                                                                         FormTag formTag) {
        try {
            JSONObject form = new JSONObject(jsonString);
            HotspotGroupModel group = buildGroupModel(form);
            if (group == null) return null;
            return new ChimwemweRegisterContract.RegistrationData(form, group);
        } catch (Exception e) {
            Timber.e(e, "ChimwemweRegisterModel: processRegistration");
            return null;
        }
    }

    @Nullable
    @Override
    public JSONObject getFormAsJson(String formName, String entityId, String currentLocationId) {
        try {
            JSONObject form = OpdUtils.getJsonFormToJsonObject(formName);
            if (form == null) {
                return null;
            }

            JSONObject metadata = form.optJSONObject("metadata");
            if (metadata != null) {
                metadata.put(ENCOUNTER_LOCATION, currentLocationId);
            }

            return form;
        } catch (Exception e) {
            Timber.e(e, "ChimwemweRegisterModel: getFormAsJson");
            return null;
        }
    }

    private HotspotGroupModel buildGroupModel(JSONObject form) {
        JSONObject step1 = form.optJSONObject("step1");
        JSONObject step2 = form.optJSONObject("step2");
        JSONObject step3 = form.optJSONObject("step3");

        String hotspotName = fieldValue(step1, "hotspot_name");
        String groupName = fieldValue(step1, "group_name");
        if (hotspotName.isEmpty() || groupName.isEmpty()) return null;

        HotspotGroupModel group = new HotspotGroupModel();
        group.setGroupCode(generateGroupCode());
        group.setHotspotName(hotspotName);
        group.setGroupName(groupName);
        group.setProvince(fieldValue(step1, "province"));
        group.setDistrict(fieldValue(step1, "district"));
        group.setLocationOfSession(fieldValue(step1, "location_of_session"));
        group.setLocationGps(fieldValue(step1, "location_gps"));
        group.setNearestHealthFacility(fieldValue(step1, "nearest_health_facility"));
        group.setCreatedDate(LocalDate.now().toString());

        group.setFacilitator1FirstName(fieldValue(step2, "facilitator_1_first_name"));
        group.setFacilitator1Surname(fieldValue(step2, "facilitator_1_surname"));
        group.setFacilitator2FirstName(fieldValue(step2, "facilitator_2_first_name"));
        group.setFacilitator2Surname(fieldValue(step2, "facilitator_2_surname"));

        group.setSession1Date(fieldValue(step3, "session_1_date"));
        group.setSession2Date(fieldValue(step3, "session_2_date"));
        group.setSession3Date(fieldValue(step3, "session_3_date"));
        group.setSession4Date(fieldValue(step3, "session_4_date"));
        group.setSession5Date(fieldValue(step3, "session_5_date"));
        group.setSession6Date(fieldValue(step3, "session_6_date"));
        group.setSession7Date(fieldValue(step3, "session_7_date"));
        group.setSession8Date(fieldValue(step3, "session_8_date"));
        group.setSession9Date(fieldValue(step3, "session_9_date"));
        group.setSession10Date(fieldValue(step3, "session_10_date"));
        group.setSession11Date(fieldValue(step3, "session_11_date"));
        group.setSession12Date(fieldValue(step3, "session_12_date"));
        group.setSession13Date(fieldValue(step3, "session_13_date"));
        group.setSession14Date(fieldValue(step3, "session_14_date"));

        return group;
    }

    private static String generateGroupCode() {
        return String.format("CHM%07d", new Random().nextInt(10_000_000));
    }

    private String fieldValue(JSONObject step, String key) {
        if (step == null) return "";
        try {
            JSONArray stepFields = step.optJSONArray("fields");
            if (stepFields == null) return "";
            for (int i = 0; i < stepFields.length(); i++) {
                JSONObject field = stepFields.getJSONObject(i);
                if (key.equals(field.optString("key"))) {
                    String value = field.optString("value", "").trim();
                    return "null".equals(value) ? "" : value;
                }
            }
        } catch (Exception ignored) {}
        return "";
    }
}
