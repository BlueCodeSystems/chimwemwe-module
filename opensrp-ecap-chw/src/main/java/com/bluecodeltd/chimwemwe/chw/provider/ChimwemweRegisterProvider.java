package com.bluecodeltd.chimwemwe.chw.provider;

import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.bluecodeltd.chimwemwe.chw.R;
import com.bluecodeltd.chimwemwe.chw.view_holder.ChimwemweGroupViewHolder;

import org.smartregister.chw.core.holders.FooterViewHolder;
import org.smartregister.commonregistry.CommonPersonObjectClient;
import org.smartregister.cursoradapter.RecyclerViewProvider;
import org.smartregister.util.Utils;
import org.smartregister.view.contract.SmartRegisterClient;
import org.smartregister.view.contract.SmartRegisterClients;
import org.smartregister.view.dialog.FilterOption;
import org.smartregister.view.dialog.ServiceModeOption;
import org.smartregister.view.dialog.SortOption;
import org.smartregister.view.viewholder.OnClickFormLauncher;

import java.text.MessageFormat;
import com.bluecodeltd.chimwemwe.chw.view_holder.ChimwemweGroupViewHolder;

public class ChimwemweRegisterProvider implements RecyclerViewProvider<ChimwemweGroupViewHolder> {

    // NEW — no sessions yet (grey)
    private static final int COLOR_NEW_BAR   = Color.parseColor("#94A3B8");
    private static final int COLOR_NEW_ICON  = Color.parseColor("#F1F5F9");
    private static final int COLOR_NEW_TEXT  = Color.parseColor("#64748B");
    private static final int COLOR_NEW_BADGE = Color.parseColor("#F1F5F9");

    // ACTIVE — sessions underway (sky blue, matches chimwemwe_primary)
    private static final int COLOR_ACT_BAR   = Color.parseColor("#0284C7");
    private static final int COLOR_ACT_ICON  = Color.parseColor("#E0F2FE");
    private static final int COLOR_ACT_TEXT  = Color.parseColor("#0284C7");
    private static final int COLOR_ACT_BADGE = Color.parseColor("#E0F2FE");

    // COMPLETE — all 14 sessions recorded (green)
    private static final int COLOR_DONE_BAR   = Color.parseColor("#166534");
    private static final int COLOR_DONE_ICON  = Color.parseColor("#DCFCE7");
    private static final int COLOR_DONE_TEXT  = Color.parseColor("#166534");
    private static final int COLOR_DONE_BADGE = Color.parseColor("#DCFCE7");

    private final android.content.Context context;
    private final View.OnClickListener onClickListener;
    private final View.OnClickListener paginationViewHandler;

    public ChimwemweRegisterProvider(android.content.Context context,
                                     View.OnClickListener onClickListener,
                                     View.OnClickListener paginationViewHandler) {
        this.context = context;
        this.onClickListener = onClickListener;
        this.paginationViewHandler = paginationViewHandler;
    }

