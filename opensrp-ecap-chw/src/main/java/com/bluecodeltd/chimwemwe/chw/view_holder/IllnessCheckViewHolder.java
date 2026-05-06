package com.bluecodeltd.chimwemwe.chw.view_holder;

import android.view.View;
import android.widget.LinearLayout;

import com.bluecodeltd.chimwemwe.chw.R;
import com.bluecodeltd.chimwemwe.chw.view_holder.BaseIllnessViewHolder;

public class IllnessCheckViewHolder extends BaseIllnessViewHolder {
    public LinearLayout checkboxParentLayout;

    public IllnessCheckViewHolder(View view) {
        super(view);
        checkboxParentLayout = view.findViewById(R.id.checkBoxParent);
    }
}
