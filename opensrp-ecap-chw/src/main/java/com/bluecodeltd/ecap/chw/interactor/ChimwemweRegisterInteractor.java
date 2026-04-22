package com.bluecodeltd.ecap.chw.interactor;

import static com.bluecodeltd.ecap.chw.util.IndexClientsUtils.getAllSharedPreferences;
import static com.bluecodeltd.ecap.chw.util.IndexClientsUtils.getFormTag;
import static com.bluecodeltd.ecap.chw.util.JsonFormUtils.tagSyncMetadata;
import static org.smartregister.chw.fp.util.FpUtil.getClientProcessorForJava;

import androidx.annotation.NonNull;

import com.bluecodeltd.ecap.chw.application.ChwApplication;
import com.bluecodeltd.ecap.chw.contract.ChimwemweRegisterContract;
import com.bluecodeltd.ecap.chw.dao.HotspotGroupDao;
import com.bluecodeltd.ecap.chw.model.HotspotGroupModel;

import org.json.JSONArray;
import org.json.JSONObject;
import org.smartregister.clientandeventmodel.Client;
import org.smartregister.clientandeventmodel.Event;
import org.smartregister.domain.db.EventClient;
import org.smartregister.domain.tag.FormTag;
import org.smartregister.family.util.AppExecutors;
import org.smartregister.opd.pojo.RegisterParams;
import org.smartregister.repository.AllSharedPreferences;
import org.smartregister.sync.helper.ECSyncHelper;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import timber.log.Timber;

public class ChimwemweRegisterInteractor implements ChimwemweRegisterContract.Interactor {

    @Override
    public void saveRegistration(@NonNull ChimwemweRegisterContract.RegistrationData registrationData,
                                 @NonNull String jsonString,
                                 @NonNull RegisterParams registerParams,
                                 ChimwemweRegisterContract.InteractorCallback callback) {
        final JSONObject form = registrationData.getForm();
        final HotspotGroupModel group = registrationData.getGroup();
        final String groupName = group.getGroupName();

        new AppExecutors().diskIO().execute(() -> {
            try {
                long groupId = HotspotGroupDao.insertGroup(group);
                if (groupId == -1) {
                    callback.onSaveError("Failed to save group. Please try again.");
                    return;
                }

                AllSharedPreferences prefs = getAllSharedPreferences();
                FormTag formTag = getFormTag();
                String entityId = org.smartregister.util.JsonFormUtils.generateRandomUUIDString();

                JSONArray fields = org.smartregister.util.JsonFormUtils.fields(form);
                JSONObject metadata = form.optJSONObject("metadata");
                String encounterType = form.optString("encounter_type", "");

                if (fields != null && metadata != null && !encounterType.isEmpty()) {
                    Event event = org.smartregister.util.JsonFormUtils.createEvent(
                            fields, metadata, formTag, entityId, encounterType, "ec_chimwemwe_group");
                    tagSyncMetadata(event);

                    Client client = org.smartregister.util.JsonFormUtils.createBaseClient(
                            fields, formTag, entityId);

                    ECSyncHelper syncHelper = ChwApplication.getInstance().getEcSyncHelper();
                    syncHelper.addClient(entityId,
                            new JSONObject(org.smartregister.util.JsonFormUtils.gson.toJson(client)));
                    syncHelper.addEvent(entityId,
                            new JSONObject(org.smartregister.util.JsonFormUtils.gson.toJson(event)));

                    Date currentSyncDate = new Date(prefs.fetchLastUpdatedAtDate(0));
                    List<EventClient> saved = syncHelper.getEvents(
                            Collections.singletonList(event.getFormSubmissionId()));
                    getClientProcessorForJava().processClient(saved);
                    prefs.saveLastUpdatedAtDate(currentSyncDate.getTime());
                }

                callback.onRegistrationSaved(registerParams.isEditMode(), groupName);
            } catch (Exception e) {
                Timber.e(e, "ChimwemweRegisterInteractor: error saving group");
                callback.onSaveError("Error saving enrollment. Please try again.");
            }
        });
    }

    @Override
    public void onDestroy(boolean isChangingConfiguration) {
        // no-op
    }
}
