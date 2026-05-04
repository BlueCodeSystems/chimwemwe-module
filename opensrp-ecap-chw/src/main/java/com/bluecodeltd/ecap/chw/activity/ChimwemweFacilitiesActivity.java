package com.bluecodeltd.ecap.chw.activity;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.bluecodeltd.ecap.chw.R;
import com.bluecodeltd.ecap.chw.application.ChwApplication;
import com.bluecodeltd.ecap.chw.dao.ChimwemweFacilitiesDao;
import com.bluecodeltd.ecap.chw.model.ChimwemweFacilityModel;
import com.bluecodeltd.ecap.chw.util.Threading;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import timber.log.Timber;

public class ChimwemweFacilitiesActivity extends AppCompatActivity {

    // Firebase RTDB REST endpoint (node is configurable in the importer; default is "facilities")
    private static final String FACILITIES_URL =
            "https://chimwemwe-app-default-rtdb.firebaseio.com/facilities.json";

    private FacilitiesAdapter adapter;
    private ProgressBar progress;
    private EditText etSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chimwemwe_facilities);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        progress = findViewById(R.id.progress);
        etSearch = findViewById(R.id.et_search);

        RecyclerView recycler = findViewById(R.id.recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FacilitiesAdapter();
        recycler.setAdapter(adapter);

        findViewById(R.id.btn_sync).setOnClickListener(v -> syncFromFirebase());

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                loadLocal(s == null ? "" : s.toString());
            }
        });

        loadLocal("");
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        findViewById(R.id.btn_sync).setEnabled(!loading);
    }

    private void loadLocal(String search) {
        Threading.io(() -> {
            List<ChimwemweFacilityModel> list = ChimwemweFacilitiesDao.getFacilities(search);
            Threading.main(() -> adapter.setData(list));
        });
    }

    private void syncFromFirebase() {
        setLoading(true);
        StringRequest req = new StringRequest(Request.Method.GET, FACILITIES_URL,
                resp -> Threading.io(() -> {
                    try {
                        if (resp == null || resp.trim().isEmpty() || "null".equals(resp.trim())) {
                            Threading.main(() -> Toast.makeText(this, "No facilities found in Firebase.", Toast.LENGTH_SHORT).show());
                            return;
                        }

                        JSONObject root = new JSONObject(resp);
                        int saved = 0;
                        Iterator<String> keys = root.keys();
                        while (keys.hasNext()) {
                            String key = keys.next();
                            JSONObject row = root.optJSONObject(key);
                            if (row == null) continue;
                            String name = row.optString("facility_name", "").trim();
                            if (name.isEmpty()) continue;
                            String district = row.optString("district", "").trim();
                            String province = row.optString("province", "").trim();
                            ChimwemweFacilitiesDao.upsertFacility(key, name, district, province);
                            saved++;
                        }

                        int finalSaved = saved;
                        Threading.main(() -> {
                            Toast.makeText(this, "Synced " + finalSaved + " facilities.", Toast.LENGTH_SHORT).show();
                            setLoading(false);
                            loadLocal(etSearch.getText() != null ? etSearch.getText().toString() : "");
                        });
                    } catch (Exception e) {
                        Timber.e(e, "Facility sync parse failed");
                        Threading.main(() -> {
                            setLoading(false);
                            Toast.makeText(this, "Sync failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        });
                    }
                }),
                err -> {
                    Timber.e(err, "Facility sync request failed");
                    setLoading(false);
                    Toast.makeText(this, "Sync failed. Check internet / Firebase rules.", Toast.LENGTH_LONG).show();
                });
        try {
            ChwApplication.getInstance().getRequestQueue().add(req);
        } catch (Exception e) {
            Timber.e(e, "Volley queue not available");
            setLoading(false);
            Toast.makeText(this, "Sync failed: request queue not ready.", Toast.LENGTH_LONG).show();
        }
    }

    private static class FacilitiesAdapter extends RecyclerView.Adapter<FacilitiesAdapter.VH> {
        private List<ChimwemweFacilityModel> data = new ArrayList<>();

        void setData(List<ChimwemweFacilityModel> d) {
            data = d != null ? d : new ArrayList<>();
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            View v = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chimwemwe_facility, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            ChimwemweFacilityModel m = data.get(position);
            h.tvName.setText(m.getFacilityName() != null ? m.getFacilityName() : "");
            String meta = "";
            if (m.getDistrict() != null && !m.getDistrict().trim().isEmpty()) meta += m.getDistrict().trim();
            if (m.getProvince() != null && !m.getProvince().trim().isEmpty()) {
                meta += (meta.isEmpty() ? "" : " • ") + m.getProvince().trim();
            }
            h.tvMeta.setText(meta);
        }

        @Override
        public int getItemCount() { return data.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvMeta;
            VH(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_facility_name);
                tvMeta = itemView.findViewById(R.id.tv_facility_meta);
            }
        }
    }
}

