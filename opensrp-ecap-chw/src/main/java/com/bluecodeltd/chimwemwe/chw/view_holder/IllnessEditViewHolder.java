package com.bluecodeltd.chimwemwe.chw.view_holder;

import android.view.View;
import android.widget.TextView;

import com.bluecodeltd.chimwemwe.chw.R;
import com.bluecodeltd.chimwemwe.chw.view_holder.BaseIllnessViewHolder;

public class IllnessEditViewHolder extends BaseIllnessViewHolder {
    public TextView tvValue;

    public IllnessEditViewHolder(View view) {
        super(view);
        tvValue = view.findViewById(R.id.tvValue);
    }
}
