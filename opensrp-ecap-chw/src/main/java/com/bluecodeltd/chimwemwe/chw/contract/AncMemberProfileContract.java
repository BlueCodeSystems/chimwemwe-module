package com.bluecodeltd.chimwemwe.chw.contract;

import org.smartregister.chw.core.listener.OnRetrieveNotifications;
import com.bluecodeltd.chimwemwe.chw.model.ReferralTypeModel;

import java.util.List;

public interface AncMemberProfileContract extends org.smartregister.chw.core.contract.AncMemberProfileContract {
    interface Presenter{
        void referToFacility();
    }

    interface View extends OnRetrieveNotifications {
        List<ReferralTypeModel> getReferralTypeModels();
    }
}
