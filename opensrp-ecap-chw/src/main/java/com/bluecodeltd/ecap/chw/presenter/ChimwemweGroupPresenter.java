package com.bluecodeltd.ecap.chw.presenter;

import android.app.Activity;

import androidx.annotation.NonNull;

import com.bluecodeltd.ecap.chw.R;
import com.bluecodeltd.ecap.chw.activity.ChimwemweRegisterActivity;
import com.bluecodeltd.ecap.chw.contract.ChimwemweRegisterContract;
import com.bluecodeltd.ecap.chw.interactor.ChimwemweRegisterInteractor;
import com.bluecodeltd.ecap.chw.model.ChimwemweRegisterModel;
import com.bluecodeltd.ecap.chw.util.Threading;

import org.apache.commons.lang3.StringUtils;
import org.smartregister.chw.core.custom_views.NavigationMenu;
import org.smartregister.domain.FetchStatus;
import org.smartregister.opd.pojo.RegisterParams;

import java.lang.ref.WeakReference;
import java.util.List;

import timber.log.Timber;

public class ChimwemweGroupPresenter implements ChimwemweRegisterContract.Presenter,
        ChimwemweRegisterContract.InteractorCallback {

    private final WeakReference<ChimwemweRegisterContract.View> viewRef;
    private final ChimwemweRegisterContract.Interactor interactor;
    private final ChimwemweRegisterContract.Model model;

    public ChimwemweGroupPresenter(ChimwemweRegisterContract.View view) {
        this.viewRef = new WeakReference<>(view);
        this.interactor = new ChimwemweRegisterInteractor();
        this.model = new ChimwemweRegisterModel();
    }

    @Override
    public void saveForm(String jsonString, @NonNull RegisterParams registerParams) {
        try {
            ChimwemweRegisterContract.RegistrationData registrationData =
                    model.processRegistration(jsonString, registerParams.getFormTag());
            ChimwemweRegisterContract.View view = getView();
            if (registrationData == null) {
                if (view != null) {
                    view.onGroupSaveError("Group name and hotspot name are required.");
                }
                return;
            }
            if (view != null) {
                view.toggleDialogVisibility(true);
            }
            interactor.saveRegistration(registrationData, jsonString, registerParams, this);
        } catch (Exception e) {
            Timber.e(e, "ChimwemweGroupPresenter: saveForm");
            onSaveError("Error saving enrollment. Please try again.");
        }
    }

    @Override
    public void startForm(String formName, String entityId, String metadata, String currentLocationId)
            throws Exception {
        if (StringUtils.isBlank(formName)) return;
        org.json.JSONObject form = model.getFormAsJson(formName, entityId, currentLocationId);
        ChimwemweRegisterContract.View view = getView();
        if (view instanceof ChimwemweRegisterActivity && form != null) {
            ((ChimwemweRegisterActivity) view).startFormActivity(form);
        } else if (view instanceof ChimwemweRegisterActivity) {
            ((ChimwemweRegisterActivity) view).displayToast(R.string.error_unable_to_start_form);
        }
    }

    @Override
    public ChimwemweRegisterContract.View getView() {
        return viewRef.get();
    }

    @Override
    public void onRegistrationSaved(boolean isEditMode, String groupName) {
        Threading.main(() -> {
            ChimwemweRegisterContract.View view = getView();
            if (view == null) return;
            view.toggleDialogVisibility(false);
            if (view instanceof ChimwemweRegisterActivity) {
                ChimwemweRegisterActivity activity = (ChimwemweRegisterActivity) view;
                activity.refreshList(FetchStatus.fetched);
                activity.hideProgressDialog();
                NavigationMenu navigationMenu = NavigationMenu.getInstance((Activity) activity, null, null);
                if (navigationMenu != null) {
                    navigationMenu.refreshCount();
                }
            }
            view.onGroupSaveComplete(groupName);
        });
    }

    @Override
    public void onSaveError(String errorMessage) {
        Threading.main(() -> {
            ChimwemweRegisterContract.View view = getView();
            if (view == null) return;
            view.toggleDialogVisibility(false);
            if (view instanceof ChimwemweRegisterActivity) {
                ((ChimwemweRegisterActivity) view).hideProgressDialog();
            }
            view.onGroupSaveError(errorMessage);
        });
    }

    @Override public void registerViewConfigurations(List<String> viewIdentifiers) {}
    @Override public void unregisterViewConfiguration(List<String> viewIdentifiers) {}
    @Override public void onDestroy(boolean isChangingConfiguration) {
        interactor.onDestroy(isChangingConfiguration);
    }
    @Override public void updateInitials() {}
}
