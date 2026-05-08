package com.bluecodeltd.chimwemwe.chw.activity;

import android.content.Intent;
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
import com.bluecodeltd.chimwemwe.chw.util.Threading;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ChimwemweHotspotListActivity extends AppCompatActivity {

    private RecyclerView recycler;
    private View emptyState;
    private ProgressBar progress;
    private EditText searchBar;
    private HotspotAdapter adapter;
    private List<String[]> allRows = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chimwemwe_hotspot_list);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        progress   = findViewById(R.id.progress);
        recycler   = findViewById(R.id.recycler);
        emptyState = findViewById(R.id.empty_state);
        searchBar  = findViewById(R.id.search_bar);
        recycler.setLayoutManager(new LinearLayoutManager(this));

        adapter = new HotspotAdapter(new ArrayList<>());
        recycler.setAdapter(adapter);

        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { applyFilter(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });

        loadHotspots();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadHotspots();
    }

    private void loadHotspots() {
        progress.setVisibility(View.VISIBLE);
        Threading.io(() -> {
            List<String[]> rows = HotspotGroupDao.getHotspotsWithGroupNames();
            final List<String[]> result = rows != null ? rows : new ArrayList<>();
            Threading.main(() -> {
                progress.setVisibility(View.GONE);
                allRows = result;
                applyFilter(searchBar.getText().toString());
            });
        });
    }

    private void applyFilter(String query) {
        String q = query.trim().toLowerCase(Locale.getDefault());
        List<String[]> filtered = new ArrayList<>();
        for (String[] row : allRows) {
            String name = row[0] != null ? row[0].toLowerCase(Locale.getDefault()) : "";
            if (q.isEmpty() || name.contains(q)) filtered.add(row);
        }
        adapter.setData(filtered);
        emptyState.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        recycler.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ── Adapter ──────────────────────────────────────────────

    private class HotspotAdapter extends RecyclerView.Adapter<HotspotAdapter.VH> {
        private List<String[]> data;

        HotspotAdapter(List<String[]> data) { this.data = data; }

        void setData(List<String[]> d) { this.data = d; notifyDataSetChanged(); }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_hotspot_summary, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            String[] row       = data.get(pos);
            String name        = row[0] != null ? row[0] : "-";
            String groupNames  = row[1] != null ? row[1] : "";
            String countStr    = row[2] != null ? row[2] : "0";
            int count = 0;
            try { count = Integer.parseInt(countStr); } catch (NumberFormatException ignored) {}

            h.tvHotspotName.setText(name);
            h.tvGroupNames.setText(groupNames);
            h.tvCount.setText(count + (count == 1 ? " group" : " groups"));
            h.tvGroupNames.setVisibility(groupNames.isEmpty() ? View.GONE : View.VISIBLE);

            final String hotspot = name;
            h.root.setOnClickListener(v -> {
                Intent intent = new Intent(ChimwemweHotspotListActivity.this, HotspotGroupListActivity.class);
                intent.putExtra(HotspotGroupListActivity.EXTRA_FILTER_TYPE, "hotspot");
                intent.putExtra(HotspotGroupListActivity.EXTRA_FILTER_VALUE, hotspot);
                startActivity(intent);
            });
        }

        @Override public int getItemCount() { return data.size(); }

        class VH extends RecyclerView.ViewHolder {
            View root;
            TextView tvHotspotName, tvGroupNames, tvCount;
            VH(View v) {
                super(v);
                root          = v.findViewById(R.id.item_root);
                tvHotspotName = v.findViewById(R.id.tv_hotspot_name);
                tvGroupNames  = v.findViewById(R.id.tv_group_names);
                tvCount       = v.findViewById(R.id.tv_count);
            }
        }
    }
}
