package com.bluecodeltd.chimwemwe.chw.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bluecodeltd.chimwemwe.chw.R;
import com.bluecodeltd.chimwemwe.chw.model.ChimwemweReferralModel;

import java.util.ArrayList;
import java.util.List;

public class ParticipantReferralsAdapter extends RecyclerView.Adapter<ParticipantReferralsAdapter.VH> {

    public interface Listener {
        void onEdit(@NonNull ChimwemweReferralModel referral);
        void onDelete(@NonNull ChimwemweReferralModel referral);
    }

    private static final String DASH = "\u2014";

    private final Listener listener;
    private List<ChimwemweReferralModel> data = new ArrayList<>();

    public ParticipantReferralsAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    public void setData(List<ChimwemweReferralModel> d) {
        data = d != null ? d : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_record_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        ChimwemweReferralModel r = data.get(position);
        h.title.setText(displayValue(r.getServiceReferredFor(), "Service not recorded"));
        h.subtitle.setText("Who is being referred: "
                + displayValue(r.getWhoReferred(), "Not recorded")
                + "  \u2022  Date: "
                + displayValue(r.getReferralDate(), "Not recorded"));

        String provider = clean(r.getProvider());
        String organisation = clean(r.getReceivingOrg());
        if (provider.isEmpty() && organisation.isEmpty()) {
            h.detail.setVisibility(View.GONE);
        } else {
            h.detail.setVisibility(View.VISIBLE);
            String detail = provider.isEmpty() ? "" : "Provider: " + provider;
            if (!organisation.isEmpty()) {
                if (!detail.isEmpty()) detail += "  \u2022  ";
                detail += "Organisation: " + organisation;
            }
            h.detail.setText(detail);
        }
        h.btnEdit.setOnClickListener(v -> listener.onEdit(r));
        h.btnDelete.setOnClickListener(v -> listener.onDelete(r));
    }

    @Override
    public int getItemCount() {
        return data != null ? data.size() : 0;
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView title, subtitle, detail;
        View btnEdit;
        View btnDelete;

        VH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tv_record_title);
            subtitle = itemView.findViewById(R.id.tv_record_subtitle);
            detail = itemView.findViewById(R.id.tv_record_detail);
            btnEdit = itemView.findViewById(R.id.btn_edit_record);
            btnDelete = itemView.findViewById(R.id.btn_delete_record);
        }
    }

    private String displayValue(String value, String fallback) {
        String cleaned = clean(value);
        return cleaned.isEmpty() ? fallback : cleaned;
    }

    private String clean(String value) {
        if (value == null) return "";
        String cleaned = value.trim();
        if (cleaned.isEmpty()
                || "null".equalsIgnoreCase(cleaned)
                || "@null".equalsIgnoreCase(cleaned)
                || DASH.equals(cleaned)) {
            return "";
        }
        return cleaned;
    }
}
