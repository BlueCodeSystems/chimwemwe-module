package com.bluecodeltd.chimwemwe.chw.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import com.bluecodeltd.chimwemwe.chw.contract.ListContract;
import com.bluecodeltd.chimwemwe.chw.viewholder.ListableViewHolder;
import com.bluecodeltd.chimwemwe.chw.viewholder.ReportViewHolder;

import com.bluecodeltd.chimwemwe.chw.R;

import com.bluecodeltd.chimwemwe.chw.domain.ReportType;

import java.util.List;
import com.bluecodeltd.chimwemwe.chw.viewholder.ListableViewHolder;
import com.bluecodeltd.chimwemwe.chw.domain.ReportType;
import com.bluecodeltd.chimwemwe.chw.adapter.ListableAdapter;

public class ReportsFragmentAdapter extends ListableAdapter<ReportType, ListableViewHolder<ReportType>> {

    public ReportsFragmentAdapter(List<ReportType> items, ListContract.View<ReportType> view) {
        super(items, view);
    }

    @NonNull
    @Override
    public ListableViewHolder<ReportType> onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.reports_fragment_item, parent, false);
        return new ReportViewHolder(view);
    }

}
