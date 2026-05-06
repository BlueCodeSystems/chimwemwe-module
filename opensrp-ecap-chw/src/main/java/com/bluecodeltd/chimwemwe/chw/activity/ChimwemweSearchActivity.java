package com.bluecodeltd.chimwemwe.chw.activity;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.bluecodeltd.chimwemwe.chw.BuildConfig;
import com.bluecodeltd.chimwemwe.chw.R;
import com.bluecodeltd.chimwemwe.chw.application.ChwApplication;
import com.bluecodeltd.chimwemwe.chw.dao.ChimwemweIndexDao;
import com.bluecodeltd.chimwemwe.chw.model.ChimwemweIndexModel;
import com.google.android.material.chip.ChipGroup;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import es.dmoral.toasty.Toasty;

/**
 * Advanced Search Activity.
 *
 * Flow:
 *  1. Authenticate against PMP API  →  POST /auth/login  →  get token
 *  2. Use token + district (from Keycloak SharedPrefs) to fetch:
 *       VCA chip      →  GET /child/district/{district}
 *       Household/Caregiver chip → GET /household/district/{district}
 *       All chip      →  both endpoints, merged
 *  3. Filter returned records locally by the typed search text.
 *  4. Let the caseworker save selected records into ec_chimwemwe_index.
 */
public class ChimwemweSearchActivity extends AppCompatActivity {

    /** Pass true to get a selected record back via setResult instead of saving to the index. */
    public static final String EXTRA_SELECTION_MODE = "selection_mode";

    /** Intent extras returned when EXTRA_SELECTION_MODE is true and the user picks a record. */
    public static final String RESULT_FIRST_NAME   = "result_first_name";
    public static final String RESULT_LAST_NAME    = "result_last_name";
    public static final String RESULT_GENDER       = "result_gender";
    public static final String RESULT_BIRTHDATE    = "result_birthdate";
    public static final String RESULT_UNIQUE_ID     = "result_unique_id";
    public static final String RESULT_HOUSEHOLD_ID  = "result_household_id";
    public static final String RESULT_CAREGIVER_NAME = "result_caregiver_name";

    // PMP authentication endpoint — credentials read from BuildConfig (set in local.properties)
    private static final String PMP_LOGIN_URL  = "https://pmp-api.bluecodeltd.com/auth/login";
    private static final String PMP_EMAIL      = BuildConfig.PMP_EMAIL;
    private static final String PMP_PASSWORD   = BuildConfig.PMP_PASSWORD;

    // Data server base URL and district-scoped paths
    private static final String DATA_BASE_URL      = "https://server-dqa.bluecodeltd.com";
    private static final String ENDPOINT_CHILD     = "/child/district/";
    private static final String ENDPOINT_HOUSEHOLD = "/household/district/";

    private EditText   etSearchQuery;
    private ProgressBar searchProgressBar;
    private RecyclerView rvSearchResults;
    private View       layoutEmptyState;
    private TextView   tvEmptyMessage;
    private View       btnRetry;
    private TextView   tvResultsCount;
    private ChipGroup  filterChipGroup;

    private SearchResultAdapter adapter;
    /** Subset currently shown in the RecyclerView (after chip + text filter). */
    private final List<ChimwemweIndexModel> results = new ArrayList<>();
    /** Full cache of every record fetched from the server — never cleared between filter changes. */
    private final List<ChimwemweIndexModel> allResults = new ArrayList<>();

    private boolean selectionMode = false;

    /** Cached PMP token — re-authenticated automatically on 401 or if null. */
    private String pmpToken = null;

