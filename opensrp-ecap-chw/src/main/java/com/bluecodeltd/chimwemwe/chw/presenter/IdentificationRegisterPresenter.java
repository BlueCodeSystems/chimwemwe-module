package com.bluecodeltd.chimwemwe.chw.presenter;

import com.bluecodeltd.chimwemwe.chw.activity.IdentificationRegisterActivity;
import com.bluecodeltd.chimwemwe.chw.contract.IdentificationRegisterContract;
import com.bluecodeltd.chimwemwe.chw.interactor.IdentificationRegisterInteractor;
import com.bluecodeltd.chimwemwe.chw.model.IdentificationEventClient;
import com.bluecodeltd.chimwemwe.chw.model.IdentificationRegisterModel;

import org.smartregister.domain.FetchStatus;

import java.lang.ref.WeakReference;
import java.util.List;

import timber.log.Timber;

public class IdentificationRegisterPresenter implements IdentificationRegisterContract.Presenter {

    private IdentificationRegisterContract.View view;
    private IdentificationRegisterContract.Model model;
    private IdentificationRegisterContract.Interactor interactor;

    private WeakReference<IdentificationRegisterContract.View> activityWeakReference;

    public IdentificationRegisterPresenter(IdentificationRegisterContract.View view) {
        this.view = view;
        activityWeakReference = new WeakReference<>(view);
        interactor = new IdentificationRegisterInteractor(this);
        setModel(new IdentificationRegisterModel());
    }

    public void setModel(IdentificationRegisterContract.Model model) {
        this.model = model;
    }

    @Override
    public void registerViewConfigurations(List<String> list) {
        //Overridden
    }

    @Override
    public void unregisterViewConfiguration(List<String> list) {
        //Overridden
    }

    @Override
    public void onDestroy(boolean b) {
        //Overridden
    }

    @Override
    public void updateInitials() {
        //Overridden
    }

    @Override
    public void saveForm(String jsonString, boolean isEditMode) {

        try {

            view.toggleDialogVisibility(true);


            IdentificationEventClient identificationEventClient = model.processRegistration(jsonString);

            if (identificationEventClient == null) {
                return;
            }

            interactor.saveRegistration(identificationEventClient, isEditMode);

        } catch (Exception e) {
            Timber.e(e);
        }
    }

    @Override
    public void onRegistrationSaved() {
        getView().toggleDialogVisibility(false);
        getView().refreshList(FetchStatus.fetched);
    }

    @Override
    public IdentificationRegisterActivity getView() {
        if (activityWeakReference.get() != null) {
            return (IdentificationRegisterActivity) view;
        }
        return null;
    }
}
