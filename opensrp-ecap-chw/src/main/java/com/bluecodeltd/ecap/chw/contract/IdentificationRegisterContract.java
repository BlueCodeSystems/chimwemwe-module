package com.bluecodeltd.chimwemwe.chw.contract;

import com.bluecodeltd.chimwemwe.chw.domain.ChildIndexEventClient;
import com.bluecodeltd.chimwemwe.chw.model.IdentificationEventClient;

import org.smartregister.view.contract.BaseRegisterContract;

public interface IdentificationRegisterContract {

    interface View {
        void toggleDialogVisibility(boolean showDialog);
    }

    interface Presenter extends BaseRegisterContract.Presenter {
        void saveForm(String json, boolean isEditMode);

        void onRegistrationSaved();

        IdentificationRegisterContract.View getView();
    }

    interface Interactor {
        boolean saveRegistration(final IdentificationEventClient identificationEventClient, final boolean isEditMode);
    }

    interface Model {
        IdentificationEventClient processRegistration(String jsonString);
    }
}
