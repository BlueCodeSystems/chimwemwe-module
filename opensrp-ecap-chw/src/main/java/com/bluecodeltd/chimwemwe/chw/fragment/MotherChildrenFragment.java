package com.bluecodeltd.chimwemwe.chw.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bluecodeltd.chimwemwe.chw.R;
import com.bluecodeltd.chimwemwe.chw.activity.MotherDetail;
import com.bluecodeltd.chimwemwe.chw.adapter.ChildrenAdapter;
import com.bluecodeltd.chimwemwe.chw.model.Child;
import com.bluecodeltd.chimwemwe.chw.viewmodel.HouseholdChildrenState;
import com.bluecodeltd.chimwemwe.chw.viewmodel.HouseholdChildrenViewModel;

import org.smartregister.commonregistry.CommonPersonObjectClient;

import java.util.ArrayList;
import java.util.HashMap;

public class MotherChildrenFragment extends Fragment {

    private com.bluecodeltd.chimwemwe.chw.databinding.FragmentChildrenBinding binding;

    private RecyclerView recyclerView;
    RecyclerView.Adapter recyclerViewadapter;
    private ArrayList<Child> childList = new ArrayList<>();
    private HouseholdChildrenViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = com.bluecodeltd.chimwemwe.chw.databinding.FragmentChildrenBinding.inflate(inflater, container, false);
        View view = binding.getRoot();

        HashMap<String, CommonPersonObjectClient> mymap = ( (MotherDetail) requireActivity()).getData();

        CommonPersonObjectClient mother = mymap.get("mother");
        String houseId = (mother != null && mother.getColumnmaps() != null) ? mother.getColumnmaps().get("household_id") : null;

        recyclerView = binding.recyclerView;

        RecyclerView.LayoutManager eLayoutManager = new LinearLayoutManager(getContext());
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(eLayoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerViewadapter = new ChildrenAdapter(childList, getContext(), "0");
        recyclerView.setAdapter(recyclerViewadapter);

        View progress = binding.progressLoading;
        if (progress != null) progress.setVisibility(View.VISIBLE);
        viewModel = new ViewModelProvider(this).get(HouseholdChildrenViewModel.class);
        viewModel.getState().observe(getViewLifecycleOwner(), this::applyChildrenState);
        viewModel.refresh(houseId);


        return view;

    }

    private void applyChildrenState(HouseholdChildrenState state) {
        if (!isAdded() || state == null) return;
        childList.clear();
        if (state.getChildren() != null) childList.addAll(state.getChildren());
        try { if (recyclerViewadapter != null) recyclerViewadapter.notifyDataSetChanged(); } catch (Exception ignored) {}
        View progress = (binding != null) ? binding.progressLoading : null;
        if (progress != null) progress.setVisibility(View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