    @Override
    public void getView(Cursor cursor, SmartRegisterClient client, ChimwemweGroupViewHolder h) {
        CommonPersonObjectClient pc = (CommonPersonObjectClient) client;

        String groupName = Utils.getValue(pc.getColumnmaps(), "group_name", false);
        String groupId = Utils.getValue(pc.getColumnmaps(), "group_id", false);
        String hotspot = Utils.getValue(pc.getColumnmaps(), "hotspot_name", false);

        String pCountStr = cursorStr(cursor, "p_count", "0");
        String sCountStr = cursorStr(cursor, "s_count", "0");
        int sCount = parseInt(sCountStr);

        h.tvGroupName.setText(!groupName.isEmpty() ? groupName : "-");
        h.tvGroupId.setText(!groupId.isEmpty() ? groupId : "-");
        h.tvHotspotName.setText(!hotspot.isEmpty() ? hotspot : "");
        h.tvParticipantCount.setText(pCountStr);
        h.tvSessionsRecorded.setText(sCount + " / 14 sessions");
        if (h.tvGroupInitials != null) {
            h.tvGroupInitials.setText(initials(groupName));
        }

        if (h.pbSessions != null) {
            h.pbSessions.setProgress(Math.min(sCount, 14));
        }

        int barColor;
        int iconColor;
        int textColor;
        int badgeColor;
        String badgeLabel;

        if (sCount >= 14) {
            barColor = COLOR_DONE_BAR;
            iconColor = COLOR_DONE_ICON;
            textColor = COLOR_DONE_TEXT;
            badgeColor = COLOR_DONE_BADGE;
            badgeLabel = "COMPLETE";
        } else if (sCount > 0) {
            barColor = COLOR_ACT_BAR;
            iconColor = COLOR_ACT_ICON;
            textColor = COLOR_ACT_TEXT;
            badgeColor = COLOR_ACT_BADGE;
            badgeLabel = "ACTIVE";
        } else {
            barColor = COLOR_NEW_BAR;
            iconColor = COLOR_NEW_ICON;
            textColor = COLOR_NEW_TEXT;
            badgeColor = COLOR_NEW_BADGE;
            badgeLabel = "NEW";
        }

        if (h.viewStatusBar != null) {
            h.viewStatusBar.setBackgroundColor(barColor);
        }
        if (h.flGroupIcon != null) {
            h.flGroupIcon.getBackground().setTint(barColor);
        }
        if (h.tvGroupStatus != null) {
            h.tvGroupStatus.setText(badgeLabel);
            h.tvGroupStatus.setTextColor(textColor);
            h.tvGroupStatus.setBackgroundTintList(ColorStateList.valueOf(badgeColor));
        }

        h.itemView.setTag(pc);
        h.itemView.setOnClickListener(onClickListener);
    }

    private static String cursorStr(Cursor c, String col, String fallback) {
        int idx = c.getColumnIndex(col);
        if (idx < 0) {
            return fallback;
        }
        String v = c.getString(idx);
        return (v == null || v.isEmpty()) ? fallback : v;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "G";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return String.valueOf(parts[0].charAt(0)).toUpperCase();
        return (String.valueOf(parts[0].charAt(0)) + String.valueOf(parts[parts.length - 1].charAt(0))).toUpperCase();
    }

    @Override
    public void getFooterView(RecyclerView.ViewHolder vh, int currentPage, int totalPages,
                              boolean hasNext, boolean hasPrev) {
        FooterViewHolder footer = (FooterViewHolder) vh;
        footer.pageInfoView.setText(
                MessageFormat.format(context.getString(org.smartregister.R.string.str_page_info),
                        currentPage, totalPages));
        footer.nextPageView.setVisibility(hasNext ? View.VISIBLE : View.INVISIBLE);
        footer.previousPageView.setVisibility(hasPrev ? View.VISIBLE : View.INVISIBLE);
        footer.nextPageView.setOnClickListener(paginationViewHandler);
        footer.previousPageView.setOnClickListener(paginationViewHandler);
    }

    @Override
    public SmartRegisterClients updateClients(FilterOption a, ServiceModeOption b, FilterOption c, SortOption d) {
        return null;
    }

    @Override
    public void onServiceModeSelected(ServiceModeOption s) {
    }

    @Override
    public OnClickFormLauncher newFormLauncher(String a, String b, String c) {
        return null;
    }

    @Override
    public LayoutInflater inflater() {
        return LayoutInflater.from(context);
    }

    @Override
    public ChimwemweGroupViewHolder createViewHolder(ViewGroup parent) {
        return new ChimwemweGroupViewHolder(
                inflater().inflate(R.layout.item_hotspot_group, parent, false));
    }

    @Override
    public RecyclerView.ViewHolder createFooterHolder(ViewGroup parent) {
        return new FooterViewHolder(
                inflater().inflate(org.smartregister.R.layout.smart_register_pagination, parent, false));
    }

    @Override
    public boolean isFooterViewHolder(RecyclerView.ViewHolder vh) {
        return vh instanceof FooterViewHolder;
    }
}
