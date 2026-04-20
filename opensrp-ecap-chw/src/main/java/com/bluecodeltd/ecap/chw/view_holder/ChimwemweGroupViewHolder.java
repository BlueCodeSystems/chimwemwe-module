package com.bluecodeltd.ecap.chw.view_holder;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bluecodeltd.ecap.chw.R;

public class ChimwemweGroupViewHolder extends RecyclerView.ViewHolder {

    public final TextView tvGroupName;
    public final TextView tvHotspotName;
    public final TextView tvParticipantCount;
    public final TextView tvSessionsRecorded;

    public ChimwemweGroupViewHolder(@NonNull View itemView) {
        super(itemView);
        tvGroupName        = itemView.findViewById(R.id.tv_group_name);
        tvHotspotName      = itemView.findViewById(R.id.tv_hotspot_name);
        tvParticipantCount = itemView.findViewById(R.id.tv_participant_count);
        tvSessionsRecorded = itemView.findViewById(R.id.tv_sessions_recorded);
    }
}
