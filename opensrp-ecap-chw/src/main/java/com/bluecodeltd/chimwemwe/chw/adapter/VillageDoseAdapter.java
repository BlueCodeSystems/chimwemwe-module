package com.bluecodeltd.chimwemwe.chw.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import com.bluecodeltd.chimwemwe.chw.contract.ListContract;
import com.bluecodeltd.chimwemwe.chw.viewholder.ListableViewHolder;
import com.bluecodeltd.chimwemwe.chw.viewholder.VillageDoseViewHolder;

import com.bluecodeltd.chimwemwe.chw.R;

import com.bluecodeltd.chimwemwe.chw.domain.VillageDose;

import java.util.List;
import com.bluecodeltd.chimwemwe.chw.viewholder.ListableViewHolder;
import com.bluecodeltd.chimwemwe.chw.domain.VillageDose;
import com.bluecodeltd.chimwemwe.chw.adapter.ListableAdapter;

public class VillageDoseAdapter extends ListableAdapter<VillageDose, ListableViewHolder<VillageDose>> {
   private Context context;
    public VillageDoseAdapter(List<VillageDose> items, ListContract.View<VillageDose> view, Context context) {
        super(items, view);
        this.context = context;
    }

    @NonNull
    @Override
    public ListableViewHolder<VillageDose> onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.village_dose_report_item, parent, false);
        return new VillageDoseViewHolder(view, context);
    }
}
