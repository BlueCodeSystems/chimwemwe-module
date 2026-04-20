package com.bluecodeltd.ecap.chw.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bluecodeltd.ecap.chw.R;
import com.bluecodeltd.ecap.chw.dao.HotspotGroupDao;
import com.bluecodeltd.ecap.chw.model.HotspotGroupModel;
import com.bluecodeltd.ecap.chw.util.Threading;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class HotspotGroupListActivity extends AppCompatActivity {

    private RecyclerView recycler;
    private View emptyState;
    private GroupAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hotspot_group_list);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        recycler = findViewById(R.id.recycler_groups);
        emptyState = findViewById(R.id.empty_state);
        recycler.setLayoutManager(new LinearLayoutManager(this));

        adapter = new GroupAdapter(new ArrayList<>());
        recycler.setAdapter(adapter);

        FloatingActionButton fab = findViewById(R.id.fab_add_group);
        fab.setOnClickListener(v -> openDetail(-1));

        loadGroups();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadGroups();
    }

    private void loadGroups() {
        Threading.io(() -> {
            List<HotspotGroupModel> groups = HotspotGroupDao.getAllGroups();
            Threading.main(() -> {
                adapter.setData(groups);
                boolean empty = groups == null || groups.isEmpty();
                emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
                recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
            });
        });
    }

    private void openDetail(long groupId) {
        Intent intent = new Intent(this, HotspotGroupDetailActivity.class);
        intent.putExtra("group_id", groupId);
        startActivity(intent);
    }

    // ── Adapter ──────────────────────────────────────────────

    class GroupAdapter extends RecyclerView.Adapter<GroupAdapter.VH> {
        private List<HotspotGroupModel> data;

        GroupAdapter(List<HotspotGroupModel> data) { this.data = data; }

        void setData(List<HotspotGroupModel> d) {
            this.data = d != null ? d : new ArrayList<>();
            notifyDataSetChanged();
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_hotspot_group, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH h, int pos) {
            HotspotGroupModel m = data.get(pos);
            h.tvGroupName.setText(m.getGroupName() != null ? m.getGroupName() : "-");
            h.tvHotspotName.setText(m.getHotspotName() != null ? m.getHotspotName() : "-");
            h.tvParticipants.setText(m.getParticipantCount() + " participants");
            h.tvSessions.setText(m.getSessionsRecorded() + "/14 sessions");
            h.itemView.setOnClickListener(v -> openDetail(m.getId()));
        }

        @Override
        public int getItemCount() { return data.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvGroupName, tvHotspotName, tvParticipants, tvSessions;
            VH(View v) {
                super(v);
                tvGroupName    = v.findViewById(R.id.tv_group_name);
                tvHotspotName  = v.findViewById(R.id.tv_hotspot_name);
                tvParticipants = v.findViewById(R.id.tv_participant_count);
                tvSessions     = v.findViewById(R.id.tv_sessions_recorded);
            }
        }
    }
}
