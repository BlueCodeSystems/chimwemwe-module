package com.bluecodeltd.chimwemwe.chw.activity;

import android.app.Activity;

import com.bluecodeltd.chimwemwe.chw.R;
import org.smartregister.chw.core.fragment.FamilyCallDialogFragment;
import org.smartregister.chw.core.listener.OnClickFloatingMenu;
import com.bluecodeltd.chimwemwe.chw.presenter.ChildProfilePresenter;

public class ChildProfileActivityFlv extends DefaultChildProfileActivityFlv {

    @Override
    public OnClickFloatingMenu getOnClickFloatingMenu(final Activity activity, final ChildProfilePresenter presenter) {
        return viewId -> {
            if (presenter != null) {
                switch (viewId) {
                    case org.smartregister.chw.core.R.id.call_layout:
                        FamilyCallDialogFragment.launchDialog(activity, presenter.getFamilyId());
                        break;
                    case org.smartregister.chw.core.R.id.refer_to_facility_layout:
                        presenter.referToFacility();
                        break;
                    default:
                        break;
                }
            }
        };
    }
}
