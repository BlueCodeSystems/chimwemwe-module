package com.bluecodeltd.ecap.chw.util;

import static com.bluecodeltd.ecap.chw.util.IndexClientsUtils.getAllSharedPreferences;
import static com.bluecodeltd.ecap.chw.util.IndexClientsUtils.getFormTag;
import static com.bluecodeltd.ecap.chw.util.JsonFormUtils.tagSyncMetadata;
import static org.smartregister.chw.fp.util.FpUtil.getClientProcessorForJava;

import androidx.annotation.Nullable;

import com.bluecodeltd.ecap.chw.application.ChwApplication;

import org.json.JSONArray;
import org.json.JSONObject;
import org.smartregister.clientandeventmodel.Client;
import org.smartregister.clientandeventmodel.Event;
import org.smartregister.domain.db.EventClient;
import org.smartregister.domain.tag.FormTag;
import org.smartregister.repository.AllSharedPreferences;
import org.smartregister.sync.helper.ECSyncHelper;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import timber.log.Timber;

public class ChimwemweFormUtils {

    private ChimwemweFormUtils() {
    }

    public static class ProcessedForm {
        private final String entityId;
        private final Event event;
        private final Client client;

        public ProcessedForm(String entityId, Event event, Client client) {
            this.entityId = entityId;
            this.event = event;
            this.client = client;
        }

        public String getEntityId() {
            return entityId;
        }

        public Event getEvent() {
            return event;
        }

        public Client getClient() {
            return client;
        }
    }

    @Nullable
    public static ProcessedForm processRegistration(JSONObject form, String bindType, String entityId) {
        try {
            if (form == null || bindType == null || bindType.trim().isEmpty()) {
                return null;
            }

            // Do not override an existing entity_id (baseEntityId). For edits, the form should
            // already carry the original entity_id; changing it creates a new Client record.
            String resolvedEntityId = form.optString("entity_id", "").trim();
            if (resolvedEntityId == null || resolvedEntityId.trim().isEmpty()) {
                resolvedEntityId = entityId;
            }
            if (resolvedEntityId == null || resolvedEntityId.trim().isEmpty()) {
                resolvedEntityId = org.smartregister.util.JsonFormUtils.generateRandomUUIDString();
            }
            form.put("entity_id", resolvedEntityId);

            JSONArray fields = org.smartregister.util.JsonFormUtils.fields(form);
            JSONObject metadata = form.optJSONObject("metadata");
            String encounterType = form.optString("encounter_type", "");
            if (fields == null || metadata == null || encounterType.isEmpty()) {
                return null;
            }

            FormTag formTag = getFormTag();
            Event event = org.smartregister.util.JsonFormUtils.createEvent(
                    fields, metadata, formTag, resolvedEntityId, encounterType, bindType);
            tagSyncMetadata(event);
            Client client = org.smartregister.util.JsonFormUtils.createBaseClient(fields, formTag, resolvedEntityId);
            return new ProcessedForm(resolvedEntityId, event, client);
        } catch (Exception e) {
            Timber.e(e, "ChimwemweFormUtils.processRegistration failed for %s", bindType);
            return null;
        }
    }

    public static boolean saveRegistration(ProcessedForm processedForm, boolean isEditMode) {
        if (processedForm == null || processedForm.getEvent() == null || processedForm.getClient() == null) {
            return false;
        }

        try {
            ECSyncHelper syncHelper = ChwApplication.getInstance().getEcSyncHelper();
            JSONObject newClientJsonObject = new JSONObject(
                    org.smartregister.util.JsonFormUtils.gson.toJson(processedForm.getClient()));

            if (isEditMode) {
                JSONObject existingClientJsonObject = null;
                try {
                    existingClientJsonObject = syncHelper.getClient(processedForm.getEntityId());
                } catch (Exception ignored) {
                }

                if (existingClientJsonObject != null) {
                    JSONObject mergedClientJsonObject =
                            org.smartregister.util.JsonFormUtils.merge(existingClientJsonObject, newClientJsonObject);
                    syncHelper.addClient(processedForm.getEntityId(), mergedClientJsonObject);
                } else {
                    syncHelper.addClient(processedForm.getEntityId(), newClientJsonObject);
                }
            } else {
                syncHelper.addClient(processedForm.getEntityId(), newClientJsonObject);
            }

            JSONObject eventJsonObject = new JSONObject(
                    org.smartregister.util.JsonFormUtils.gson.toJson(processedForm.getEvent()));
            syncHelper.addEvent(processedForm.getEvent().getBaseEntityId(), eventJsonObject);

            AllSharedPreferences prefs = getAllSharedPreferences();
            Date currentSyncDate = new Date(prefs.fetchLastUpdatedAtDate(0));
            List<EventClient> savedEvents = syncHelper.getEvents(
                    Collections.singletonList(processedForm.getEvent().getFormSubmissionId()));
            getClientProcessorForJava().processClient(savedEvents);
            prefs.saveLastUpdatedAtDate(currentSyncDate.getTime());
            return true;
        } catch (Exception e) {
            Timber.e(e, "ChimwemweFormUtils.saveRegistration failed");
            return false;
        }
    }

    public static void ensureFieldValue(JSONObject form, String key, String value) {
        if (form == null || key == null || key.trim().isEmpty()) {
            return;
        }

        try {
            String normalizedValue = value == null ? "" : value;

            if (setFieldValueInSteps(form, key, normalizedValue)) {
                return;
            }

            // If the key doesn't exist in any step, add it to step1 so that subsequent calls
            // to JsonFormUtils.fields(form) (used by event/client creation) will include it.
            JSONObject step1 = form.optJSONObject("step1");
            if (step1 == null) {
                return;
            }

            JSONArray stepFields = step1.optJSONArray("fields");
            if (stepFields == null) {
                stepFields = new JSONArray();
                step1.put("fields", stepFields);
            }

            JSONObject field = new JSONObject();
            field.put("key", key);
            field.put("openmrs_entity_parent", "");
            field.put("openmrs_entity", "person_attribute");
            field.put("openmrs_entity_id", key);
            field.put("type", "hidden");
            field.put("value", normalizedValue);
            stepFields.put(field);
        } catch (Exception e) {
            Timber.e(e, "ChimwemweFormUtils.ensureFieldValue failed for %s", key);
        }
    }

    private static boolean setFieldValueInSteps(JSONObject form, String key, String value) {
        try {
            java.util.Iterator<String> it = form.keys();
            while (it.hasNext()) {
                String stepKey = it.next();
                if (stepKey == null || !stepKey.startsWith("step")) continue;
                JSONObject step = form.optJSONObject(stepKey);
                if (step == null) continue;
                JSONArray fields = step.optJSONArray("fields");
                if (fields == null) continue;

                for (int i = 0; i < fields.length(); i++) {
                    JSONObject field = fields.optJSONObject(i);
                    if (field != null && key.equals(field.optString("key"))) {
                        field.put("value", value);
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static String attendanceEntityId(String groupId, int sessionNumber) {
        String gid = groupId != null ? groupId.trim() : "";
        return "chimwemwe-attendance-" + gid + "-" + sessionNumber;
    }

    public static String attendanceEntityId(String groupId, int sessionNumber, long participantId) {
        String gid = groupId != null ? groupId.trim() : "";
        return "chimwemwe-attendance-" + gid + "-" + sessionNumber + "-" + participantId;
    }

    public static String reviewEntityId(long reviewId) {
        return "chimwemwe-review-" + reviewId;
    }

    public static String referralEntityId(long referralId) {
        return "chimwemwe-referral-" + referralId;
    }
}
