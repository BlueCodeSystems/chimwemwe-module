package com.bluecodeltd.chimwemwe.chw.activity;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
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
import com.bluecodeltd.chimwemwe.chw.model.HotspotGroupModel;
import com.bluecodeltd.chimwemwe.chw.model.ParticipantModel;
import com.bluecodeltd.chimwemwe.chw.util.Threading;
import com.bluecodeltd.chimwemwe.chw.view_holder.ChimwemweGroupViewHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ChimwemweSummaryListActivity extends AppCompatActivity {

    public static final String EXTRA_TYPE          = "list_type";
    public static final String EXTRA_FILTER_VALUE  = "filter_value";
    public static final String TYPE_FACILITIES            = "FACILITIES";
    public static final String TYPE_HOTSPOTS              = "HOTSPOTS";
    public static final String TYPE_HOTSPOTS_BY_FACILITY  = "HOTSPOTS_BY_FACILITY";
    public static final String TYPE_PARTICIPANTS          = "PARTICIPANTS";
    public static final String TYPE_GRADUATES             = "GRADUATES";

    private static final int COLOR_NEW_BAR   = Color.parseColor("#94A3B8");
    private static final int COLOR_ACT_BAR   = Color.parseColor("#0284C7");
    private static final int COLOR_DONE_BAR  = Color.parseColor("#0284C7");
    private static final int COLOR_NEW_ICON  = Color.parseColor("#F1F5F9");
    private static final int COLOR_ACT_ICON  = Color.parseColor("#E0F2FE");
    private static final int COLOR_DONE_ICON = Color.parseColor("#DCFCE7");
    private static final int COLOR_NEW_TEXT  = Color.parseColor("#64748B");
    private static final int COLOR_ACT_TEXT  = Color.parseColor("#0284C7");
    private static final int COLOR_DONE_TEXT = Color.parseColor("#166534");

    private String listType;
    private String filterValue;
    private RecyclerView recycler;
    private View emptyState;
    private ProgressBar progress;
    private EditText searchBar;

    private List<String[]> allSummaryRows           = new ArrayList<>();
    private List<HotspotGroupModel> allGroups        = new ArrayList<>();
    private List<ParticipantModel> allParticipants   = new ArrayList<>();

    private SummaryAdapter summaryAdapter;
    private GroupsAdapter groupsAdapter;
    private ParticipantAdapter participantAdapter;

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
        searchBar  = findViewById(R.id.search_bar);
        recycler.setLayoutManager(new LinearLayoutManager(this));

        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { applyFilter(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });

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

    private boolean isGroupType() {
        return TYPE_HOTSPOTS.equals(listType) || TYPE_HOTSPOTS_BY_FACILITY.equals(listType);
    }

    private void loadData() {
        progress.setVisibility(View.VISIBLE);
        if (TYPE_PARTICIPANTS.equals(listType) || TYPE_GRADUATES.equals(listType)) {
            loadParticipants();
        } else if (isGroupType()) {
            loadGroups();
        } else {
            loadFacilitySummary();
        }
    }

    // ── Facilities: name + group count ───────────────────────

    private void loadFacilitySummary() {
        Threading.io(() -> {
            List<String[]> rows = HotspotGroupDao.getDistinctFacilitiesWithCount();
            final List<String[]> result = rows != null ? rows : new ArrayList<>();
            Threading.main(() -> {
                progress.setVisibility(View.GONE);
                allSummaryRows = result;
                summaryAdapter = new SummaryAdapter(new ArrayList<>(allSummaryRows));
                recycler.setAdapter(summaryAdapter);
                String q = searchBar.getText().toString();
                if (!q.isEmpty()) applyFilter(q);
                else showEmpty(allSummaryRows.isEmpty());
            });
        });
    }

    // ── Hotspots: show actual groups with hotspot + group name

    private void loadGroups() {
        Threading.io(() -> {
            List<HotspotGroupModel> groups;
            if (TYPE_HOTSPOTS_BY_FACILITY.equals(listType) && filterValue != null) {
                groups = HotspotGroupDao.getGroupsByFacility(filterValue);
            } else {
                groups = HotspotGroupDao.getAllGroups();
            }
            final List<HotspotGroupModel> result = groups != null ? groups : new ArrayList<>();
            Threading.main(() -> {
                progress.setVisibility(View.GONE);
                allGroups = result;
                groupsAdapter = new GroupsAdapter(new ArrayList<>(allGroups));
                recycler.setAdapter(groupsAdapter);
                String q = searchBar.getText().toString();
                if (!q.isEmpty()) applyFilter(q);
                else showEmpty(allGroups.isEmpty());
            });
        });
    }

    // ── Participants ─────────────────────────────────────────

    private void loadParticipants() {
        Threading.io(() -> {
            List<ParticipantModel> list = TYPE_GRADUATES.equals(listType)
                    ? ParticipantDao.getGraduatesWithSessions()
                    : ParticipantDao.getAllParticipantsWithSessions();
            final List<ParticipantModel> result = list != null ? list : new ArrayList<>();
            Threading.main(() -> {
                progress.setVisibility(View.GONE);
                allParticipants = result;
                participantAdapter = new ParticipantAdapter(new ArrayList<>(allParticipants));
                recycler.setAdapter(participantAdapter);
                String q = searchBar.getText().toString();
                if (!q.isEmpty()) applyFilter(q);
                else showEmpty(allParticipants.isEmpty());
            });
        });
    }

    // ── Filter ────────────────────────────────────────────────

    private void applyFilter(String query) {
        String q = query.trim().toLowerCase(Locale.getDefault());
        if (summaryAdapter != null) {
            List<String[]> filtered = new ArrayList<>();
            for (String[] row : allSummaryRows) {
                String name = row[0] != null ? row[0].toLowerCase(Locale.getDefault()) : "";
                if (q.isEmpty() || name.contains(q)) filtered.add(row);
            }
            summaryAdapter.setData(filtered);
            showEmpty(filtered.isEmpty());
        } else if (groupsAdapter != null) {
            List<HotspotGroupModel> filtered = new ArrayList<>();
            for (HotspotGroupModel m : allGroups) {
                String gname   = m.getGroupName()   != null ? m.getGroupName().toLowerCase(Locale.getDefault())   : "";
                String hotspot = m.getHotspotName() != null ? m.getHotspotName().toLowerCase(Locale.getDefault()) : "";
                String gid     = m.getGroupId()     != null ? m.getGroupId().toLowerCase(Locale.getDefault())     : "";
                if (q.isEmpty() || gname.contains(q) || hotspot.contains(q) || gid.contains(q)) filtered.add(m);
            }
            groupsAdapter.setData(filtered);
            showEmpty(filtered.isEmpty());
        } else if (participantAdapter != null) {
            List<ParticipantModel> filtered = new ArrayList<>();
            for (ParticipantModel m : allParticipants) {
                String first   = trim(m.getCaregiverFirstName()).toLowerCase(Locale.getDefault());
                String surname = trim(m.getCaregiverSurname()).toLowerCase(Locale.getDefault());
                String child   = (trim(m.getChildFirstName()) + " " + trim(m.getChildSurname())).toLowerCase(Locale.getDefault());
                String group   = m.getGroupId() != null ? m.getGroupId().toLowerCase(Locale.getDefault()) : "";
                if (q.isEmpty() || first.contains(q) || surname.contains(q) || child.contains(q) || group.contains(q)) {
                    filtered.add(m);
                }
            }
            participantAdapter.setData(filtered);
            showEmpty(filtered.isEmpty());
        }
    }

    private void showEmpty(boolean empty) {
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    // ── Adapter: Facility summary ─────────────────────────────

    private class SummaryAdapter extends RecyclerView.Adapter<SummaryAdapter.VH> {
        private List<String[]> data;
        SummaryAdapter(List<String[]> data) { this.data = data; }
        void setData(List<String[]> d) { this.data = d; notifyDataSetChanged(); }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_summary_row, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            String[] row  = data.get(pos);
            String name   = row[0] != null ? row[0] : "-";
            String count  = row[1] != null ? row[1] : "0";
            h.tvName.setText(name);
            h.tvCount.setText(count + (Integer.parseInt(count) == 1 ? " group" : " groups"));
            h.root.setOnClickListener(v -> {
                Intent intent = new Intent(ChimwemweSummaryListActivity.this, HotspotGroupListActivity.class);
                intent.putExtra(HotspotGroupListActivity.EXTRA_FILTER_TYPE, "facility");
                intent.putExtra(HotspotGroupListActivity.EXTRA_FILTER_VALUE, name);
                startActivity(intent);
            });
        }

        @Override public int getItemCount() { return data.size(); }

        class VH extends RecyclerView.ViewHolder {
            View root; TextView tvName, tvCount;
            VH(View v) {
                super(v);
                root    = v.findViewById(R.id.item_root);
                tvName  = v.findViewById(R.id.tv_name);
                tvCount = v.findViewById(R.id.tv_count);
            }
        }
    }

    // ── Adapter: Groups (hotspot + group name) ────────────────

    private class GroupsAdapter extends RecyclerView.Adapter<ChimwemweGroupViewHolder> {
        private List<HotspotGroupModel> data;
        GroupsAdapter(List<HotspotGroupModel> data) { this.data = data; }
        void setData(List<HotspotGroupModel> d) { this.data = d; notifyDataSetChanged(); }

        @NonNull @Override
        public ChimwemweGroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_hotspot_group, parent, false);
            return new ChimwemweGroupViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ChimwemweGroupViewHolder h, int pos) {
            HotspotGroupModel m = data.get(pos);
            String groupName = m.getGroupName()   != null ? m.getGroupName()   : "-";
            String groupId   = m.getGroupId()     != null ? m.getGroupId()     : "-";
            String hotspot   = m.getHotspotName() != null ? m.getHotspotName() : "";
            int sCount = m.getSessionsRecorded();
            int pCount = m.getParticipantCount();

            h.tvGroupName.setText(groupName);
            h.tvGroupId.setText(groupId);
            h.tvHotspotName.setText(hotspot);
            h.tvParticipantCount.setText(String.valueOf(pCount));
            h.tvSessionsRecorded.setText(sCount + " / 14 sessions");
            if (h.tvGroupInitials != null) h.tvGroupInitials.setText(initials(groupName));
            if (h.pbSessions != null)      h.pbSessions.setProgress(Math.min(sCount, 14));

            int barColor, iconColor, textColor; String badge;
            if (sCount >= 14) {
                barColor = COLOR_DONE_BAR; iconColor = COLOR_DONE_ICON; textColor = COLOR_DONE_TEXT; badge = "COMPLETE";
            } else if (sCount > 0) {
                barColor = COLOR_ACT_BAR;  iconColor = COLOR_ACT_ICON;  textColor = COLOR_ACT_TEXT;  badge = "ACTIVE";
            } else {
                barColor = COLOR_NEW_BAR;  iconColor = COLOR_NEW_ICON;  textColor = COLOR_NEW_TEXT;  badge = "NEW";
            }
            if (h.viewStatusBar != null) h.viewStatusBar.setBackgroundColor(barColor);
            if (h.flGroupIcon != null)   h.flGroupIcon.getBackground().setTint(barColor);
            if (h.tvGroupStatus != null) {
                h.tvGroupStatus.setText(badge);
                h.tvGroupStatus.setTextColor(textColor);
                h.tvGroupStatus.setBackgroundTintList(ColorStateList.valueOf(iconColor));
            }
            h.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(ChimwemweSummaryListActivity.this, HotspotGroupDetailActivity.class);
                intent.putExtra(HotspotGroupDetailActivity.EXTRA_GROUP_ID, m.getGroupId());
                startActivity(intent);
            });
        }

        @Override public int getItemCount() { return data.size(); }
    }

    // ── Adapter: Participants ─────────────────────────────────

    private class ParticipantAdapter extends RecyclerView.Adapter<ParticipantAdapter.VH> {
        private List<ParticipantModel> data;
        ParticipantAdapter(List<ParticipantModel> data) { this.data = data; }
        void setData(List<ParticipantModel> d) { this.data = d; notifyDataSetChanged(); }

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
            h.root.setOnClickListener(v -> {
                Intent intent = new Intent(ChimwemweSummaryListActivity.this, ChimwemweParticipantProfileActivity.class);
                intent.putExtra("participant_id", m.getId());
                startActivity(intent);
            });
        }

        @Override public int getItemCount() { return data.size(); }

        class VH extends RecyclerView.ViewHolder {
            View root; TextView tvCaregiver, tvChild, tvGroup, tvSessions;
            VH(View v) {
                super(v);
                root        = v.findViewById(R.id.item_root);
                tvCaregiver = v.findViewById(R.id.tv_caregiver);
                tvChild     = v.findViewById(R.id.tv_child);
                tvGroup     = v.findViewById(R.id.tv_group);
                tvSessions  = v.findViewById(R.id.tv_sessions);
            }
        }
    }

    private static String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "G";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return String.valueOf(parts[0].charAt(0)).toUpperCase();
        return (String.valueOf(parts[0].charAt(0)) + String.valueOf(parts[parts.length - 1].charAt(0))).toUpperCase();
    }

    private static String trim(String s) { return s != null ? s.trim() : ""; }
}
