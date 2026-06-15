package com.bluecodeltd.chimwemwe.chw.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.bluecodeltd.chimwemwe.chw.R;
import com.bluecodeltd.chimwemwe.chw.application.ChwApplication;
import com.bluecodeltd.chimwemwe.chw.dao.ChimwemweFacilitiesDao;
import com.bluecodeltd.chimwemwe.chw.model.ChimwemweFacilityModel;
import com.bluecodeltd.chimwemwe.chw.util.Threading;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import timber.log.Timber;

public class ChimwemweFacilitiesActivity extends AppCompatActivity {

    public static final String EXTRA_SELECT_MODE = "select_mode";
    public static final String RESULT_FACILITY_NAME = "facility_name";
    public static final String RESULT_FACILITY_DISTRICT = "facility_district";
    public static final String RESULT_FACILITY_PROVINCE = "facility_province";

    private static final String FACILITIES_URL =
            "https://chimwemwe-app-default-rtdb.firebaseio.com/facilities.json";

    private FacilitiesAdapter adapter;
    private ProgressBar progress;
    private EditText etSearch;
    private boolean selectMode;
    private String userDistrict;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chimwemwe_facilities);

        selectMode = getIntent().getBooleanExtra(EXTRA_SELECT_MODE, false);

        SharedPreferences sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);
        userDistrict = selectMode ? sp.getString("district", "") : null;

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            if (selectMode) {
                String title = (userDistrict != null && !userDistrict.isEmpty())
                        ? "Facilities – " + userDistrict
                        : "Select Facility";
                getSupportActionBar().setTitle(title);
            }
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        progress = findViewById(R.id.progress);
        etSearch = findViewById(R.id.et_search);

        RecyclerView recycler = findViewById(R.id.recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FacilitiesAdapter(selectMode ? this::onFacilitySelected : null);
        recycler.setAdapter(adapter);

        if (selectMode) {
            findViewById(R.id.btn_sync).setVisibility(View.GONE);
            findViewById(R.id.btn_seed).setVisibility(View.GONE);
        } else {
            findViewById(R.id.btn_sync).setOnClickListener(v -> {
                if (isOnline()) {
                    syncFromFirebase();
                } else {
                    Toast.makeText(this, "No internet connection. Using local data.", Toast.LENGTH_SHORT).show();
                }
            });
            findViewById(R.id.btn_seed).setOnClickListener(v -> confirmAndSeedAll());
        }

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                loadLocal(s == null ? "" : s.toString());
            }
        });

        if (selectMode) {
            loadLocalThenSync();
        } else {
            loadLocalAndAutoSync();
        }
    }

    private void onFacilitySelected(ChimwemweFacilityModel facility) {
        Intent result = new Intent();
        result.putExtra(RESULT_FACILITY_NAME, facility.getFacilityName());
        result.putExtra(RESULT_FACILITY_DISTRICT, facility.getDistrict());
        result.putExtra(RESULT_FACILITY_PROVINCE, facility.getProvince());
        setResult(RESULT_OK, result);
        finish();
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        View sync = findViewById(R.id.btn_sync);
        View seed = findViewById(R.id.btn_seed);
        if (sync.getVisibility() == View.VISIBLE) sync.setEnabled(!loading);
        if (seed.getVisibility() == View.VISIBLE) seed.setEnabled(!loading);
    }

    private void confirmAndSeedAll() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Seed All Facilities")
                .setMessage("This will upload all Zambian health facilities to Firebase and save them locally. Continue?")
                .setPositiveButton("Seed", (d, w) -> seedAllToFirebase())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void seedAllToFirebase() {
        setLoading(true);
        Threading.io(() -> {
            try {
                InputStream is = getResources().openRawResource(R.raw.zambia_facilities);
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                JSONObject payload = new JSONObject();
                java.util.Map<String, Integer> seen = new java.util.HashMap<>();
                String line;
                int localCount = 0;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split("\t", -1);
                    if (parts.length < 3) continue;
                    String name = parts[0].trim();
                    String province = parts[1].trim();
                    String district = parts[2].trim();
                    if (name.isEmpty()) continue;

                    String baseKey = slugify(name + "_" + district);
                    String key = baseKey;
                    if (seen.containsKey(baseKey)) {
                        int n = seen.get(baseKey);
                        key = baseKey + "_" + n;
                        seen.put(baseKey, n + 1);
                    } else {
                        seen.put(baseKey, 1);
                    }

                    JSONObject row = new JSONObject();
                    row.put("facility_name", name);
                    row.put("province", province);
                    row.put("district", district);
                    payload.put(key, row);

                    ChimwemweFacilitiesDao.upsertFacility(key, name, district, province);
                    localCount++;
                }
                reader.close();

                int totalLocal = localCount;
                String jsonBody = payload.toString();
                Threading.main(() -> pushSeedPayload(jsonBody, totalLocal));
            } catch (Exception e) {
                Timber.e(e, "Facility seed build failed");
                Threading.main(() -> {
                    setLoading(false);
                    Toast.makeText(this, "Seed failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void pushSeedPayload(String jsonBody, int count) {
        try {
            com.android.volley.toolbox.StringRequest req = new com.android.volley.toolbox.StringRequest(
                    com.android.volley.Request.Method.PATCH, FACILITIES_URL,
                    response -> {
                        setLoading(false);
                        loadLocal(etSearch.getText() != null ? etSearch.getText().toString() : "");
                        Toast.makeText(this, "Seeded " + count + " facilities to Firebase.", Toast.LENGTH_LONG).show();
                    },
                    error -> {
                        Timber.e(error, "Facility seed Firebase push failed");
                        setLoading(false);
                        Toast.makeText(this, "Local save done but Firebase push failed.", Toast.LENGTH_LONG).show();
                    }) {
                @Override
                public byte[] getBody() {
                    return jsonBody.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                }

                @Override
                public String getBodyContentType() {
                    return "application/json; charset=utf-8";
                }
            };
            ((ChwApplication) ChwApplication.getInstance()).getRequestQueue().add(req);
        } catch (Exception e) {
            Timber.e(e, "Volley queue not available for seed push");
            setLoading(false);
            Toast.makeText(this, "Firebase push failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static String slugify(String s) {
        return s.toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_|_$", "")
                .substring(0, Math.min(s.length(), 60));
    }

    private void loadLocal(String search) {
        Threading.io(() -> {
            List<ChimwemweFacilityModel> list = ChimwemweFacilitiesDao.getFacilities(search, userDistrict);
            Threading.main(() -> adapter.setData(list));
        });
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    /** Select mode: show cached facilities for user's district, then refresh from Firebase if online. */
    private void loadLocalThenSync() {
        Threading.io(() -> {
            List<ChimwemweFacilityModel> list = ChimwemweFacilitiesDao.getFacilities("", userDistrict);
            Threading.main(() -> {
                adapter.setData(list);
                if (isOnline()) {
                    syncFromFirebase();
                }
            });
        });
    }

    private void loadLocalAndAutoSync() {
        Threading.io(() -> {
            List<ChimwemweFacilityModel> list = ChimwemweFacilitiesDao.getFacilities("", null);
            Threading.main(() -> {
                adapter.setData(list);
                if (list.isEmpty() && isOnline()) {
                    syncFromFirebase();
                }
            });
        });
    }

    private void showAddFacilityDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_facility, null);
        TextInputEditText etName = dialogView.findViewById(R.id.et_facility_name);
        TextInputEditText etDistrict = dialogView.findViewById(R.id.et_district);
        TextInputEditText etProvince = dialogView.findViewById(R.id.et_province);

        new AlertDialog.Builder(this)
                .setTitle("Add Facility")
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = etName.getText() != null ? etName.getText().toString().trim() : "";
                    String district = etDistrict.getText() != null ? etDistrict.getText().toString().trim() : "";
                    String province = etProvince.getText() != null ? etProvince.getText().toString().trim() : "";

                    if (name.isEmpty()) {
                        Toast.makeText(this, "Facility name is required.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    saveFacility(name, district, province);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveFacility(String name, String district, String province) {
        String id = UUID.randomUUID().toString();
        Threading.io(() -> {
            ChimwemweFacilitiesDao.upsertFacility(id, name, district, province);
            Threading.main(() -> {
                loadLocal(etSearch.getText() != null ? etSearch.getText().toString() : "");
                Toast.makeText(this, "Facility saved locally.", Toast.LENGTH_SHORT).show();
            });
        });
        pushToFirebase(id, name, district, province);
    }

    private void pushToFirebase(String id, String name, String district, String province) {
        String nodeUrl = "https://chimwemwe-app-default-rtdb.firebaseio.com/facilities/" + id + ".json";
        com.android.volley.toolbox.JsonObjectRequest req;
        try {
            JSONObject body = new JSONObject();
            body.put("facility_name", name);
            body.put("district", district);
            body.put("province", province);

            req = new com.android.volley.toolbox.JsonObjectRequest(
                    Request.Method.PUT, nodeUrl, body,
                    response -> Timber.d("Facility pushed to Firebase: %s", id),
                    error -> Timber.e(error, "Facility push to Firebase failed")
            );
        } catch (Exception e) {
            Timber.e(e, "Facility push body build failed");
            return;
        }
        try {
            ((ChwApplication) ChwApplication.getInstance()).getRequestQueue().add(req);
        } catch (Exception e) {
            Timber.e(e, "Volley queue not available for facility push");
        }
    }

    private void syncFromFirebase() {
        setLoading(true);
        StringRequest req = new StringRequest(Request.Method.GET, FACILITIES_URL,
                resp -> Threading.io(() -> {
                    try {
                        if (resp == null || resp.trim().isEmpty() || "null".equals(resp.trim())) {
                            Threading.main(() -> {
                                setLoading(false);
                                Toast.makeText(this, "No facilities found in Firebase.", Toast.LENGTH_SHORT).show();
                            });
                            return;
                        }

                        JSONObject root = new JSONObject(resp);
                        java.util.Map<String, String[]> batch = new java.util.LinkedHashMap<>();
                        Iterator<String> keys = root.keys();
                        while (keys.hasNext()) {
                            String key = keys.next();
                            JSONObject row = root.optJSONObject(key);
                            if (row == null) continue;
                            String name = row.optString("facility_name", "").trim();
                            if (name.isEmpty()) continue;
                            String district = row.optString("district", "").trim();
                            String province = row.optString("province", "").trim();
                            batch.put(key, new String[]{name, district, province});
                        }

                        ChimwemweFacilitiesDao.batchUpsert(batch);
                        Threading.main(() -> {
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
                    Threading.main(() -> {
                        setLoading(false);
                        Toast.makeText(this, "Sync failed. Check internet / Firebase rules.", Toast.LENGTH_LONG).show();
                    });
                });
        try {
            ((ChwApplication) ChwApplication.getInstance()).getRequestQueue().add(req);
        } catch (Exception e) {
            Timber.e(e, "Volley queue not available");
            setLoading(false);
            Toast.makeText(this, "Sync failed: request queue not ready.", Toast.LENGTH_LONG).show();
        }
    }

    interface OnFacilityClickListener {
        void onFacilityClick(ChimwemweFacilityModel facility);
    }

    private static class FacilitiesAdapter extends RecyclerView.Adapter<FacilitiesAdapter.VH> {
        private List<ChimwemweFacilityModel> data = new ArrayList<>();
        private final OnFacilityClickListener listener;

        FacilitiesAdapter(OnFacilityClickListener listener) {
            this.listener = listener;
        }

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
            if (listener != null) {
                h.itemView.setOnClickListener(v -> listener.onFacilityClick(m));
            }
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
