package com.bluecodeltd.chimwemwe.chw.interactor;

import androidx.annotation.NonNull;

import com.bluecodeltd.chimwemwe.chw.contract.ChimwemweRegisterContract;
import com.bluecodeltd.chimwemwe.chw.model.HotspotGroupModel;
import com.bluecodeltd.chimwemwe.chw.util.ChimwemweFormUtils;

import org.json.JSONObject;
import org.smartregister.family.util.AppExecutors;
import org.smartregister.opd.pojo.RegisterParams;

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
                if (group.getGroupId() == null || group.getGroupId().trim().isEmpty()) {
                    callback.onSaveError("Failed to save group. Please try again.");
                    return;
                }

                ChimwemweFormUtils.ensureFieldValue(form, "group_id", group.getGroupId());
                ChimwemweFormUtils.ensureFieldValue(form, "created_date", group.getCreatedDate());
                form.put("encounter_type", "Chimwemwe Group Registration");

                ChimwemweFormUtils.ProcessedForm processedForm = ChimwemweFormUtils.processRegistration(
                        form, "ec_chimwemwe_group", group.getGroupId());
                boolean saved = ChimwemweFormUtils.saveRegistration(processedForm, registerParams.isEditMode());
                if (!saved) {
                    callback.onSaveError("Failed to save group. Please try again.");
                    return;
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
