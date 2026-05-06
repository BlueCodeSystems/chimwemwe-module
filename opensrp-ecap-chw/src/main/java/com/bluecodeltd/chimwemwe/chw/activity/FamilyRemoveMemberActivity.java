package com.bluecodeltd.chimwemwe.chw.activity;

import org.smartregister.chw.core.activity.CoreFamilyRemoveMemberActivity;
import com.bluecodeltd.chimwemwe.chw.fragment.FamilyRemoveMemberFragment;

public class FamilyRemoveMemberActivity extends CoreFamilyRemoveMemberActivity {

    @Override
    protected void setRemoveMemberFragment() {
        this.removeMemberFragment = FamilyRemoveMemberFragment.newInstance(getIntent().getExtras());
    }

}
