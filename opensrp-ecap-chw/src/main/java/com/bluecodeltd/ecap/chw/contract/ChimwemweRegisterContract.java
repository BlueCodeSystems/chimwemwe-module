package com.bluecodeltd.ecap.chw.contract;

import com.bluecodeltd.ecap.chw.model.HotspotGroupModel;

import org.json.JSONObject;
import org.smartregister.view.contract.BaseRegisterContract;

public interface ChimwemweRegisterContract {

    interface View {
        void toggleDialogVisibility(boolean showDialog);
        void onGroupSaveComplete(String groupName);
        void onGroupSaveError(String errorMessage);
    }

    interface Presenter extends BaseRegisterContract.Presenter {
        void saveForm(JSONObject form, HotspotGroupModel group);
        View getView();
    }

    interface Interactor {
        void saveGroup(JSONObject form, HotspotGroupModel group, InteractorCallback callback);
        void onDestroy(boolean isChangingConfiguration);
    }

    interface InteractorCallback {
        void onGroupSaved(String groupName);
        void onSaveError(String errorMessage);
    }
}
