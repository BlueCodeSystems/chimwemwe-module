package com.bluecodeltd.chimwemwe.chw.fragment;

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
import com.bluecodeltd.chimwemwe.chw.dao.ChimwemweReferralDao;
import com.bluecodeltd.chimwemwe.chw.dao.ParticipantDao;
import com.bluecodeltd.chimwemwe.chw.activity.ReferralEditActivity;
import com.bluecodeltd.chimwemwe.chw.model.ChimwemweReferralModel;
import com.bluecodeltd.chimwemwe.chw.model.ParticipantModel;

import java.util.ArrayList;
import java.util.List;

public class GroupReferralsFragment extends Fragment {

    private static final String ARG_GROUP_ID = "group_id";
    private static final int REQ_EDIT_REFERRAL = 3003;

    public static GroupReferralsFragment newInstance(@Nullable String groupId) {
        GroupReferralsFragment fragment = new GroupReferralsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_GROUP_ID, groupId);
        fragment.setArguments(args);
        return fragment;
    }

    private TextView empty;
    private RecyclerView recycler;
    private GroupSimpleListAdapter<ChimwemweReferralModel> adapter;
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
                item -> item.getReferral_date() + " - " + item.getParticipant_id(),
                item -> item.getService_referred_for() + " @ " + item.getReceiving_org(),
                new GroupSimpleListAdapter.ActionProvider<ChimwemweReferralModel>() {
                    @Override
                    public void onEdit(ChimwemweReferralModel item) {
                        if (item == null) return;
                        String participantId = item.getParticipant_id();
                        if (participantId == null || participantId.trim().isEmpty()) return;
                        if (getActivity() == null) return;
                        ParticipantModel participant = ParticipantDao.getParticipantByCode(participantId);
                        if (participant == null) return;
                        android.content.Intent intent = new android.content.Intent(getActivity(), ReferralEditActivity.class);
                        intent.putExtra(ReferralEditActivity.EXTRA_PARTICIPANT_CODE, participant.getParticipantId());
                        intent.putExtra(ReferralEditActivity.EXTRA_REFERRAL_BASE_ENTITY_ID, item.getBase_entity_id());
                        startActivityForResult(intent, REQ_EDIT_REFERRAL);
                    }

                    @Override
                    public void onDelete(ChimwemweReferralModel item) {
                        ChimwemweReferralDao.deleteReferral(item.getBase_entity_id());
                        refresh();
                    }
                });
        recycler.setAdapter(adapter);
        refresh();
    }

    private void refresh() {
        List<ChimwemweReferralModel> items = groupId == null ? new ArrayList<>() : ChimwemweReferralDao.getGroupReferrals(groupId);
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        recycler.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        adapter.setData(items);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_EDIT_REFERRAL && resultCode == android.app.Activity.RESULT_OK) {
            refresh();
        }
    }
}
