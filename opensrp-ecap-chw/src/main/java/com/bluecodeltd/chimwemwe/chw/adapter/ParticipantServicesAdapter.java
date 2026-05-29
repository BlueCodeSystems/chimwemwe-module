package com.bluecodeltd.chimwemwe.chw.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bluecodeltd.chimwemwe.chw.R;
import com.bluecodeltd.chimwemwe.chw.model.HouseholdServiceReportModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Service report rows for the participant profile Services tab.
 * Re-uses {@code item_record_card} for visual parity with Reviews / Referrals.
 * <p>
 * Title    → service date
 * Subtitle → audience (household / caregiver / child) + signed flag
 * Detail   → comma-joined list of service categories actually selected
 */
public class ParticipantServicesAdapter extends RecyclerView.Adapter<ParticipantServicesAdapter.VH> {

    public interface Listener {
        void onEdit(@NonNull HouseholdServiceReportModel service);
        void onDelete(@NonNull HouseholdServiceReportModel service);
    }

    private static final String DASH = "—";

    private final Listener listener;
    private List<HouseholdServiceReportModel> data = new ArrayList<>();

    public ParticipantServicesAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    public void setData(List<HouseholdServiceReportModel> d) {
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
        HouseholdServiceReportModel r = data.get(position);
        h.title.setText("Service: " + dash(r.getDate()));

        String audience = dash(r.getServices());
        boolean hasSignature = r.getSignature() != null && !r.getSignature().trim().isEmpty();
        h.subtitle.setText("Audience: " + audience + (hasSignature ? "  •  Signed" : ""));

        h.detail.setText("Provided: " + dash(joinSelectedCategories(r)));

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

    private static String joinSelectedCategories(HouseholdServiceReportModel r) {
        StringBuilder sb = new StringBuilder();
        appendIfPresent(sb, "Health", r.getHealth_services());
        appendIfPresent(sb, "Schooled", r.getSchooled_services());
        appendIfPresent(sb, "Safe", r.getSafe_services());
        appendIfPresent(sb, "Stable", r.getStable_services());
        appendIfPresent(sb, "Household", r.getHh_level_services());
        return sb.toString();
    }

    private static void appendIfPresent(StringBuilder sb, String label, String value) {
        if (value == null) return;
        String t = value.trim();
        if (t.isEmpty()) return;
        if (sb.length() > 0) sb.append(", ");
        sb.append(label);
    }

    private static String dash(String v) {
        if (v == null) return DASH;
        String t = v.trim();
        return t.isEmpty() ? DASH : t;
    }
}
