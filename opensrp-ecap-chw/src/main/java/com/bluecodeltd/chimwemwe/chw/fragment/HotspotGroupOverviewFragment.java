package com.bluecodeltd.chimwemwe.chw.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bluecodeltd.chimwemwe.chw.R;
import com.bluecodeltd.chimwemwe.chw.activity.HotspotGroupDetailActivity;

public class HotspotGroupOverviewFragment extends Fragment
        implements HotspotGroupDetailActivity.HotspotGroupSectionFragment {

    private View rootView;

    public HotspotGroupOverviewFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_hotspot_group_overview, container, false);
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        refreshContent();
    }

    @Override
    public void refreshContent() {
        if (rootView == null) return;
        if (!(getActivity() instanceof HotspotGroupDetailActivity)) return;
        ((HotspotGroupDetailActivity) getActivity()).bindOverview(rootView);
    }
}
