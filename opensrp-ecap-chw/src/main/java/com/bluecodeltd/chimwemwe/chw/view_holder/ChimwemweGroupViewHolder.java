package com.bluecodeltd.chimwemwe.chw.view_holder;

import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bluecodeltd.chimwemwe.chw.R;

public class ChimwemweGroupViewHolder extends RecyclerView.ViewHolder {

    public final View        viewStatusBar;
    public final FrameLayout flGroupIcon;
    public final TextView    tvGroupInitials;
    public final TextView    tvGroupName;
    public final TextView    tvGroupId;
    public final TextView    tvHotspotName;
    public final TextView    tvGroupStatus;
    public final TextView    tvNotSavedBadge;
    public final TextView    tvSessionsRecorded;
    public final ProgressBar pbSessions;
    public final TextView    tvParticipantCount;

    public ChimwemweGroupViewHolder(@NonNull View itemView) {
        super(itemView);
        viewStatusBar      = itemView.findViewById(R.id.view_status_bar);
        flGroupIcon        = itemView.findViewById(R.id.fl_group_icon);
        tvGroupInitials    = itemView.findViewById(R.id.tv_group_initials);
        tvGroupName        = itemView.findViewById(R.id.tv_group_name);
        tvGroupId          = itemView.findViewById(R.id.tv_group_id);
        tvHotspotName      = itemView.findViewById(R.id.tv_hotspot_name);
        tvGroupStatus      = itemView.findViewById(R.id.tv_group_status);
        tvNotSavedBadge    = itemView.findViewById(R.id.tv_not_saved_badge);
        tvSessionsRecorded = itemView.findViewById(R.id.tv_sessions_recorded);
        pbSessions         = itemView.findViewById(R.id.pb_sessions);
        tvParticipantCount = itemView.findViewById(R.id.tv_participant_count);
    }
}
