package com.bluecodeltd.chimwemwe.chw.view_holder;

import android.view.View;
import android.widget.RadioGroup;

import com.bluecodeltd.chimwemwe.chw.R;
import com.bluecodeltd.chimwemwe.chw.view_holder.BaseIllnessViewHolder;

public class IllnessRadioViewHolder extends BaseIllnessViewHolder {
    public RadioGroup rgOptions;

    public IllnessRadioViewHolder(View view) {
        super(view);
        rgOptions = view.findViewById(R.id.rgOptions);
    }
}
