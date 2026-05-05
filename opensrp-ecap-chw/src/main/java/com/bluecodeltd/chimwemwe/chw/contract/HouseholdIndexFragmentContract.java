package com.bluecodeltd.chimwemwe.chw.contract;

import org.smartregister.view.contract.BaseRegisterFragmentContract;

public interface HouseholdIndexFragmentContract {

    interface Presenter extends BaseRegisterFragmentContract.Presenter {
        void initView(View view);

        View getView();
    }

    interface View extends BaseRegisterFragmentContract.View {
        void initializeAdapter();
    }
}
