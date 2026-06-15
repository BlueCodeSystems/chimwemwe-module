package com.bluecodeltd.chimwemwe.chw.fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bluecodeltd.chimwemwe.chw.R;

import java.util.ArrayList;
import java.util.List;

class GroupSimpleListAdapter<T> extends RecyclerView.Adapter<GroupSimpleListAdapter.Holder> {

    interface TitleProvider<T> { String title(T item); }
    interface SubtitleProvider<T> { String subtitle(T item); }

    private final TitleProvider<T> titleProvider;
    private final SubtitleProvider<T> subtitleProvider;
    interface ActionProvider<T> { void onEdit(T item); void onDelete(T item); }

    private final List<T> data = new ArrayList<>();
    private final ActionProvider<T> actionProvider;

    GroupSimpleListAdapter(TitleProvider<T> titleProvider, SubtitleProvider<T> subtitleProvider) {
        this(titleProvider, subtitleProvider, null);
    }

    GroupSimpleListAdapter(TitleProvider<T> titleProvider, SubtitleProvider<T> subtitleProvider, ActionProvider<T> actionProvider) {
        this.titleProvider = titleProvider;
        this.subtitleProvider = subtitleProvider;
        this.actionProvider = actionProvider;
    }

    void setData(List<T> items) {
        data.clear();
        if (items != null) data.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_group_simple_list, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        T item = data.get(position);
        holder.title.setText(titleProvider.title(item));
        holder.subtitle.setText(subtitleProvider.subtitle(item));
        if (holder.btnEdit != null) {
            holder.btnEdit.setVisibility(actionProvider == null ? View.GONE : View.VISIBLE);
            holder.btnEdit.setOnClickListener(v -> { if (actionProvider != null) actionProvider.onEdit(item); });
        }
        if (holder.btnDelete != null) {
            holder.btnDelete.setVisibility(actionProvider == null ? View.GONE : View.VISIBLE);
            holder.btnDelete.setOnClickListener(v -> { if (actionProvider != null) actionProvider.onDelete(item); });
        }
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView subtitle;
        final View btnEdit;
        final View btnDelete;
        Holder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.item_title);
            subtitle = itemView.findViewById(R.id.item_subtitle);
            btnEdit = itemView.findViewById(R.id.item_edit);
            btnDelete = itemView.findViewById(R.id.item_delete);
        }
    }
}
