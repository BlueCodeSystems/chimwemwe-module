package com.bluecodeltd.chimwemwe.chw.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bluecodeltd.chimwemwe.chw.R;
import com.bluecodeltd.chimwemwe.chw.adapter.ParticipantReferralsAdapter;
import com.bluecodeltd.chimwemwe.chw.dao.ChimwemweReferralDao;
import com.bluecodeltd.chimwemwe.chw.model.ChimwemweReferralModel;
import com.bluecodeltd.chimwemwe.chw.util.ChimwemweFormUtils;
import com.bluecodeltd.chimwemwe.chw.util.Threading;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.json.JSONObject;
import org.smartregister.chw.core.utils.CoreJsonFormUtils;
import org.smartregister.util.FormUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

public class ParticipantReferralsFragment extends Fragment implements ParticipantProfileSection {

    private TextView tvEmpty;
    private RecyclerView recycler;
    private ParticipantReferralsAdapter adapter;
    private List<ChimwemweReferralModel> items = new ArrayList<>();

    public ParticipantReferralsFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_participant_referrals, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tvEmpty = view.findViewById(R.id.tv_empty);
        recycler = view.findViewById(R.id.recycler);

        adapter = new ParticipantReferralsAdapter(new ParticipantReferralsAdapter.Listener() {
            @Override
            public void onEdit(@NonNull ChimwemweReferralModel referral) {
                requestEdit(referral);
            }

            @Override
            public void onDelete(@NonNull ChimwemweReferralModel referral) {
                confirmDelete(referral);
            }
        });

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.addItemDecoration(new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));
        recycler.setAdapter(adapter);

        refreshContent();
    }

    @Override
    public void refreshContent() {
        if (!(getActivity() instanceof com.bluecodeltd.chimwemwe.chw.activity.ChimwemweParticipantProfileActivity)) return;
        com.bluecodeltd.chimwemwe.chw.activity.ChimwemweParticipantProfileActivity host =
                (com.bluecodeltd.chimwemwe.chw.activity.ChimwemweParticipantProfileActivity) getActivity();
        String participantCode = host.getParticipant() != null ? host.getParticipant().getParticipantId() : null;
        if (participantCode == null || participantCode.trim().isEmpty()) {
            items = new ArrayList<>();
            render();
            host.setReferralsCount(0);
            return;
        }

        Threading.io(() -> {
            List<ChimwemweReferralModel> loaded = null;
            int count = 0;
            try {
                loaded = ChimwemweReferralDao.getParticipantReferrals(participantCode);
                count = ChimwemweReferralDao.countParticipantReferrals(participantCode);
            } catch (Exception e) {
                Timber.e(e, "Load participant referrals failed");
            }
            List<ChimwemweReferralModel> finalLoaded = loaded != null ? loaded : new ArrayList<>();
            int finalCount = count;
            Threading.main(() -> {
                items = finalLoaded;
                render();
                host.setReferralsCount(finalCount);
            });
        });
    }

    private void render() {
        if (tvEmpty == null || recycler == null || adapter == null) return;
        boolean empty = items == null || items.isEmpty();
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
        adapter.setData(items);
    }

    private void requestEdit(ChimwemweReferralModel referral) {
        if (getActivity() instanceof ParticipantFormHost) {
            ((ParticipantFormHost) getActivity()).launchReferralForm(referral);
        }
    }

    private void confirmDelete(ChimwemweReferralModel referral) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete referral?")
                .setMessage("This will permanently delete this referral.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> Threading.io(() -> {
                    try {
                        referral.setDelete_status("1");
                        FormUtils formUtils = new FormUtils(requireContext());
                        JSONObject form = formUtils.getFormJson("chimwemwe_referral");
                        CoreJsonFormUtils.populateJsonForm(form, new ObjectMapper().convertValue(referral, Map.class));
                        ChimwemweFormUtils.ensureFieldValue(form, "delete_status", "1");
                        form.put("entity_id", referral.getBaseEntityId());
                        ChimwemweFormUtils.saveRegistration(
                                ChimwemweFormUtils.processRegistration(form, "ec_chimwemwe_referral", null),
                                true
                        );
                    } catch (Exception e) {
                        Timber.e(e, "Delete referral failed");
                    }
                    Threading.main(this::refreshContent);
                }))
                .show();
    }

    public interface ParticipantFormHost {
        void launchReferralForm(@Nullable ChimwemweReferralModel referral);
        void reloadParticipant();
    }
}
