package com.bluecodeltd.ecap.chw.view_holder;

import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bluecodeltd.ecap.chw.R;

public class ChimwemweGroupViewHolder extends RecyclerView.ViewHolder {

    public final View        viewStatusBar;
    public final FrameLayout flGroupIcon;
    public final TextView    tvGroupName;
    public final TextView    tvGroupId;
    public final TextView    tvHotspotName;
    public final TextView    tvGroupStatus;
    public final TextView    tvSessionsRecorded;
    public final ProgressBar pbSessions;
    public final TextView    tvParticipantCount;

    public ChimwemweGroupViewHolder(@NonNull View itemView) {
        super(itemView);
        viewStatusBar      = itemView.findViewById(R.id.view_status_bar);
        flGroupIcon        = itemView.findViewById(R.id.fl_group_icon);
        tvGroupName        = itemView.findViewById(R.id.tv_group_name);
        tvGroupId          = itemView.findViewById(R.id.tv_group_id);
        tvHotspotName      = itemView.findViewById(R.id.tv_hotspot_name);
        tvGroupStatus      = itemView.findViewById(R.id.tv_group_status);
        tvSessionsRecorded = itemView.findViewById(R.id.tv_sessions_recorded);
        pbSessions         = itemView.findViewById(R.id.pb_sessions);
        tvParticipantCount = itemView.findViewById(R.id.tv_participant_count);
    }
}
