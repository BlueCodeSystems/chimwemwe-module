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

            String resolvedEntityId = entityId;
            if (resolvedEntityId == null || resolvedEntityId.trim().isEmpty()) {
                resolvedEntityId = form.optString("entity_id", "");
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

    public static String attendanceEntityId(long groupId, int sessionNumber) {
        return "chimwemwe-attendance-" + groupId + "-" + sessionNumber;
    }

    public static String reviewEntityId(long reviewId) {
        return "chimwemwe-review-" + reviewId;
    }

    public static String referralEntityId(long referralId) {
        return "chimwemwe-referral-" + referralId;
    }
}
