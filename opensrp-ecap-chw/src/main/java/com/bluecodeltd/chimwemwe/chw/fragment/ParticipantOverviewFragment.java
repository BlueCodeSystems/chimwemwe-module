package com.bluecodeltd.chimwemwe.chw.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bluecodeltd.chimwemwe.chw.R;
import com.bluecodeltd.chimwemwe.chw.model.HotspotGroupModel;
import com.bluecodeltd.chimwemwe.chw.model.ParticipantModel;

public class ParticipantOverviewFragment extends Fragment implements ParticipantProfileSection {

    private static final String DASH = "\u2014";

    private View rootView;

    public ParticipantOverviewFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_participant_overview, container, false);
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
        if (!(getActivity() instanceof com.bluecodeltd.chimwemwe.chw.activity.ChimwemweParticipantProfileActivity)) return;
        com.bluecodeltd.chimwemwe.chw.activity.ChimwemweParticipantProfileActivity host =
                (com.bluecodeltd.chimwemwe.chw.activity.ChimwemweParticipantProfileActivity) getActivity();
        ParticipantModel participant = host.getParticipant();
        HotspotGroupModel group = host.getGroup();

        TextView tvChildDob = rootView.findViewById(R.id.tv_child_dob);
        TextView tvChildSex = rootView.findViewById(R.id.tv_child_sex);
        TextView tvVcaId = rootView.findViewById(R.id.tv_vca_id);
        TextView tvCaregiverId = rootView.findViewById(R.id.tv_caregiver_id);

        TextView tvGroupName = rootView.findViewById(R.id.tv_group_name);
        TextView tvHotspotName = rootView.findViewById(R.id.tv_hotspot_name);
        TextView tvProvinceDistrict = rootView.findViewById(R.id.tv_province_district);
        TextView tvSessionLocation = rootView.findViewById(R.id.tv_session_location);
        TextView tvHealthFacility = rootView.findViewById(R.id.tv_health_facility);
        TextView tvFacilitators = rootView.findViewById(R.id.tv_facilitators);

        if (participant != null) {
            if (tvChildDob != null) tvChildDob.setText(dash(participant.getChildDob()));
            if (tvChildSex != null) tvChildSex.setText(dash(participant.getChildSex()));
            if (tvVcaId != null) tvVcaId.setText(dash(participant.getVcaId()));
            if (tvCaregiverId != null) tvCaregiverId.setText(dash(participant.getCaregiverId()));
        } else {
            if (tvChildDob != null) tvChildDob.setText(DASH);
            if (tvChildSex != null) tvChildSex.setText(DASH);
            if (tvVcaId != null) tvVcaId.setText(DASH);
            if (tvCaregiverId != null) tvCaregiverId.setText(DASH);
        }

        if (group != null) {
            if (tvGroupName != null) tvGroupName.setText(dash(group.getGroupName()));
            if (tvHotspotName != null) tvHotspotName.setText(dash(group.getHotspotName()));
            if (tvProvinceDistrict != null) {
                tvProvinceDistrict.setText(dash(group.getProvince()) + " / " + dash(group.getDistrict()));
            }
            if (tvSessionLocation != null) tvSessionLocation.setText(dash(group.getLocationOfSession()));
            if (tvHealthFacility != null) tvHealthFacility.setText(dash(group.getNearestHealthFacility()));
            if (tvFacilitators != null) {
                String f1 = group.getFacilitatorName1() != null ? group.getFacilitatorName1().trim() : "";
                String f2 = group.getFacilitatorName2() != null ? group.getFacilitatorName2().trim() : "";
                String facilitators = f1.isEmpty() ? f2 : (f2.isEmpty() ? f1 : f1 + ", " + f2);
                tvFacilitators.setText(dash(facilitators));
            }
        } else {
            if (tvGroupName != null) tvGroupName.setText(DASH);
            if (tvHotspotName != null) tvHotspotName.setText(DASH);
            if (tvProvinceDistrict != null) tvProvinceDistrict.setText(DASH);
            if (tvSessionLocation != null) tvSessionLocation.setText(DASH);
            if (tvHealthFacility != null) tvHealthFacility.setText(DASH);
            if (tvFacilitators != null) tvFacilitators.setText(DASH);
        }
    }

    private String dash(String v) {
        if (v == null) return DASH;
        String t = v.trim();
        return t.isEmpty() ? DASH : t;
    }
}
