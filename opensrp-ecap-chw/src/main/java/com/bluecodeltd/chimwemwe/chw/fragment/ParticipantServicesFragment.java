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
import com.bluecodeltd.chimwemwe.chw.adapter.ParticipantServicesAdapter;
import com.bluecodeltd.chimwemwe.chw.model.HouseholdServiceReportModel;
import com.bluecodeltd.chimwemwe.chw.util.Threading;
import com.bluecodeltd.chimwemwe.chw.dao.HouseholdServiceReportDao;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

public class ParticipantServicesFragment extends Fragment implements ParticipantProfileSection {

    private TextView tvEmpty;
    private RecyclerView recycler;
    private ParticipantServicesAdapter adapter;
    private List<HouseholdServiceReportModel> items = new ArrayList<>();

    public ParticipantServicesFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_participant_services, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tvEmpty = view.findViewById(R.id.tv_empty);
        recycler = view.findViewById(R.id.recycler);

        adapter = new ParticipantServicesAdapter(new ParticipantServicesAdapter.Listener() {
            @Override
            public void onEdit(@NonNull HouseholdServiceReportModel service) {
                requestEdit(service);
            }

            @Override
            public void onDelete(@NonNull HouseholdServiceReportModel service) {
                confirmDelete(service);
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
            host.setServicesCount(0);
            return;
        }

        Threading.io(() -> {
            // Option 2 scoping: services are linked per-participant by storing the
            // participant code in the form's household_id field, which surfaces on
            // the ec_household_service_report.household_id column.
            List<HouseholdServiceReportModel> loaded = null;
            try {
                loaded = HouseholdServiceReportDao.getServicesByHousehold(participantCode);
            } catch (Exception e) {
                Timber.e(e, "Load participant services failed");
            }
            final List<HouseholdServiceReportModel> finalLoaded = loaded != null ? loaded : new ArrayList<>();
            Threading.main(() -> {
                items = finalLoaded;
                render();
                host.setServicesCount(finalLoaded.size());
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

    private void requestEdit(HouseholdServiceReportModel service) {
        if (getActivity() instanceof ParticipantFormHost) {
            ((ParticipantFormHost) getActivity()).launchServiceForm(service);
        }
    }

    private void confirmDelete(HouseholdServiceReportModel service) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete service record?")
                .setMessage("This will permanently delete this service record.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> Threading.io(() -> {
                    HouseholdServiceReportDao.deleteHouseholdService(service.getBase_entity_id());
                    Threading.main(this::refreshContent);
                }))
                .show();
    }

    public interface ParticipantFormHost {
        void launchServiceForm(@Nullable HouseholdServiceReportModel service);
        void reloadParticipant();
    }
}
