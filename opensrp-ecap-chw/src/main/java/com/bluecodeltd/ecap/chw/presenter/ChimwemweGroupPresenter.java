package com.bluecodeltd.ecap.chw.presenter;

import com.bluecodeltd.ecap.chw.contract.ChimwemweRegisterContract;
import com.bluecodeltd.ecap.chw.interactor.ChimwemweRegisterInteractor;
import com.bluecodeltd.ecap.chw.model.HotspotGroupModel;
import com.bluecodeltd.ecap.chw.util.Threading;

import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.List;

public class ChimwemweGroupPresenter implements ChimwemweRegisterContract.Presenter,
        ChimwemweRegisterContract.InteractorCallback {

    private final WeakReference<ChimwemweRegisterContract.View> viewRef;
    private final ChimwemweRegisterContract.Interactor interactor;

    public ChimwemweGroupPresenter(ChimwemweRegisterContract.View view) {
        this.viewRef = new WeakReference<>(view);
        this.interactor = new ChimwemweRegisterInteractor();
    }

    @Override
    public void saveForm(JSONObject form, HotspotGroupModel group) {
        if (getView() != null) getView().toggleDialogVisibility(true);
        interactor.saveGroup(form, group, this);
    }

    @Override
    public ChimwemweRegisterContract.View getView() {
        return viewRef.get();
    }

    // ── InteractorCallback ────────────────────────────────────

    @Override
    public void onGroupSaved(String groupName) {
        Threading.main(() -> {
            ChimwemweRegisterContract.View v = getView();
            if (v == null) return;
            v.toggleDialogVisibility(false);
            v.onGroupSaveComplete(groupName);
        });
    }

    @Override
    public void onSaveError(String errorMessage) {
        Threading.main(() -> {
            ChimwemweRegisterContract.View v = getView();
            if (v == null) return;
            v.toggleDialogVisibility(false);
            v.onGroupSaveError(errorMessage);
        });
    }

    // ── BaseRegisterContract.Presenter stubs ──────────────────

    @Override public void registerViewConfigurations(List<String> viewIdentifiers) {}
    @Override public void unregisterViewConfiguration(List<String> viewIdentifiers) {}
    @Override public void onDestroy(boolean isChangingConfiguration) {
        interactor.onDestroy(isChangingConfiguration);
    }
    @Override public void updateInitials() {}
}
