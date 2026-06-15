package com.bluecodeltd.chimwemwe.chw.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bluecodeltd.chimwemwe.chw.R;
import com.bluecodeltd.chimwemwe.chw.activity.ReviewEditActivity;
import com.bluecodeltd.chimwemwe.chw.dao.MonthlyReviewDao;
import com.bluecodeltd.chimwemwe.chw.model.MonthlyReviewModel;
import java.util.ArrayList;
import java.util.List;

public class GroupReviewsFragment extends Fragment {

    private static final String ARG_GROUP_ID = "group_id";
    private static final int REQ_EDIT_REVIEW = 3004;

    public static GroupReviewsFragment newInstance(@Nullable String groupId) {
        GroupReviewsFragment fragment = new GroupReviewsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_GROUP_ID, groupId);
        fragment.setArguments(args);
        return fragment;
    }

    private TextView empty;
    private RecyclerView recycler;
    private GroupSimpleListAdapter<MonthlyReviewModel> adapter;
    private String groupId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_group_simple_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        groupId = getArguments() != null ? getArguments().getString(ARG_GROUP_ID) : null;
        empty = view.findViewById(R.id.tv_empty);
        recycler = view.findViewById(R.id.recycler);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.addItemDecoration(new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));
        adapter = new GroupSimpleListAdapter<>(
                item -> item.getReviewDate() + " - " + item.getReviewerName(),
                item -> item.getReviewerNotes(),
                new GroupSimpleListAdapter.ActionProvider<MonthlyReviewModel>() {
                    @Override
                    public void onEdit(MonthlyReviewModel item) {
                        launchReviewForm(item);
                    }

                    @Override
                    public void onDelete(MonthlyReviewModel item) {
                        MonthlyReviewDao.deleteReview(item.getBase_entity_id());
                        refresh();
                    }
                });
        recycler.setAdapter(adapter);
        refresh();
    }

    private void refresh() {
        List<MonthlyReviewModel> items = groupId == null ? new ArrayList<>() : MonthlyReviewDao.getReviews(groupId);
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        recycler.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        adapter.setData(items);
    }

    private void launchReviewForm(@Nullable MonthlyReviewModel existing) {
        if (groupId == null || groupId.trim().isEmpty()) return;
        Intent intent = new Intent(requireContext(), ReviewEditActivity.class);
        intent.putExtra(ReviewEditActivity.EXTRA_GROUP_ID, groupId);
        intent.putExtra(ReviewEditActivity.EXTRA_REVIEW_BASE_ENTITY_ID,
                existing != null ? existing.getBase_entity_id() : null);
        startActivityForResult(intent, REQ_EDIT_REVIEW);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_EDIT_REVIEW && resultCode == android.app.Activity.RESULT_OK) {
            refresh();
        }
    }
}
