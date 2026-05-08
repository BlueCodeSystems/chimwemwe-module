package com.bluecodeltd.chimwemwe.chw.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bluecodeltd.chimwemwe.chw.R;
import com.bluecodeltd.chimwemwe.chw.model.chimwemweParticipantReviewModel;

import java.util.ArrayList;
import java.util.List;

public class ParticipantReviewsAdapter extends RecyclerView.Adapter<ParticipantReviewsAdapter.VH> {

    public interface Listener {
        void onEdit(@NonNull chimwemweParticipantReviewModel review);
        void onDelete(@NonNull chimwemweParticipantReviewModel review);
    }

    private static final String DASH = "\u2014";

    private final Listener listener;
    private List<chimwemweParticipantReviewModel> data = new ArrayList<>();

    public ParticipantReviewsAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    public void setData(List<chimwemweParticipantReviewModel> d) {
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
        chimwemweParticipantReviewModel r = data.get(position);
        h.title.setText(dash(r.getReview_quarter()));
        h.subtitle.setText("Reviewer: " + dash(r.getReviewer_name()) + "  \u2022  Date: " + dash(r.getReview_date()));
        h.detail.setText("Register accurate: " + dash(r.getRegister_accurate()) + "\n" + dash(r.getReviewer_notes()));
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

    private String dash(String v) {
        if (v == null) return DASH;
        String t = v.trim();
        return t.isEmpty() ? DASH : t;
    }
}
