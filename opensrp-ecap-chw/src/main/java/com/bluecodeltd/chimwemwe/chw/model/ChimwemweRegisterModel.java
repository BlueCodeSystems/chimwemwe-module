package com.bluecodeltd.chimwemwe.chw.model;

import static org.smartregister.util.JsonFormUtils.ENCOUNTER_LOCATION;

import androidx.annotation.Nullable;

import com.bluecodeltd.chimwemwe.chw.contract.ChimwemweRegisterContract;

import org.json.JSONArray;
import org.json.JSONObject;
import org.smartregister.domain.tag.FormTag;
import org.smartregister.opd.utils.OpdUtils;

import java.time.LocalDate;
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

        String hotspotName = fieldValue(step1, "hotspot_name");
        String groupName = fieldValue(step1, "group_name");
        if (hotspotName.isEmpty() || groupName.isEmpty()) return null;

        HotspotGroupModel group = new HotspotGroupModel();
        String groupIdStr = fieldValue(step1, "group_id");
        group.setGroupId(groupIdStr);
        group.setHotspotName(hotspotName);
        group.setGroupName(groupName);
        group.setProvince(fieldValue(step1, "province"));
        group.setDistrict(fieldValue(step1, "district"));
        group.setLocationOfSession(fieldValue(step1, "location_of_session"));
        group.setLocationGps(fieldValue(step1, "location_gps"));
        group.setNearestHealthFacility(fieldValue(step1, "nearest_health_facility"));
        group.setCreatedDate(LocalDate.now().toString());

        group.setFacilitatorName1(fieldValue(step1, "facilitator_name_1"));
        group.setFacilitatorName2(fieldValue(step1, "facilitator_name_2"));

        return group;
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
