package com.bluecodeltd.chimwemwe.chw.activity;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.res.ColorStateList;
import android.graphics.Color;

import com.bluecodeltd.chimwemwe.chw.R;
import com.bluecodeltd.chimwemwe.chw.dao.HotspotGroupDao;
import com.bluecodeltd.chimwemwe.chw.model.HotspotGroupModel;
import com.bluecodeltd.chimwemwe.chw.util.ChimwemweFormUtils;
import com.bluecodeltd.chimwemwe.chw.util.Threading;
import com.bluecodeltd.chimwemwe.chw.view_holder.ChimwemweGroupViewHolder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.vijay.jsonwizard.constants.JsonFormConstants;

import org.json.JSONArray;
import org.json.JSONObject;
import org.smartregister.family.util.JsonFormUtils;
import org.smartregister.family.util.Utils;
import org.smartregister.util.FormUtils;
import com.vijay.jsonwizard.domain.Form;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import timber.log.Timber;

public class HotspotGroupListActivity extends AppCompatActivity {

    public static final String EXTRA_FILTER_TYPE  = "filter_type";   // "facility" or "hotspot"
    public static final String EXTRA_FILTER_VALUE = "filter_value";

    private static final int REQUEST_CODE_SELECT_FACILITY = 49013;
    private static final int REQUEST_CODE_GROUP_FORM      = 2004;

    private RecyclerView recycler;
    private View emptyState;
    private EditText searchBar;
    private GroupAdapter adapter;
    private String filterType;
    private String filterValue;

    private List<HotspotGroupModel> allGroups = new ArrayList<>();

