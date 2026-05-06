package com.bluecodeltd.chimwemwe.chw.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bluecodeltd.chimwemwe.chw.R;
import com.bluecodeltd.chimwemwe.chw.dao.HotspotGroupDao;
import com.bluecodeltd.chimwemwe.chw.dao.ParticipantDao;
import com.bluecodeltd.chimwemwe.chw.model.ParticipantModel;
import com.bluecodeltd.chimwemwe.chw.util.Threading;

import java.util.ArrayList;
import java.util.List;

public class ChimwemweSummaryListActivity extends AppCompatActivity {

    public static final String EXTRA_TYPE         = "list_type";
    public static final String EXTRA_FILTER_VALUE = "filter_value";
    public static final String TYPE_FACILITIES          = "FACILITIES";
    public static final String TYPE_HOTSPOTS            = "HOTSPOTS";
    public static final String TYPE_HOTSPOTS_BY_FACILITY = "HOTSPOTS_BY_FACILITY";
    public static final String TYPE_PARTICIPANTS        = "PARTICIPANTS";
    public static final String TYPE_GRADUATES           = "GRADUATES";

    private String listType;
    private String filterValue;
    private RecyclerView recycler;
    private View emptyState;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chimwemwe_summary_list);

        listType    = getIntent().getStringExtra(EXTRA_TYPE);
        filterValue = getIntent().getStringExtra(EXTRA_FILTER_VALUE);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(titleFor(listType));
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        progress   = findViewById(R.id.progress);
        recycler   = findViewById(R.id.recycler);
        emptyState = findViewById(R.id.empty_state);
        recycler.setLayoutManager(new LinearLayoutManager(this));

        loadData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private String titleFor(String type) {
        if (TYPE_FACILITIES.equals(type))            return "Facilities";
        if (TYPE_HOTSPOTS.equals(type))              return "Hotspots";
        if (TYPE_HOTSPOTS_BY_FACILITY.equals(type))  return filterValue != null ? filterValue : "Hotspots";
        if (TYPE_PARTICIPANTS.equals(type))          return "Participants";
        if (TYPE_GRADUATES.equals(type))             return "Programme Graduates";
        return "List";
    }

    private void loadData() {
        progress.setVisibility(View.VISIBLE);
        if (TYPE_PARTICIPANTS.equals(listType) || TYPE_GRADUATES.equals(listType)) {
            loadParticipants();
        } else {
            loadGroupSummary();
        }
    }

    private void loadGroupSummary() {
        Threading.io(() -> {
            List<String[]> rows;
            if (TYPE_FACILITIES.equals(listType)) {
                rows = HotspotGroupDao.getDistinctFacilitiesWithCount();
            } else if (TYPE_HOTSPOTS_BY_FACILITY.equals(listType)) {
                rows = filterValue != null
                        ? HotspotGroupDao.getDistinctHotspotsByFacility(filterValue)
                        : HotspotGroupDao.getDistinctHotspotsWithCount();
            } else {
                rows = HotspotGroupDao.getDistinctHotspotsWithCount();
            }
            Threading.main(() -> {
                progress.setVisibility(View.GONE);
                boolean empty = rows == null || rows.isEmpty();
                emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
                recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
                if (!empty) {
                    recycler.setAdapter(new SummaryAdapter(rows));
                }
            });
        });
    }

    private void loadParticipants() {
        Threading.io(() -> {
            List<ParticipantModel> list = TYPE_GRADUATES.equals(listType)
                    ? ParticipantDao.getGraduatesWithSessions()
                    : ParticipantDao.getAllParticipantsWithSessions();
            Threading.main(() -> {
                progress.setVisibility(View.GONE);
                boolean empty = list == null || list.isEmpty();
                emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
                recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
                if (!empty) {
                    recycler.setAdapter(new ParticipantAdapter(list));
                }
            });
        });
    }

    // ── Adapter: Facility / Hotspot summary ───────────────────

    private class SummaryAdapter extends RecyclerView.Adapter<SummaryAdapter.VH> {
        private final List<String[]> data;
        SummaryAdapter(List<String[]> data) { this.data = data; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_summary_row, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            String[] row = data.get(pos);
            String name  = row[0] != null ? row[0] : "-";
            String count = row[1] != null ? row[1] : "0";
            h.tvName.setText(name);
            if (TYPE_FACILITIES.equals(listType)) {
                h.tvCount.setText(count + (Integer.parseInt(count) == 1 ? " hotspot" : " hotspots"));
            } else {
                h.tvCount.setText(count + (Integer.parseInt(count) == 1 ? " group" : " groups"));
            }
            h.itemView.setOnClickListener(v -> {
                if (TYPE_FACILITIES.equals(listType)) {
                    // Facility → drill into hotspots within this facility
                    Intent intent = new Intent(ChimwemweSummaryListActivity.this, ChimwemweSummaryListActivity.class);
                    intent.putExtra(EXTRA_TYPE, TYPE_HOTSPOTS_BY_FACILITY);
                    intent.putExtra(EXTRA_FILTER_VALUE, name);
                    startActivity(intent);
                } else {
                    // Hotspot (or hotspots-by-facility) → drill into groups
                    Intent intent = new Intent(ChimwemweSummaryListActivity.this, HotspotGroupListActivity.class);
                    intent.putExtra(HotspotGroupListActivity.EXTRA_FILTER_TYPE, "hotspot");
                    intent.putExtra(HotspotGroupListActivity.EXTRA_FILTER_VALUE, name);
                    startActivity(intent);
                }
            });
        }

        @Override public int getItemCount() { return data.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvCount;
            VH(View v) {
                super(v);
                tvName  = v.findViewById(R.id.tv_name);
                tvCount = v.findViewById(R.id.tv_count);
            }
        }
    }

    // ── Adapter: Participants ─────────────────────────────────

    private class ParticipantAdapter extends RecyclerView.Adapter<ParticipantAdapter.VH> {
        private final List<ParticipantModel> data;
        ParticipantAdapter(List<ParticipantModel> data) { this.data = data; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_summary_participant, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            ParticipantModel m = data.get(pos);
            String caregiver = trim(m.getCaregiverFirstName()) + " " + trim(m.getCaregiverSurname());
            String child     = trim(m.getChildFirstName()) + " " + trim(m.getChildSurname());
            h.tvCaregiver.setText(caregiver.trim().isEmpty() ? "—" : caregiver.trim());
            h.tvChild.setText(child.trim().isEmpty() ? "—" : "Child: " + child.trim());
            h.tvGroup.setText("Group: " + (m.getGroupId() != null ? m.getGroupId() : "—"));
            h.tvSessions.setText(m.getSessionsCompleted() + " / 14 sessions");
            h.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(ChimwemweSummaryListActivity.this, ChimwemweParticipantProfileActivity.class);
                intent.putExtra("participant_id", m.getId());
                startActivity(intent);
            });
        }

        @Override public int getItemCount() { return data.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvCaregiver, tvChild, tvGroup, tvSessions;
            VH(View v) {
                super(v);
                tvCaregiver = v.findViewById(R.id.tv_caregiver);
                tvChild     = v.findViewById(R.id.tv_child);
                tvGroup     = v.findViewById(R.id.tv_group);
                tvSessions  = v.findViewById(R.id.tv_sessions);
            }
        }
    }

    private static String trim(String s) { return s != null ? s.trim() : ""; }
}