    /** District from Keycloak attributes (stored in SharedPreferences by DashboardActivity). */
    private String district = "";
    /** Lowercased/trimmed district for comparisons. */
    private String districtNormalized = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chimwemwe_search);

        selectionMode = getIntent().getBooleanExtra(EXTRA_SELECTION_MODE, false);

        Toolbar toolbar = findViewById(R.id.search_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(selectionMode ? "Search OVC Register" : "Advanced Search");
        }

        etSearchQuery      = findViewById(R.id.et_search_query);
        searchProgressBar  = findViewById(R.id.search_progress_bar);
        rvSearchResults    = findViewById(R.id.rv_search_results);
        layoutEmptyState   = findViewById(R.id.layout_empty_state);
        tvEmptyMessage     = findViewById(R.id.tv_empty_message);
        btnRetry           = findViewById(R.id.btn_retry);
        tvResultsCount     = findViewById(R.id.tv_results_count);
        filterChipGroup    = findViewById(R.id.filter_chip_group);

        btnRetry.setOnClickListener(v -> loadAll());

        adapter = new SearchResultAdapter(results, selectionMode, this::onItemAction);
        rvSearchResults.setLayoutManager(new LinearLayoutManager(this));
        rvSearchResults.setAdapter(adapter);

        // District is stored by DashboardActivity after Keycloak /userinfo call
        district = PreferenceManager.getDefaultSharedPreferences(this)
                .getString("district", "")
                .trim();
        districtNormalized = district.toLowerCase();

        // Re-filter the cached list whenever the chip selection changes
        filterChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> applyFilters());

        // Filter as the user types — no button press needed
        etSearchQuery.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilters();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Also support keyboard Search action and the search button
        etSearchQuery.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                applyFilters();
                return true;
            }
            return false;
        });
        findViewById(R.id.btn_search).setOnClickListener(v -> applyFilters());

        // Load all records immediately on open
        loadAll();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh district in case DashboardActivity updated SharedPrefs after login.
        String latest = PreferenceManager.getDefaultSharedPreferences(this).getString("district", "");
        latest = latest == null ? "" : latest.trim();
        if (!latest.equals(district)) {
            district = latest;
            districtNormalized = district.toLowerCase();
        }
    }

    // -------------------------------------------------------------------------
    // Load & filter orchestration
    // -------------------------------------------------------------------------

    /** Called once on open — fetches all records from both endpoints into allResults. */
    private void loadAll() {
        if (TextUtils.isEmpty(district)) {
            showEmptyState("District not set — please log in again");
            return;
        }
        showLoading(true);
        allResults.clear();
        results.clear();
        adapter.notifyDataSetChanged();

        if (pmpToken != null) {
            fetchBoth(pmpToken);
        } else {
            authenticate();
        }
    }

    /**
     * Applies the active chip + typed text to allResults and updates the RecyclerView.
     * Never makes a network call — purely in-memory.
     */
    private void applyFilters() {
        int checkedId = filterChipGroup.getCheckedChipId();
        String query  = etSearchQuery.getText().toString().trim().toLowerCase();

        List<ChimwemweIndexModel> filtered = new ArrayList<>(allResults);

        // Chip filter
        if (checkedId == R.id.chip_vca) {
            filtered.removeIf(m -> !"VCA".equals(m.getSubPopulation()));
        } else if (checkedId == R.id.chip_caregiver) {
            filtered.removeIf(m -> !"Household".equals(m.getSubPopulation()));
        }
        // chip_all (or nothing checked): keep everything

        // Text filter
        if (!TextUtils.isEmpty(query)) {
            filtered.removeIf(m -> {
                String name  = m.getFullName().toLowerCase();
                String uid   = m.getUniqueId() != null ? m.getUniqueId().toLowerCase()  : "";
                String phone = m.getPhone()    != null ? m.getPhone().toLowerCase()     : "";
                return !name.contains(query) && !uid.contains(query) && !phone.contains(query);
            });
        }

        showResults(filtered);
    }

    /** Step 1: POST credentials to PMP API to obtain a bearer token. */
    private void authenticate() {
        JSONObject body = new JSONObject();
        try {
            body.put("email", PMP_EMAIL);
            body.put("password", PMP_PASSWORD);
        } catch (Exception e) {
            showEmptyState("Authentication error. Please try again.");
            return;
        }

        // Use a StringRequest so we can log the raw response and parse it ourselves
        StringRequest loginRequest = new StringRequest(
                Request.Method.POST,
                PMP_LOGIN_URL,
                response -> {
                    timber.log.Timber.d("PMP login raw response: %s", response);
                    String token = extractToken(response);
                    if (TextUtils.isEmpty(token)) {
                        showEmptyState("Could not retrieve authentication token.\n\nServer response:\n"
                                + response.substring(0, Math.min(response.length(), 400)), true);
                        return;
                    }
                    pmpToken = token;
                    fetchBoth(pmpToken);
                },
                error -> {
                    String msg = "Authentication failed.";
                    if (error.networkResponse != null) {
                        msg += " HTTP " + error.networkResponse.statusCode;
                        try {
                            msg += "\n" + new String(error.networkResponse.data);
                        } catch (Exception ignored) {}
                    } else if (error.getMessage() != null) {
                        msg += "\n" + error.getMessage();
                    }
                    timber.log.Timber.e("PMP login error: %s", msg);
                    showEmptyState(msg, true);
                }
        ) {
            @Override
            public byte[] getBody() {
                return body.toString().getBytes();
            }

            @Override
            public String getBodyContentType() {
                return "application/json; charset=utf-8";
            }
        };

        ChwApplication.getApplicationFlavor().chwAppInstance()
                .addToRequestQueue(loginRequest, "pmp_auth");
    }

    /**
     * Tries every common token field name (top-level and two levels nested).
     * Also handles plain-text responses and JWT auto-detection.
     */
    private String extractToken(String rawResponse) {
        if (TextUtils.isEmpty(rawResponse)) return null;
        String trimmed = rawResponse.trim();

        // Plain-text response that IS the token
        if (trimmed.startsWith("eyJ") && !trimmed.contains(" ") && !trimmed.startsWith("{")) {
            return trimmed;
        }

        String[] tokenKeys = {"token", "access_token", "accessToken",
                "jwt", "bearer", "id_token", "auth_token", "Authorization"};

        try {
            JSONObject obj = new JSONObject(trimmed);

            // Top-level candidates
            for (String key : tokenKeys) {
                String val = obj.optString(key, "");
                if (!val.isEmpty() && !val.equalsIgnoreCase("null")) return val;
            }

            // One and two levels nested — e.g. { "data": { "token": "..." } }
            for (String wrapperKey : new String[]{"data", "result", "user", "auth", "payload", "response"}) {
                JSONObject nested = obj.optJSONObject(wrapperKey);
                if (nested == null) continue;
                for (String key : tokenKeys) {
                    String val = nested.optString(key, "");
                    if (!val.isEmpty() && !val.equalsIgnoreCase("null")) return val;
                }
                // Two levels deep
                for (String wrapperKey2 : new String[]{"data", "result", "user", "auth"}) {
                    JSONObject nested2 = nested.optJSONObject(wrapperKey2);
                    if (nested2 == null) continue;
                    for (String key : tokenKeys) {
                        String val = nested2.optString(key, "");
                        if (!val.isEmpty() && !val.equalsIgnoreCase("null")) return val;
                    }
                }
            }

            // Last resort: scan ALL string values at top level for JWT pattern
            java.util.Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String val = obj.optString(key, "");
                if (val.startsWith("eyJ")) return val;
            }

        } catch (Exception e) {
            timber.log.Timber.e("extractToken parse error: %s", e.getMessage());
            // Response wasn't JSON — if it looks like a token, use it
            if (trimmed.length() > 20 && !trimmed.contains("\n") && !trimmed.contains("<")) {
                return trimmed;
            }
        }
        return null;
    }

    /** Fetch both endpoints in parallel; on completion populate allResults and apply filters. */
    private void fetchBoth(String token) {
        final List<ChimwemweIndexModel> merged = new ArrayList<>();
        final int[] done = {0};

        for (String[] pair : new String[][]{{ENDPOINT_CHILD, "vca"}, {ENDPOINT_HOUSEHOLD, "household"}}) {
            final String endpoint = pair[0];
            final String type     = pair[1];
            String url = DATA_BASE_URL + endpoint + Uri.encode(district);

            StringRequest request = new StringRequest(
                    Request.Method.GET,
                    url,
                    response -> {
                        synchronized (merged) {
                            merged.addAll(parseResponse(response, type));
                            done[0]++;
                        }
                        if (done[0] == 2) {
                            allResults.clear();
                            allResults.addAll(merged);
                            showLoading(false);
                            applyFilters();
                        }
                    },
                    error -> {
                        synchronized (merged) { done[0]++; }
                        if (done[0] == 2) {
                            allResults.clear();
                            allResults.addAll(merged);
                            showLoading(false);
                            if (allResults.isEmpty()) {
                                showEmptyState("Could not reach the server.\nCheck your connection and try again.", true);
                            } else {
                                applyFilters();
                            }
                        }
                        if (error.networkResponse != null && error.networkResponse.statusCode == 401) {
                            pmpToken = null;
                        }
                    }
            ) {
                @Override
                public Map<String, String> getHeaders() throws AuthFailureError {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Authorization", "Bearer " + token);
                    headers.put("Accept", "application/json");
                    return headers;
                }
            };

            ChwApplication.getApplicationFlavor().chwAppInstance()
                    .addToRequestQueue(request, "pmp_search_" + type);
        }
    }

    // -------------------------------------------------------------------------
    // Response parsing
    // -------------------------------------------------------------------------

    private List<ChimwemweIndexModel> parseResponse(String json, String type) {
        List<ChimwemweIndexModel> list = new ArrayList<>();
        try {
            // Log first record's raw keys to diagnose field name mismatches
            timber.log.Timber.d("PMP %s raw response (first 400): %s",
                    type, json.substring(0, Math.min(json.length(), 400)));

            JSONArray arr = null;
            if (json.trim().startsWith("[")) {
                arr = new JSONArray(json);
            } else {
                JSONObject obj = new JSONObject(json);
                for (String key : new String[]{"data", "results", "children", "households",
                        "clients", "vcas", "caregivers", "records", "items"}) {
                    JSONArray candidate = obj.optJSONArray(key);
                    if (candidate != null) { arr = candidate; break; }
                }
            }
            if (arr == null) {
                timber.log.Timber.w("parseResponse: no array found for type=%s", type);
                return list;
            }

            // Log keys of the very first item so we know the actual field names
            if (arr.length() > 0) {
                JSONObject first = arr.getJSONObject(0);
                timber.log.Timber.d("PMP %s first item keys: %s", type, first.keys().toString());
                timber.log.Timber.d("PMP %s first item: %s", type, first.toString());
            }

            for (int i = 0; i < arr.length(); i++) {
                ChimwemweIndexModel m = parseItem(arr.getJSONObject(i), type);
                if (m != null) list.add(m);
            }
        } catch (Exception e) {
            timber.log.Timber.e("parseResponse error for type=%s: %s", type, e.getMessage());
        }
        return list;
    }

    private ChimwemweIndexModel parseItem(JSONObject obj, String type) {
        try {
            ChimwemweIndexModel m = new ChimwemweIndexModel();

            m.setRemoteId(firstNonEmpty(obj, "id", "baseEntityId", "child_id", "household_id"));

            // Name — for VCA records this is the child's name
            String firstName = firstNonEmpty(obj, "first_name", "firstName", "child_first_name");
            String lastName  = firstNonEmpty(obj, "last_name",  "lastName",  "child_last_name",
                    "surname", "child_surname");

            // Household records may only supply a combined name or caregiver_first/last
            if (TextUtils.isEmpty(firstName) && TextUtils.isEmpty(lastName)) {
                String full = firstNonEmpty(obj, "name", "full_name", "fullName",
                        "caregiver_name", "caregiver_full_name");
                if (TextUtils.isEmpty(full)) {
                    firstName = firstNonEmpty(obj, "caregiver_first_name", "guardian_first_name");
                    lastName  = firstNonEmpty(obj, "caregiver_last_name",  "guardian_last_name",
                            "caregiver_surname");
                } else {
                    String[] parts = full.split(" ", 2);
                    firstName = parts[0];
                    lastName  = parts.length > 1 ? parts[1] : "";
                }
            }
            m.setFirstName(firstName);
            m.setLastName(lastName);

            // Gender / sex — child for VCA records
            m.setGender(firstNonEmpty(obj, "vca_gender", "gender", "sex", "child_gender", "child_sex"));

            // Date of birth — child for VCA records
            m.setBirthdate(firstNonEmpty(obj, "dob", "birthdate", "date_of_birth", "dateOfBirth",
                    "birth_date", "child_dob", "child_birthdate"));

            // VCA unique ID
            m.setUniqueId(firstNonEmpty(obj, "uid", "unique_id", "uniqueId", "vca_id",
                    "child_unique_id", "national_id"));

            // Household / caregiver OVC ID
            m.setHouseholdId(firstNonEmpty(obj, "household_id", "householdId",
                    "caregiver_household_id"));

            // caregiver_name is the API field — use it directly, fall back to first+last if absent
            String cgFull = obj.optString("caregiver_name", "").trim();
            if (TextUtils.isEmpty(cgFull)) {
                String cgFirst = firstNonEmpty(obj, "caregiver_first_name", "guardian_first_name");
                String cgLast  = firstNonEmpty(obj, "caregiver_last_name", "caregiver_surname", "guardian_last_name");
                cgFull = (cgFirst + " " + cgLast).trim();
            }
            if (!TextUtils.isEmpty(cgFull)) m.setCaregiverName(cgFull);

            m.setPhone(firstNonEmpty(obj, "phone", "phone_number", "phoneNumber", "mobile"));
            String recordDistrict = firstNonEmpty(obj, "district", "district_name");
            // Defensive: if server returns out-of-district records, drop them client-side too.
            if (!TextUtils.isEmpty(recordDistrict)
                    && !TextUtils.isEmpty(districtNormalized)
                    && !recordDistrict.trim().toLowerCase().equals(districtNormalized)) {
                return null;
            }
            m.setDistrict(recordDistrict);
            m.setFacility(firstNonEmpty(obj, "facility", "health_facility", "clinic"));
            m.setProvince(firstNonEmpty(obj, "province"));
            m.setCaseStatus(firstNonEmpty(obj, "case_status", "status", "caseStatus"));
            m.setSubPopulation(type.equals("vca") ? "VCA" : "Household");
            m.setSource("remote");
            m.setDateAdded(LocalDate.now().toString());

            return m;
        } catch (Exception e) {
            return null;
        }
    }

    /** Returns the value of the first key that exists and is non-empty in obj. */
    private String firstNonEmpty(JSONObject obj, String... keys) {
        for (String key : keys) {
            String val = obj.optString(key, "").trim();
            if (!val.isEmpty() && !val.equalsIgnoreCase("null")) return val;
        }
        return "";
    }

    private void showResults(List<ChimwemweIndexModel> filtered) {
        runOnUiThread(() -> {
            results.clear();
            if (filtered.isEmpty()) {
                adapter.notifyDataSetChanged();
                showEmptyState("No records match the selected filter");
            } else {
                results.addAll(filtered);
                adapter.notifyDataSetChanged();
                tvResultsCount.setText(filtered.size() + " found");
                rvSearchResults.setVisibility(View.VISIBLE);
                layoutEmptyState.setVisibility(View.GONE);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Save to local index
    // -------------------------------------------------------------------------

    private void onItemAction(ChimwemweIndexModel model) {
        if (selectionMode) {
            // caregiver_name is already in the VCA record from the API — use it directly
            Intent result = new Intent();
            result.putExtra(RESULT_FIRST_NAME,     model.getFirstName());
            result.putExtra(RESULT_LAST_NAME,      model.getLastName());
            result.putExtra(RESULT_GENDER,         model.getGender());
            result.putExtra(RESULT_BIRTHDATE,      model.getBirthdate());
            result.putExtra(RESULT_UNIQUE_ID,      model.getUniqueId());
            result.putExtra(RESULT_HOUSEHOLD_ID,   model.getHouseholdId());
            result.putExtra(RESULT_CAREGIVER_NAME, model.getCaregiverName() != null ? model.getCaregiverName() : "");
            setResult(Activity.RESULT_OK, result);
            finish();
        } else {
            new Thread(() -> {
                boolean saved = ChimwemweIndexDao.saveRecord(model);
                runOnUiThread(() -> {
                    if (saved) {
                        Toasty.success(this,
                                model.getFullName() + " saved to Chimwemwe Index",
                                Toast.LENGTH_SHORT, true).show();
                    } else {
                        Toasty.info(this,
                                "Record already exists in Chimwemwe Index",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }).start();
        }
    }

    // -------------------------------------------------------------------------
    // UI helpers
    // -------------------------------------------------------------------------

    private void showLoading(boolean loading) {
        runOnUiThread(() -> {
            searchProgressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
            if (loading) {
                btnRetry.setVisibility(View.GONE);
                layoutEmptyState.setVisibility(View.GONE);
                rvSearchResults.setVisibility(View.GONE);
                tvResultsCount.setText("");
            }
        });
    }

    private void showEmptyState(String message) {
        showEmptyState(message, false);
    }

    private void showEmptyState(String message, boolean showRetry) {
        runOnUiThread(() -> {
            tvEmptyMessage.setText(message);
            btnRetry.setVisibility(showRetry ? View.VISIBLE : View.GONE);
            layoutEmptyState.setVisibility(View.VISIBLE);
            rvSearchResults.setVisibility(View.GONE);
            tvResultsCount.setText("");
            searchProgressBar.setVisibility(View.GONE);
        });
    }

    // -------------------------------------------------------------------------
    // RecyclerView adapter
    // -------------------------------------------------------------------------

    private static class SearchResultAdapter
            extends RecyclerView.Adapter<SearchResultAdapter.ViewHolder> {

        interface OnItemClick { void onItem(ChimwemweIndexModel model); }

        private final List<ChimwemweIndexModel> data;
        private final boolean selectionMode;
        private final OnItemClick listener;

        SearchResultAdapter(List<ChimwemweIndexModel> data, boolean selectionMode, OnItemClick listener) {
            this.data = data;
            this.selectionMode = selectionMode;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            View view = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chimwemwe_search_result, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ChimwemweIndexModel model = data.get(position);

            holder.tvInitial.setText(model.getAvatarInitial());
            holder.tvName.setText(model.getFullName());
            holder.tvMeta.setText(buildMeta(model.getGender(), model.getBirthdate()));
            holder.tvId.setText(TextUtils.isEmpty(model.getUniqueId()) ? "" : "ID: " + model.getUniqueId());
            holder.tvSubpop.setText(model.getSubPopulation() != null ? model.getSubPopulation() : "");
            ((com.google.android.material.button.MaterialButton) holder.btnSave)
                    .setText(selectionMode ? "Select" : "Save");
            holder.btnSave.setOnClickListener(v -> listener.onItem(model));
        }

        /** Builds "Female • 01-05-2012 (12 yrs)" or "Male • —" etc. */
        private String buildMeta(String gender, String birthdate) {
            String g = formatGender(gender);
            String d = formatDob(birthdate);
            return g + " • " + d;
        }

        private String formatGender(String gender) {
            if (TextUtils.isEmpty(gender)) return "—";
            String g = gender.trim().toLowerCase();
            if (g.startsWith("m")) return "Male";
            if (g.startsWith("f")) return "Female";
            return gender;
        }

        private String formatDob(String birthdate) {
            if (TextUtils.isEmpty(birthdate)) return "—";
            String[] formats = {"yyyy-MM-dd", "dd-MM-yyyy", "dd/MM/yyyy", "MM/dd/yyyy", "yyyy/MM/dd"};
            for (String fmt : formats) {
                try {
                    LocalDate dob = LocalDate.parse(birthdate, DateTimeFormatter.ofPattern(fmt));
                    int years = Period.between(dob, LocalDate.now()).getYears();
                    String display = dob.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                    return display + " (" + years + " yrs)";
                } catch (Exception ignored) {}
            }
            return birthdate; // show raw value if unparseable
        }

        @Override
        public int getItemCount() { return data.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvInitial, tvName, tvMeta, tvId, tvSubpop;
            View btnSave;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvInitial = itemView.findViewById(R.id.tv_avatar_initial);
                tvName    = itemView.findViewById(R.id.tv_result_name);
                tvMeta    = itemView.findViewById(R.id.tv_result_meta);
                tvId      = itemView.findViewById(R.id.tv_result_id);
                tvSubpop  = itemView.findViewById(R.id.tv_result_subpop);
                btnSave   = itemView.findViewById(R.id.btn_save_result);
            }
        }
    }
}
