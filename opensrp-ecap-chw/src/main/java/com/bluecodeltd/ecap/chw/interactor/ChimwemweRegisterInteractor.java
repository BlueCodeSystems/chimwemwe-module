package com.bluecodeltd.ecap.chw.interactor;

import androidx.annotation.NonNull;

import com.bluecodeltd.ecap.chw.contract.ChimwemweRegisterContract;
import com.bluecodeltd.ecap.chw.dao.HotspotGroupDao;
import com.bluecodeltd.ecap.chw.model.HotspotGroupModel;
import com.bluecodeltd.ecap.chw.util.ChimwemweFormUtils;

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
                long groupId = HotspotGroupDao.insertGroup(group);
                if (groupId == -1) {
                    callback.onSaveError("Failed to save group. Please try again.");
                    return;
                }

                ChimwemweFormUtils.ProcessedForm processedForm = ChimwemweFormUtils.processRegistration(
                        form, "ec_chimwemwe_group", group.getGroupCode());
                ChimwemweFormUtils.saveRegistration(processedForm, registerParams.isEditMode());

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