    // Held between facility-picker result and form launch
    private String pendingFacilityName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hotspot_group_list);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        filterType  = getIntent().getStringExtra(EXTRA_FILTER_TYPE);
        filterValue = getIntent().getStringExtra(EXTRA_FILTER_VALUE);
        if (filterValue != null && !filterValue.isEmpty() && getSupportActionBar() != null) {
            getSupportActionBar().setTitle(filterValue);
        }

        recycler   = findViewById(R.id.recycler_groups);
        emptyState = findViewById(R.id.empty_state);
        searchBar  = findViewById(R.id.search_bar);
        recycler.setLayoutManager(new LinearLayoutManager(this));

        adapter = new GroupAdapter(new ArrayList<>());
        recycler.setAdapter(adapter);

        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { applyFilter(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });

        FloatingActionButton fab = findViewById(R.id.fab_add_group);
        fab.setOnClickListener(v -> startRegistration());

        loadGroups();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadGroups();
    }

    private void loadGroups() {
        Threading.io(() -> {
            List<HotspotGroupModel> groups;
            if ("facility".equals(filterType) && filterValue != null && !filterValue.isEmpty()) {
                groups = HotspotGroupDao.getGroupsByFacility(filterValue);
            } else if ("hotspot".equals(filterType) && filterValue != null && !filterValue.isEmpty()) {
                groups = HotspotGroupDao.getGroupsByHotspot(filterValue);
            } else {
                groups = HotspotGroupDao.getAllGroups();
            }
            final List<HotspotGroupModel> result = groups != null ? groups : new ArrayList<>();
            // Resolve which groups still have unsynced events on this device so the
            // adapter can flag them as "UNSYNCED" in the list. One batch query — no
            // per-row work.
            final java.util.Set<String> unsynced = HotspotGroupDao.getUnsyncedGroupIds();
            Threading.main(() -> {
                allGroups = result;
                adapter.setUnsyncedIds(unsynced);
                String q = searchBar.getText().toString();
                if (!q.isEmpty()) {
                    applyFilter(q);
                } else {
                    adapter.setData(new ArrayList<>(allGroups));
                    showEmpty(allGroups.isEmpty());
                }
            });
        });
    }

    private void applyFilter(String query) {
        String q = query.trim().toLowerCase(java.util.Locale.getDefault());
        if (q.isEmpty()) {
            adapter.setData(new ArrayList<>(allGroups));
            showEmpty(allGroups.isEmpty());
            return;
        }
        List<HotspotGroupModel> filtered = new ArrayList<>();
        for (HotspotGroupModel m : allGroups) {
            String name    = m.getGroupName()    != null ? m.getGroupName().toLowerCase(java.util.Locale.getDefault())    : "";
            String hotspot = m.getHotspotName()  != null ? m.getHotspotName().toLowerCase(java.util.Locale.getDefault())  : "";
            String gid     = m.getGroupId()      != null ? m.getGroupId().toLowerCase(java.util.Locale.getDefault())      : "";
            if (name.contains(q) || hotspot.contains(q) || gid.contains(q)) filtered.add(m);
        }
        adapter.setData(filtered);
        showEmpty(filtered.isEmpty());
    }

    private void showEmpty(boolean empty) {
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    // ── Standard registration flow ───────────────────────────

    /** Step 1: open facility picker (same as ChimwemweRegisterActivity.startRegistration). */
    private void startRegistration() {
        Intent intent = new Intent(this, ChimwemweFacilitiesActivity.class);
        intent.putExtra(ChimwemweFacilitiesActivity.EXTRA_SELECT_MODE, true);
        startActivityForResult(intent, REQUEST_CODE_SELECT_FACILITY);
    }

    /** Step 2: facility chosen — load form and pre-fill then launch wizard. */
    private void launchNewGroupForm(String facilityName) {
        Threading.io(() -> {
            try {
                FormUtils formUtils = new FormUtils(this);
                JSONObject form = formUtils.getFormJson("chimwemwe_group_register");
                if (form == null) return;

                // Auto-generate group_id
                String groupId = String.valueOf(new Random().nextInt(900_000_000));
                setFieldValue(form, "step1", "group_id", groupId);

                // Pre-fill facility
                setFieldValue(form, "step1", "nearest_health_facility", facilityName);

                // Pre-fill caseworker name
                try {
                    org.smartregister.repository.AllSharedPreferences prefs =
                            com.bluecodeltd.chimwemwe.chw.util.Utils.context().allSharedPreferences();
                    String anm  = prefs.fetchRegisteredANM();
                    String name = prefs.getANMPreferredName(anm);
                    if (name == null || name.isEmpty()) name = anm;
                    setFieldValue(form, "step1", "facilitator_name_1", name);
                } catch (Exception ignored) {}

                // Pre-fill province/district from shared prefs if available
                try {
                    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
                    String district = sp.getString("district", null);
                    String province = sp.getString("province", null);
                    if (district != null && !district.isEmpty())
                        setFieldValue(form, "step1", "district", district);
                    if (province != null && !province.isEmpty())
                        setFieldValue(form, "step1", "province", province);
                } catch (Exception ignored) {}

                final JSONObject finalForm = form;
                Threading.main(() -> {
                    try {
                        Intent intent = new Intent(this, Utils.metadata().familyFormActivity);
                        Form cfg = new Form();
                        cfg.setWizard(true);
                        cfg.setHideSaveLabel(true);
                        cfg.setNextLabel(getString(R.string.next));
                        cfg.setPreviousLabel(getString(R.string.previous));
                        cfg.setSaveLabel(getString(R.string.submit));
                        cfg.setNavigationBackground(R.color.chimwemwe_primary);
                        intent.putExtra(JsonFormConstants.JSON_FORM_KEY.FORM, cfg);
                        intent.putExtra(JsonFormConstants.JSON_FORM_KEY.JSON, finalForm.toString());
                        startActivityForResult(intent, REQUEST_CODE_GROUP_FORM);
                    } catch (Exception e) {
                        Timber.e(e, "HotspotGroupListActivity: launch form");
                    }
                });
            } catch (Exception e) {
                Timber.e(e, "HotspotGroupListActivity: load form json");
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_SELECT_FACILITY) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                pendingFacilityName = data.getStringExtra(ChimwemweFacilitiesActivity.RESULT_FACILITY_NAME);
                launchNewGroupForm(pendingFacilityName != null ? pendingFacilityName : "");
            }
            return;
        }

        if (requestCode == REQUEST_CODE_GROUP_FORM
                && resultCode == Activity.RESULT_OK && data != null) {
            String jsonString = data.getStringExtra(org.smartregister.opd.utils.OpdConstants.JSON_FORM_EXTRA.JSON);
            if (jsonString == null) return;
            Threading.io(() -> {
                try {
                    JSONObject form  = new JSONObject(jsonString);
                    JSONObject step1 = form.optJSONObject("step1");
                    JSONObject step3 = form.optJSONObject("step3");

                    HotspotGroupModel m = new HotspotGroupModel();
                    String savedGroupId = fieldValue(step1, "group_id");
                    if (savedGroupId == null || savedGroupId.trim().isEmpty()) {
                        savedGroupId = String.valueOf(new Random().nextInt(900_000_000));
                    }
                    m.setGroupId(savedGroupId);
                    m.setGroupName(fieldValue(step1,             "group_name"));
                    m.setHotspotName(fieldValue(step1,           "hotspot_name"));
                    m.setProvince(fieldValue(step1,              "province"));
                    m.setDistrict(fieldValue(step1,              "district"));
                    m.setLocationOfSession(fieldValue(step1,     "location_of_session"));
                    m.setNearestHealthFacility(fieldValue(step1, "nearest_health_facility"));
                    m.setFacilitatorName1(fieldValue(step1,      "facilitator_name_1"));
                    m.setFacilitatorName2(fieldValue(step1,      "facilitator_name_2"));
                    if (step3 != null) {
                        m.setSession1Date(fieldValue(step3,  "session_1_date"));
                        m.setSession2Date(fieldValue(step3,  "session_2_date"));
                        m.setSession3Date(fieldValue(step3,  "session_3_date"));
                        m.setSession4Date(fieldValue(step3,  "session_4_date"));
                        m.setSession5Date(fieldValue(step3,  "session_5_date"));
                        m.setSession6Date(fieldValue(step3,  "session_6_date"));
                        m.setSession7Date(fieldValue(step3,  "session_7_date"));
                        m.setSession8Date(fieldValue(step3,  "session_8_date"));
                        m.setSession9Date(fieldValue(step3,  "session_9_date"));
                        m.setSession10Date(fieldValue(step3, "session_10_date"));
                        m.setSession11Date(fieldValue(step3, "session_11_date"));
                        m.setSession12Date(fieldValue(step3, "session_12_date"));
                        m.setSession13Date(fieldValue(step3, "session_13_date"));
                        m.setSession14Date(fieldValue(step3, "session_14_date"));
                    }
                    // Single write path: persist via the form/Event pipeline. ClientProcessor
                    // materializes the row in ec_chimwemwe_group from the classification rules,
                    // and the same Event is what gets synced to the server.
                    // (The previous direct HotspotGroupDao.insertGroup call caused duplicate
                    // rows because the same form submission was written twice.)
                    ChimwemweFormUtils.ensureFieldValue(form, "group_id", m.getGroupId());
                    ChimwemweFormUtils.saveRegistration(
                            ChimwemweFormUtils.processRegistration(form, "ec_chimwemwe_group", m.getGroupId()),
                            true
                    );

                    final String gid = m.getGroupId();
                    Threading.main(() -> {
                        Toast.makeText(this, "Group registered", Toast.LENGTH_SHORT).show();
                        Intent detail = new Intent(this, HotspotGroupDetailActivity.class);
                        detail.putExtra(HotspotGroupDetailActivity.EXTRA_GROUP_ID, gid);
                        startActivity(detail);
                    });
                } catch (Exception e) {
                    Timber.e(e, "HotspotGroupListActivity: save new group");
                    Threading.main(() ->
                            Toast.makeText(this, "Error saving group", Toast.LENGTH_SHORT).show());
                }
            });
        }
    }

    // ── Helpers ──────────────────────────────────────────────

    private void setFieldValue(JSONObject form, String stepKey, String fieldKey, String value) {
        if (value == null || value.isEmpty()) return;
        try {
            JSONObject step = form.optJSONObject(stepKey);
            if (step == null) return;
            JSONArray fields = step.optJSONArray("fields");
            if (fields == null) return;
            for (int i = 0; i < fields.length(); i++) {
                JSONObject field = fields.getJSONObject(i);
                if (fieldKey.equals(field.optString("key"))) {
                    field.put("value", value);
                    return;
                }
            }
        } catch (Exception ignored) {}
    }

    private String fieldValue(JSONObject step, String key) {
        try {
            if (step == null) return "";
            JSONArray fields = step.optJSONArray("fields");
            if (fields == null) return "";
            for (int i = 0; i < fields.length(); i++) {
                JSONObject field = fields.getJSONObject(i);
                if (key.equals(field.optString("key"))) {
                    String v = field.optString("value", "").trim();
                    return v.equals("null") ? "" : v;
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    private void openDetail(long groupId) {
        Intent intent = new Intent(this, HotspotGroupDetailActivity.class);
        intent.putExtra("group_id", groupId);
        startActivity(intent);
    }

    // ── Adapter ──────────────────────────────────────────────

    private static final int COLOR_NEW_BAR   = Color.parseColor("#94A3B8");
    private static final int COLOR_ACT_BAR   = Color.parseColor("#0284C7");
    private static final int COLOR_DONE_BAR  = Color.parseColor("#0284C7");
    private static final int COLOR_NEW_ICON  = Color.parseColor("#F1F5F9");
    private static final int COLOR_ACT_ICON  = Color.parseColor("#E0F2FE");
    private static final int COLOR_DONE_ICON = Color.parseColor("#DCFCE7");
    private static final int COLOR_NEW_TEXT  = Color.parseColor("#64748B");
    private static final int COLOR_ACT_TEXT  = Color.parseColor("#0284C7");
    private static final int COLOR_DONE_TEXT = Color.parseColor("#166534");

    class GroupAdapter extends RecyclerView.Adapter<ChimwemweGroupViewHolder> {
        private List<HotspotGroupModel> data;
        private java.util.Set<String> unsyncedIds = java.util.Collections.emptySet();

        GroupAdapter(List<HotspotGroupModel> data) { this.data = data; }

        void setData(List<HotspotGroupModel> d) {
            this.data = d != null ? d : new ArrayList<>();
            notifyDataSetChanged();
        }

        void setUnsyncedIds(java.util.Set<String> ids) {
            this.unsyncedIds = ids != null ? ids : java.util.Collections.<String>emptySet();
            notifyDataSetChanged();
        }

        @Override
        public ChimwemweGroupViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_hotspot_group, parent, false);
            return new ChimwemweGroupViewHolder(v);
        }

        @Override
        public void onBindViewHolder(ChimwemweGroupViewHolder h, int pos) {
            HotspotGroupModel m = data.get(pos);
            String groupName = m.getGroupName()   != null ? m.getGroupName()   : "-";
            String groupId   = m.getGroupId()     != null ? m.getGroupId()     : "-";
            String hotspot   = m.getHotspotName() != null ? m.getHotspotName() : "";
            int    sCount    = m.getSessionsRecorded();
            int    pCount    = m.getParticipantCount();

            h.tvGroupName.setText(groupName);
            h.tvGroupId.setText(groupId);
            h.tvHotspotName.setText(hotspot);
            h.tvParticipantCount.setText(String.valueOf(pCount));
            h.tvSessionsRecorded.setText(sCount + " / 14 sessions");
            if (h.tvGroupInitials != null) h.tvGroupInitials.setText(initials(groupName));
            if (h.pbSessions != null) h.pbSessions.setProgress(Math.min(sCount, 14));

            int barColor, iconColor, textColor;
            String badge;
            if (sCount >= 14) {
                barColor = COLOR_DONE_BAR; iconColor = COLOR_DONE_ICON;
                textColor = COLOR_DONE_TEXT; badge = "COMPLETE";
            } else if (sCount > 0) {
                barColor = COLOR_ACT_BAR; iconColor = COLOR_ACT_ICON;
                textColor = COLOR_ACT_TEXT; badge = "ACTIVE";
            } else {
                barColor = COLOR_NEW_BAR; iconColor = COLOR_NEW_ICON;
                textColor = COLOR_NEW_TEXT; badge = "NEW";
            }
            if (h.viewStatusBar != null) h.viewStatusBar.setBackgroundColor(barColor);
            if (h.flGroupIcon != null)   h.flGroupIcon.getBackground().setTint(barColor);
            if (h.tvGroupStatus != null) {
                h.tvGroupStatus.setText(badge);
                h.tvGroupStatus.setTextColor(textColor);
                h.tvGroupStatus.setBackgroundTintList(ColorStateList.valueOf(iconColor));
            }
            if (h.tvUnsyncedBadge != null) {
                boolean unsynced = m.getGroupId() != null
                        && unsyncedIds.contains(m.getGroupId().trim());
                h.tvUnsyncedBadge.setVisibility(unsynced ? View.VISIBLE : View.GONE);
            }

            h.itemView.setOnClickListener(v -> openDetail(m.getId()));
        }

        @Override
        public int getItemCount() { return data.size(); }
    }

    private static String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "G";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return String.valueOf(parts[0].charAt(0)).toUpperCase();
        return (String.valueOf(parts[0].charAt(0)) + String.valueOf(parts[parts.length - 1].charAt(0))).toUpperCase();
    }
}
