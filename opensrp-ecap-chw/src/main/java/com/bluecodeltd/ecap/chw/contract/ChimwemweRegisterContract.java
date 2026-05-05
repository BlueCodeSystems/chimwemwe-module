package com.bluecodeltd.chimwemwe.chw.contract;

import com.bluecodeltd.chimwemwe.chw.model.HotspotGroupModel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;
import org.smartregister.domain.tag.FormTag;
import org.smartregister.opd.pojo.RegisterParams;
import org.smartregister.view.contract.BaseRegisterContract;

import java.util.List;

public interface ChimwemweRegisterContract {
    interface View {
        void toggleDialogVisibility(boolean showDialog);
        void onGroupSaveComplete(String groupName);
        void onGroupSaveError(String errorMessage);
    }

    interface Presenter extends BaseRegisterContract.Presenter {
        void saveForm(String jsonString, @NonNull RegisterParams registerParams);
        void startForm(String formName, String entityId, String metadata,
                       String currentLocationId) throws Exception;
        View getView();
    }

    interface Interactor {
        void saveRegistration(@NonNull RegistrationData registrationData,
                              @NonNull String jsonString,
                              @NonNull RegisterParams registerParams,
                              InteractorCallback callback);
        void onDestroy(boolean isChangingConfiguration);
    }

    interface Model {
        @Nullable
        RegistrationData processRegistration(String jsonString, FormTag formTag);

        @Nullable
        JSONObject getFormAsJson(String formName, String entityId, String currentLocationId);
    }

    interface InteractorCallback {
        void onRegistrationSaved(boolean isEditMode, String groupName);
        void onSaveError(String errorMessage);
    }

    class RegistrationData {
        private final JSONObject form;
        private final HotspotGroupModel group;

        public RegistrationData(JSONObject form, HotspotGroupModel group) {
            this.form = form;
            this.group = group;
        }

        public JSONObject getForm() {
            return form;
        }

        public HotspotGroupModel getGroup() {
            return group;
        }
    }
}
