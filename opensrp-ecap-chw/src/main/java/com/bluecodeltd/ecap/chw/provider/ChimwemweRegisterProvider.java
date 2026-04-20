package com.bluecodeltd.ecap.chw.provider;

import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.bluecodeltd.ecap.chw.R;
import com.bluecodeltd.ecap.chw.view_holder.ChimwemweGroupViewHolder;

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

public class ChimwemweRegisterProvider implements RecyclerViewProvider<ChimwemweGroupViewHolder> {

    private final Context context;
    private final View.OnClickListener onClickListener;
    private final View.OnClickListener paginationViewHandler;

    public ChimwemweRegisterProvider(Context context,
                                     View.OnClickListener onClickListener,
                                     View.OnClickListener paginationViewHandler) {
        this.context = context;
        this.onClickListener = onClickListener;
        this.paginationViewHandler = paginationViewHandler;
    }

    @Override
    public void getView(Cursor cursor, SmartRegisterClient client, ChimwemweGroupViewHolder holder) {
        CommonPersonObjectClient personClient = (CommonPersonObjectClient) client;
        String groupName = Utils.getValue(personClient.getColumnmaps(), "group_name",   false);
        String hotspot   = Utils.getValue(personClient.getColumnmaps(), "hotspot_name", false);

        // Read computed counts directly from the cursor — CommonRepository may not include
        // aliased subquery columns in the column map, so we bypass it here.
        String pCount = cursorString(cursor, "p_count", "0");
        String sCount = cursorString(cursor, "s_count", "0");

        holder.tvGroupName.setText(!groupName.isEmpty() ? groupName : "—");
        holder.tvHotspotName.setText(hotspot);
        holder.tvParticipantCount.setText(pCount + " participants");
        holder.tvSessionsRecorded.setText(sCount + "/14 sessions");

        holder.itemView.setTag(personClient);
        holder.itemView.setOnClickListener(onClickListener);
    }

    /** Read a string value from the cursor by column name, returning {@code fallback} if absent. */
    private static String cursorString(Cursor cursor, String column, String fallback) {
        int idx = cursor.getColumnIndex(column);
        if (idx < 0) return fallback;
        String val = cursor.getString(idx);
        return (val == null || val.isEmpty()) ? fallback : val;
    }

    @Override
    public void getFooterView(RecyclerView.ViewHolder viewHolder, int currentPage, int totalPages,
                              boolean hasNext, boolean hasPrev) {
        FooterViewHolder footer = (FooterViewHolder) viewHolder;
        footer.pageInfoView.setText(
                MessageFormat.format(context.getString(org.smartregister.R.string.str_page_info),
                        currentPage, totalPages));
        footer.nextPageView.setVisibility(hasNext ? View.VISIBLE : View.INVISIBLE);
        footer.previousPageView.setVisibility(hasPrev ? View.VISIBLE : View.INVISIBLE);
        footer.nextPageView.setOnClickListener(paginationViewHandler);
        footer.previousPageView.setOnClickListener(paginationViewHandler);
    }

    @Override
    public SmartRegisterClients updateClients(FilterOption fo, ServiceModeOption smo,
                                              FilterOption fo2, SortOption so) {
        return null;
    }

    @Override
    public void onServiceModeSelected(ServiceModeOption smo) {}

    @Override
    public OnClickFormLauncher newFormLauncher(String s, String s1, String s2) {
        return null;
    }

    @Override
    public LayoutInflater inflater() {
        return LayoutInflater.from(context);
    }

    @Override
    public ChimwemweGroupViewHolder createViewHolder(ViewGroup parent) {
        View v = inflater().inflate(R.layout.item_hotspot_group, parent, false);
        return new ChimwemweGroupViewHolder(v);
    }

    @Override
    public RecyclerView.ViewHolder createFooterHolder(ViewGroup parent) {
        View v = inflater().inflate(org.smartregister.R.layout.smart_register_pagination, parent, false);
        return new FooterViewHolder(v);
    }

    @Override
    public boolean isFooterViewHolder(RecyclerView.ViewHolder vh) {
        return vh instanceof FooterViewHolder;
    }
}
