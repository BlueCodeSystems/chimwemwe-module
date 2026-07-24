package com.bluecodeltd.chimwemwe.chw.activity;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.bluecodeltd.chimwemwe.chw.BuildConfig;
import com.bluecodeltd.chimwemwe.chw.R;
import com.bluecodeltd.chimwemwe.chw.actionhelper.CSVGeneratorHelper;
import com.bluecodeltd.chimwemwe.chw.application.ChwApplication;
import com.bluecodeltd.chimwemwe.chw.contract.GenerateCSVContract;
import com.bluecodeltd.chimwemwe.chw.dao.HotspotGroupDao;
import com.bluecodeltd.chimwemwe.chw.dao.ParticipantDao;
import com.bluecodeltd.chimwemwe.chw.dao.SessionAttendanceParticipantDao;
import com.bluecodeltd.chimwemwe.chw.model.HotspotGroupModel;
import com.bluecodeltd.chimwemwe.chw.presenter.GenerateCSVPresenter;
import com.bluecodeltd.chimwemwe.chw.util.CsvFormImportService;
import com.bluecodeltd.chimwemwe.chw.util.DistrictNameUtils;
import com.bluecodeltd.chimwemwe.chw.activity.ChimwemweSummaryListActivity;
import com.bluecodeltd.chimwemwe.chw.util.Threading;
import com.bluecodeltd.chimwemwe.chw.util.UpdateManager;
import com.bluecodeltd.chimwemwe.chw.viewmodel.DashboardViewModel;
import com.github.javiersantos.appupdater.AppUpdater;
import com.github.mikephil.charting.charts.HorizontalBarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;

import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.chw.core.custom_views.NavigationMenu;
import org.smartregister.domain.FetchStatus;
import org.smartregister.job.SyncServiceJob;
import org.smartregister.receiver.SyncStatusBroadcastReceiver;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import timber.log.Timber;

public class DashboardActivity extends AppCompatActivity
        implements GenerateCSVContract.View, SyncStatusBroadcastReceiver.SyncStatusListener {

    private com.bluecodeltd.chimwemwe.chw.databinding.ActivityDashboardBinding binding;
    private GenerateCSVContract.Presenter presenter;
    private CSVGeneratorHelper csvGenerator;
    private Toolbar toolbar;
    private AppUpdater appUpdater;
    private DashboardViewModel dashboardViewModel;

    private static final int COLOR_MALE = Color.parseColor("#2563EB");
    private static final int COLOR_FEMALE = Color.parseColor("#DB2777");
    private static final int COLOR_UNSPECIFIED = Color.parseColor("#9CA3AF");

    private static final int COLOR_RETENTION = Color.parseColor("#2563EB");

    /** All non-deleted groups, used to (re)build the facility and group filters. */
    private final List<HotspotGroupModel> allChartGroups = new ArrayList<>();
    /** Groups backing the group spinner for the current facility scope (index 0 = sentinel). */
    private final List<HotspotGroupModel> attendanceGroups = new ArrayList<>();
    /** Facility labels backing the facility spinner (index 0 = "All facilities"). */
    private final List<String> attendanceFacilities = new ArrayList<>();
    /** Facility currently selected; null means "All facilities". */
    private String selectedAttendanceFacility = null;
    /** group_id currently selected; null means "all groups in the current facility scope". */
    private String selectedAttendanceGroupId = null;
    /** Guards the spinner listeners while we programmatically restore selections. */
    private boolean suppressFacilitySpinnerCallback = false;
    private boolean suppressGroupSpinnerCallback = false;

    private final Handler handler = new Handler();
    private Runnable runnable;
    private String phone = "";
    private static final int FORTY_FIVE_MINUTES = 2_700_000;
    private static final int REQUEST_CODE_IMPORT_CSV = 49011;
    private static final long AUTO_SYNC_COOLDOWN_MS = 60_000L;
    /** Time window (ms) within which onSyncStart is considered to belong to our own
     *  kickServerSync(false) from onResume. Anything outside this window is treated
     *  as user-initiated (toolbar sync icon, dashboard sync button, etc.). */
    private static final long AUTO_SYNC_OWNERSHIP_WINDOW_MS = 3_000L;
    private long lastAutoSyncMs = 0L;
    /** Set when we fire a non-user auto-sync. onSyncStart checks against this to decide
     *  whether the sync we're seeing is ours (suppress popup) or user-initiated (show popup). */
    private volatile long lastAutoSyncKickMs = 0L;
    /** True when the completion popup should be shown. Set by either btnSync taps or
     *  onSyncStart for any sync we didn't kick ourselves. */
    private boolean userInitiatedSync = false;

    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = com.bluecodeltd.chimwemwe.chw.databinding.ActivityDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        toolbar = binding.toolbarx;
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        toolbar.getOverflowIcon().setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP);

        binding.btnSync.setOnClickListener(v -> {
            userInitiatedSync = true;
            kickServerSync(true);
            loadData();
        });

        NavigationMenu.getInstance(this, null, toolbar);

        presenter = new GenerateCSVPresenter(this);
        csvGenerator = new CSVGeneratorHelper();
        appUpdater = new AppUpdater(this);
        UpdateManager.startOnce(this);
        setupDashboardTabs();

        // Wire each dashboard stat card to its drill-down screen.
        wireSummaryCard(R.id.row_facilities,   ChimwemweSummaryListActivity.TYPE_FACILITIES);
        wireSummaryCard(R.id.row_hotspots,     ChimwemweSummaryListActivity.TYPE_HOTSPOTS);
        wireSummaryCard(R.id.row_participants, ChimwemweSummaryListActivity.TYPE_PARTICIPANTS);
        wireSummaryCard(R.id.row_graduates,    ChimwemweSummaryListActivity.TYPE_GRADUATES);

        // Groups and Sessions both drill into the group list (sessions are tracked per group).
        View rowGroups = findViewById(R.id.row_groups);
        if (rowGroups != null) {
            rowGroups.setOnClickListener(v ->
                    startActivity(new Intent(this, HotspotGroupListActivity.class)));
        }
        View rowSessions = findViewById(R.id.row_sessions);
        if (rowSessions != null) {
            rowSessions.setOnClickListener(v ->
                    startActivity(new Intent(this, HotspotGroupListActivity.class)));
        }

        // Shared prefs
        Bundle extras = getIntent().getExtras();
        String username = extras != null ? extras.getString("username") : null;
        String password = extras != null ? extras.getString("password") : null;

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        String district = sp.getString("district", "");
        phone = sp.getString("phone", "");

        if (binding.dashFacilityName != null) {
            binding.dashFacilityName.setText(DistrictNameUtils.display(district));
        }
        String caseworkerName = sp.getString("caseworker_name", "");
        TextView dashName = findViewById(R.id.dash_caseworker_name);
        if (dashName != null) {
            dashName.setText(caseworkerName.isEmpty() ? "Chimwemwe" : caseworkerName);
            dashName.setOnClickListener(v -> startActivity(new Intent(this, FacilitatorProfileActivity.class)));
        }
        TextView dashFacility = findViewById(R.id.dash_facility_name);
        if (dashFacility != null) {
            dashFacility.setOnClickListener(v -> startActivity(new Intent(this, FacilitatorProfileActivity.class)));
        }
        View userProfilePill = findViewById(R.id.btn_user_profile);
        if (userProfilePill != null) {
            userProfilePill.setOnClickListener(v -> startActivity(new Intent(this, FacilitatorProfileActivity.class)));
        }

        // Token refresh on first launch
        if (username != null && password != null) {
            String code = sp.getString("code", "0000");
            if (!sp.contains("code") || code.equals("0000")) {
                getToken(username, password);
            }
        }

        // Observe Chimwemwe dashboard state
        dashboardViewModel = new ViewModelProvider(this).get(DashboardViewModel.class);
        dashboardViewModel.getState().observe(this, state -> {
            if (binding.dashProgressbar != null) {
                binding.dashProgressbar.setVisibility(View.GONE);
            }
            if (state == null) return;
            try {
                ((TextView) findViewById(R.id.stat_facilities)).setText(String.valueOf(state.getFacilitiesCount()));
                ((TextView) findViewById(R.id.stat_hotspots)).setText(String.valueOf(state.getHotspotsCount()));
                binding.statGroups.setText(String.valueOf(state.getGroupsCount()));
                binding.statParticipants.setText(String.valueOf(state.getParticipantsCount()));
                binding.statSessions.setText(state.getSessionsRecorded() + " / " + state.getMaxSessions());
                binding.statCompleted.setText(String.valueOf(state.getCompletedCount()));
                if (state.getLastUpdated() != null) {
                    binding.lastUpdated.setText(dtf.format(state.getLastUpdated()));
                }
            } catch (Exception e) {
                Timber.e(e, "DashboardActivity: state update failed");
            }
        });

        loadData();
        refreshData();
    }

    private void wireSummaryCard(int viewId, String listType) {
        View view = findViewById(viewId);
        if (view == null) return;
        view.setOnClickListener(v -> {
            Intent intent = new Intent(this, ChimwemweSummaryListActivity.class);
            intent.putExtra(ChimwemweSummaryListActivity.EXTRA_TYPE, listType);
            startActivity(intent);
        });
    }


    private void setupDashboardTabs() {
        TextView tabOverview = findViewById(R.id.tab_overview);
        TextView tabCharts = findViewById(R.id.tab_charts);
        View overview = findViewById(R.id.section_overview);
        View charts = findViewById(R.id.section_charts);
        if (tabOverview == null || tabCharts == null || overview == null || charts == null) return;

        View.OnClickListener showOverview = v -> setDashboardTab(true, tabOverview, tabCharts, overview, charts);
        View.OnClickListener showCharts = v -> setDashboardTab(false, tabOverview, tabCharts, overview, charts);
        tabOverview.setOnClickListener(showOverview);
        tabCharts.setOnClickListener(showCharts);
        setDashboardTab(true, tabOverview, tabCharts, overview, charts);
        setupAttendanceHelp();
    }

    /** Wires the collapsible "How to read these charts" helper above the attendance charts. */
    private void setupAttendanceHelp() {
        TextView toggle = findViewById(R.id.tv_attendance_help_toggle);
        View body = findViewById(R.id.tv_attendance_help_body);
        if (toggle == null || body == null) return;
        toggle.setOnClickListener(v -> {
            boolean show = body.getVisibility() != View.VISIBLE;
            body.setVisibility(show ? View.VISIBLE : View.GONE);
            toggle.setText(show ? "ⓘ  Hide chart help" : "ⓘ  How to read these charts");
        });
    }

    private void setDashboardTab(boolean overviewSelected, TextView tabOverview, TextView tabCharts, View overview, View charts) {
        overview.setVisibility(overviewSelected ? View.VISIBLE : View.GONE);
        charts.setVisibility(overviewSelected ? View.GONE : View.VISIBLE);

        int activeBg = getResources().getColor(R.color.chimwemwe_primary);
        int inactiveBg = Color.TRANSPARENT;
        int activeText = Color.WHITE;
        int inactiveText = getResources().getColor(R.color.chimwemwe_text_secondary);

        tabOverview.setBackgroundColor(overviewSelected ? activeBg : inactiveBg);
        tabOverview.setTextColor(overviewSelected ? activeText : inactiveText);
        tabCharts.setBackgroundColor(overviewSelected ? inactiveBg : activeBg);
        tabCharts.setTextColor(overviewSelected ? inactiveText : activeText);
    }

    private void loadData() {
        if (binding.dashProgressbar != null) {
            binding.dashProgressbar.setVisibility(View.VISIBLE);
        }
        dashboardViewModel.refresh();
        loadHomepageCharts();
    }

    private void loadHomepageCharts() {
        Threading.io(() -> {
            int[] childGenderCounts = ParticipantDao.getChildGenderCounts();
            List<HotspotGroupModel> groups = HotspotGroupDao.getAllGroups();
            Threading.main(() -> {
                if (isFinishing() || isDestroyed()) return;
                renderGenderPie(R.id.chart_child_gender, R.id.tv_child_gender_empty, childGenderCounts, "Children");
                setupAttendanceFilters(groups);
                renderGroupSessions(groups);
            });
        });
    }

    /**
     * Builds the facility + group filters for the attendance charts. The facility spinner narrows
     * which groups appear in the group spinner; the group spinner narrows to a single group. Both
     * selections are preserved across refreshes (and reset gracefully when their target disappears).
     */
    private void setupAttendanceFilters(List<HotspotGroupModel> groups) {
        Spinner facilitySpinner = findViewById(R.id.spinner_attendance_facility);
        if (facilitySpinner == null) return;

        allChartGroups.clear();
        if (groups != null) {
            for (HotspotGroupModel g : groups) {
                if (g == null || g.getGroupId() == null || g.getGroupId().trim().isEmpty()) continue;
                allChartGroups.add(g);
            }
        }

        // Distinct facilities (case-insensitive), sorted for a stable dropdown order.
        TreeMap<String, String> facilityByKey = new TreeMap<>();
        for (HotspotGroupModel g : allChartGroups) {
            String f = g.getNearestHealthFacility();
            if (f == null || f.trim().isEmpty()) continue;
            facilityByKey.putIfAbsent(f.trim().toLowerCase(), f.trim());
        }

        attendanceFacilities.clear();
        List<String> facilityLabels = new ArrayList<>();
        facilityLabels.add("All facilities");
        attendanceFacilities.add(null); // index 0 sentinel
        for (String display : facilityByKey.values()) {
            facilityLabels.add(display);
            attendanceFacilities.add(display);
        }

        // Restore facility selection if it still exists.
        int facilityIndex = 0;
        if (selectedAttendanceFacility != null) {
            for (int i = 1; i < attendanceFacilities.size(); i++) {
                if (selectedAttendanceFacility.equalsIgnoreCase(attendanceFacilities.get(i))) {
                    facilityIndex = i;
                    selectedAttendanceFacility = attendanceFacilities.get(i);
                    break;
                }
            }
            if (facilityIndex == 0) selectedAttendanceFacility = null; // facility gone
        }

        ArrayAdapter<String> facilityAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, facilityLabels);
        facilityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        suppressFacilitySpinnerCallback = true;
        facilitySpinner.setAdapter(facilityAdapter);
        facilitySpinner.setSelection(facilityIndex, false);
        facilitySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (suppressFacilitySpinnerCallback) {
                    suppressFacilitySpinnerCallback = false;
                    return;
                }
                selectedAttendanceFacility = position > 0 && position < attendanceFacilities.size()
                        ? attendanceFacilities.get(position) : null;
                selectedAttendanceGroupId = null; // group list changes with facility
                rebuildGroupSpinner();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        rebuildGroupSpinner();
    }

    /** (Re)builds the group spinner for the currently selected facility scope. */
    private void rebuildGroupSpinner() {
        Spinner groupSpinner = findViewById(R.id.spinner_attendance_group);
        if (groupSpinner == null) return;

        attendanceGroups.clear();
        List<String> labels = new ArrayList<>();
        labels.add(selectedAttendanceFacility == null ? "All groups" : "All groups at this facility");
        attendanceGroups.add(null); // index 0 sentinel = all groups in scope

        for (HotspotGroupModel g : allChartGroups) {
            if (selectedAttendanceFacility != null
                    && !selectedAttendanceFacility.equalsIgnoreCase(
                            g.getNearestHealthFacility() != null ? g.getNearestHealthFacility().trim() : "")) {
                continue;
            }
            String name = g.getGroupName() != null && !g.getGroupName().trim().isEmpty()
                    ? g.getGroupName().trim() : "(unnamed group)";
            labels.add(name);
            attendanceGroups.add(g);
        }

        // Restore group selection if it still exists in the current scope.
        int groupIndex = 0;
        if (selectedAttendanceGroupId != null) {
            for (int i = 1; i < attendanceGroups.size(); i++) {
                HotspotGroupModel g = attendanceGroups.get(i);
                if (g != null && selectedAttendanceGroupId.equals(g.getGroupId())) {
                    groupIndex = i;
                    break;
                }
            }
            if (groupIndex == 0) selectedAttendanceGroupId = null;
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        suppressGroupSpinnerCallback = true;
        groupSpinner.setAdapter(adapter);
        groupSpinner.setSelection(groupIndex, false);
        groupSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (suppressGroupSpinnerCallback) {
                    suppressGroupSpinnerCallback = false;
                    loadAttendanceForSelection();
                    return;
                }
                HotspotGroupModel g = position >= 0 && position < attendanceGroups.size()
                        ? attendanceGroups.get(position) : null;
                selectedAttendanceGroupId = g != null ? g.getGroupId() : null;
                loadAttendanceForSelection();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        // setSelection(..., false) does not reliably fire the listener; render explicitly.
        loadAttendanceForSelection();
    }

    private void loadAttendanceForSelection() {
        final String groupId = selectedAttendanceGroupId;
        final String facility = selectedAttendanceFacility;
        final String scope = computeScopeLabel(groupId, facility);
        Threading.io(() -> {
            int[][] counts;
            if (groupId != null && !groupId.trim().isEmpty()) {
                counts = SessionAttendanceParticipantDao.getChildAttendanceBySession(groupId);
            } else if (facility != null && !facility.trim().isEmpty()) {
                counts = SessionAttendanceParticipantDao.getChildAttendanceBySessionForFacility(facility);
            } else {
                counts = SessionAttendanceParticipantDao.getChildAttendanceBySession((String) null);
            }
            Threading.main(() -> {
                if (isFinishing() || isDestroyed()) return;
                renderAttendanceCharts(counts, scope);
            });
        });
    }

    /** Human-readable scope for the attendance subtitle, reflecting both filters. */
    private String computeScopeLabel(String groupId, String facility) {
        if (groupId != null && !groupId.trim().isEmpty()) {
            for (HotspotGroupModel g : attendanceGroups) {
                if (g != null && groupId.equals(g.getGroupId())) {
                    return g.getGroupName() != null && !g.getGroupName().trim().isEmpty()
                            ? g.getGroupName().trim() : "Selected group";
                }
            }
            return "Selected group";
        }
        if (facility != null && !facility.trim().isEmpty()) {
            return facility.trim() + " · all groups";
        }
        return "All groups";
    }

    private void renderGenderPie(int chartId, int emptyId, int[] counts, String centerLabel) {
        PieChart chart = findViewById(chartId);
        TextView empty = findViewById(emptyId);
        if (chart == null || empty == null) return;

        int total = (counts == null) ? 0 : counts[0] + counts[1] + counts[2];
        if (total == 0) {
            chart.setVisibility(View.GONE);
            empty.setVisibility(View.VISIBLE);
            return;
        }
        chart.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);

        List<PieEntry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        if (counts[0] > 0) { entries.add(new PieEntry(counts[0], "Male")); colors.add(COLOR_MALE); }
        if (counts[1] > 0) { entries.add(new PieEntry(counts[1], "Female")); colors.add(COLOR_FEMALE); }
        if (counts[2] > 0) { entries.add(new PieEntry(counts[2], "Unspecified")); colors.add(COLOR_UNSPECIFIED); }

        PieDataSet set = new PieDataSet(entries, "");
        set.setColors(colors);
        set.setValueTextColor(Color.WHITE);
        set.setValueTextSize(13f);
        set.setValueFormatter(new ValueFormatter() {
            @Override public String getFormattedValue(float value) {
                return String.valueOf((int) value);
            }
        });

        chart.setData(new PieData(set));
        chart.getDescription().setEnabled(false);
        chart.setEntryLabelColor(Color.WHITE);
        chart.setEntryLabelTextSize(12f);
        chart.setUsePercentValues(false);
        chart.setDrawHoleEnabled(true);
        chart.setHoleRadius(42f);
        chart.setTransparentCircleRadius(46f);
        chart.setHoleColor(Color.TRANSPARENT);
        chart.setCenterText(centerLabel + "\n" + total);
        chart.setCenterTextSize(13f);
        chart.getLegend().setWordWrapEnabled(true);
        chart.animateY(500);
        chart.invalidate();
    }

    /**
     * Renders the child session-attendance card for the given (null = all groups) scope:
     *  - A headline: overall complete-pair attendance (% of all recorded pairs that were complete).
     *  - One bar chart: number of complete caregiver-child pairs attending each session (1–14).
     * Tapping a bar shows that session's exact numbers. Sessions not yet held have no bar, but
     * their number still shows on the axis so the 1–14 sequence is always complete and in order.
     */
    private void renderAttendanceCharts(int[][] counts, String scope) {
        LineChart chart = findViewById(R.id.chart_child_attendance_retention);
        TextView empty = findViewById(R.id.tv_child_session_attendance_empty);
        TextView subtitle = findViewById(R.id.tv_child_session_attendance_subtitle);
        TextView headlinePct = findViewById(R.id.tv_attendance_headline_pct);
        TextView headlineSub = findViewById(R.id.tv_attendance_headline_sub);
        View headline = findViewById(R.id.attendance_headline);
        TextView detail = findViewById(R.id.tv_attendance_detail);
        if (chart == null || empty == null) return;

        // Per-session tallies, kept for the tap-to-inspect detail line.
        final int[] fullBySession = new int[14];
        final int[] recordedBySession = new int[14];
        List<Entry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        int totalComplete = 0;
        int totalRecorded = 0;
        int maxFull = 0;
        int sessionsRecorded = 0;
        for (int i = 0; i < 14; i++) {
            labels.add(String.valueOf(i + 1)); // full 1..14 axis, always
            int full = counts != null && i < counts.length && counts[i] != null && counts[i].length > 0 ? counts[i][0] : 0;
            int partial = counts != null && i < counts.length && counts[i] != null && counts[i].length > 1 ? counts[i][1] : 0;
            int absent = counts != null && i < counts.length && counts[i] != null && counts[i].length > 2 ? counts[i][2] : 0;
            int recorded = full + partial + absent;
            fullBySession[i] = full;
            recordedBySession[i] = recorded;
            if (recorded == 0) continue; // session not held yet: no bar, axis number stays

            entries.add(new Entry(i, full));
            totalComplete += full;
            totalRecorded += recorded;
            maxFull = Math.max(maxFull, full);
            sessionsRecorded++;
        }

        if (sessionsRecorded == 0) {
            chart.setVisibility(View.GONE);
            if (headline != null) headline.setVisibility(View.GONE);
            if (detail != null) detail.setVisibility(View.GONE);
            empty.setVisibility(View.VISIBLE);
            if (headlinePct != null) headlinePct.setText("—");
            if (headlineSub != null) headlineSub.setText("No sessions recorded yet.");
            if (subtitle != null) subtitle.setText(scope + " · no sessions recorded yet.");
            return;
        }

        chart.setVisibility(View.VISIBLE);
        if (headline != null) headline.setVisibility(View.VISIBLE);
        if (detail != null) detail.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);

        // ── Headline: overall complete-pair attendance ────────────
        int overallPct = totalRecorded > 0 ? Math.round((totalComplete * 100f) / totalRecorded) : 0;
        if (headlinePct != null) {
            headlinePct.setText(overallPct + "%");
            headlinePct.setTextColor(COLOR_RETENTION);
        }
        if (headlineSub != null) {
            headlineSub.setText(sessionsRecorded + (sessionsRecorded == 1 ? " session held · " : " sessions held · ")
                    + totalComplete + (totalComplete == 1 ? " complete pair" : " complete pairs"));
        }
        if (subtitle != null) {
            subtitle.setText(scope + " · " + sessionsRecorded
                    + (sessionsRecorded == 1 ? " session held" : " sessions held"));
        }
        if (detail != null) detail.setText("Tap a point to see that session’s numbers.");

        // ── Line chart: complete pairs attending each session ─────
        LineDataSet set = new LineDataSet(entries, "Complete pairs");
        set.setColor(COLOR_RETENTION);
        set.setLineWidth(2.5f);
        set.setCircleColor(COLOR_RETENTION);
        set.setCircleRadius(4.5f);
        set.setDrawCircleHole(true);
        set.setCircleHoleColor(Color.WHITE);
        set.setValueTextSize(10f);
        set.setValueTextColor(COLOR_RETENTION);
        set.setMode(LineDataSet.Mode.LINEAR);
        set.setValueFormatter(new ValueFormatter() {
            @Override public String getFormattedValue(float value) {
                return String.valueOf((int) value);
            }
        });
        LineData data = new LineData(set);
        styleLineChart(chart, data, labels);
        YAxis left = chart.getAxisLeft();
        left.setAxisMinimum(0f);
        left.setAxisMaximum(Math.max(1f, maxFull + 1f));
        left.setGranularity(1f);

        // Tap a bar → show that session's complete pairs and % of recorded.
        chart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override public void onValueSelected(Entry e, Highlight h) {
                if (detail == null || e == null) return;
                int s = Math.round(e.getX());
                if (s < 0 || s >= 14) return;
                int full = fullBySession[s];
                int recorded = recordedBySession[s];
                int pct = recorded > 0 ? Math.round((full * 100f) / recorded) : 0;
                detail.setText("Session " + (s + 1) + ": " + full
                        + (full == 1 ? " complete pair" : " complete pairs")
                        + " · " + pct + "% of " + recorded + " recorded");
            }
            @Override public void onNothingSelected() {
                if (detail != null) detail.setText("Tap a point to see that session’s numbers.");
            }
        });
        chart.invalidate();
    }

    /** Shared axis/legend styling for the per-session attendance line chart. */
    private void styleLineChart(LineChart chart, LineData data, List<String> labels) {
        chart.setData(data);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setScaleEnabled(false);
        chart.setDoubleTapToZoomEnabled(false);
        chart.setExtraBottomOffset(6f);

        XAxis x = chart.getXAxis();
        x.setValueFormatter(new IndexAxisValueFormatter(labels));
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setGranularity(1f);
        x.setGranularityEnabled(true);
        x.setDrawGridLines(false);
        x.setLabelCount(labels.size(), false);
        x.setAxisMinimum(-0.5f);
        x.setAxisMaximum(labels.size() - 0.5f);

        chart.getAxisLeft().setDrawGridLines(true);
        chart.getAxisRight().setEnabled(false);
        chart.animateX(500);
    }

    private void renderGroupSessions(List<HotspotGroupModel> groups) {
        HorizontalBarChart chart = findViewById(R.id.chart_group_sessions);
        TextView empty = findViewById(R.id.tv_group_sessions_empty);
        if (chart == null || empty == null) return;

        if (groups == null || groups.isEmpty()) {
            chart.setVisibility(View.GONE);
            empty.setVisibility(View.VISIBLE);
            return;
        }
        chart.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);

        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (int i = groups.size() - 1; i >= 0; i--) {
            HotspotGroupModel group = groups.get(i);
            String name = group.getGroupName() != null && !group.getGroupName().trim().isEmpty()
                    ? group.getGroupName().trim() : "(unnamed)";
            int index = labels.size();
            labels.add(name);
            entries.add(new BarEntry(index, group.getSessionsRecorded()));
        }

        BarDataSet set = new BarDataSet(entries, "Sessions completed");
        set.setColor(androidx.core.content.ContextCompat.getColor(this, R.color.chimwemwe_primary));
        set.setValueTextSize(10f);
        set.setValueFormatter(new ValueFormatter() {
            @Override public String getFormattedValue(float value) {
                return String.valueOf((int) value);
            }
        });

        BarData data = new BarData(set);
        data.setBarWidth(0.6f);
        chart.setData(data);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setFitBars(true);
        chart.setDrawValueAboveBar(true);
        chart.setScaleEnabled(false);

        XAxis x = chart.getXAxis();
        x.setValueFormatter(new IndexAxisValueFormatter(labels));
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setGranularity(1f);
        x.setGranularityEnabled(true);
        x.setDrawGridLines(false);
        x.setLabelCount(labels.size());

        YAxis left = chart.getAxisLeft();
        left.setAxisMinimum(0f);
        left.setAxisMaximum(14f);
        left.setGranularity(1f);

        YAxis right = chart.getAxisRight();
        right.setAxisMinimum(0f);
        right.setAxisMaximum(14f);
        right.setDrawLabels(false);

        float density = getResources().getDisplayMetrics().density;
        int perBar = Math.round(46 * density);
        int minHeight = Math.round(280 * density);
        int desiredHeight = Math.max(minHeight, groups.size() * perBar);
        ViewGroup.LayoutParams lp = chart.getLayoutParams();
        if (lp != null && lp.height != desiredHeight) {
            lp.height = desiredHeight;
            chart.setLayoutParams(lp);
        }

        chart.animateY(500);
        chart.invalidate();
    }

    private void refreshData() {
        handler.postDelayed(runnable = new Runnable() {
            public void run() {
                handler.postDelayed(runnable, FORTY_FIVE_MINUTES);
                loadData();
            }
        }, FORTY_FIVE_MINUTES);
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            SyncStatusBroadcastReceiver receiver = SyncStatusBroadcastReceiver.getInstance();
            if (receiver != null) receiver.addSyncStatusListener(this);
        } catch (Exception e) {
            Timber.e(e, "addSyncStatusListener");
        }
        kickServerSync(false);
        loadData();
    }

    @Override
    public void onSyncStart() {
        // Any sync that starts while the dashboard is visible and was NOT our own
        // onResume auto-sync is treated as user-initiated. This catches the toolbar/
        // drawer sync icon (from NavigationMenu in opensrp-ecap-chw-core) which does
        // not go through our btnSync click handler.
        long now = System.currentTimeMillis();
        boolean ours = (lastAutoSyncKickMs != 0L)
                && (now - lastAutoSyncKickMs <= AUTO_SYNC_OWNERSHIP_WINDOW_MS);
        if (!ours) userInitiatedSync = true;
        lastAutoSyncKickMs = 0L;
    }

    @Override
    public void onSyncInProgress(FetchStatus fetchStatus) {
        // No-op: incremental progress is not surfaced on the dashboard.
    }

    @Override
    public void onSyncComplete(FetchStatus fetchStatus) {
        // Always refresh local counts when sync finishes, regardless of who triggered it.
        loadData();

        // Only show the completion popup when the user explicitly initiated the sync ?
        // background/periodic syncs should not interrupt with a dialog.
        if (!userInitiatedSync) return;
        userInitiatedSync = false;

        if (isFinishing() || isDestroyed()) return;
        String message;
        if (fetchStatus == FetchStatus.fetched) {
            message = "Sync complete: your data is up to date.";
        } else if (fetchStatus == FetchStatus.nothingFetched) {
            message = "Sync complete: you are already up to date.";
        } else {
            String reason = fetchStatus != null && fetchStatus.displayValue() != null
                    ? fetchStatus.displayValue()
                    : "Please check your connection and try again.";
            message = "Sync failed: " + reason;
        }

        try {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Timber.e(e, "onSyncComplete toast");
        }
    }

    private void kickServerSync(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastAutoSyncMs < AUTO_SYNC_COOLDOWN_MS) {
            return;
        }
        lastAutoSyncMs = now;
        // Only the silent onResume auto-sync needs ownership marking. The force=true
        // path from btnSync already sets userInitiatedSync=true at the click site, so
        // marking it here would falsely suppress its own popup.
        if (!force) lastAutoSyncKickMs = now;
        try {
            SyncServiceJob.scheduleJobImmediately(SyncServiceJob.TAG);
        } catch (Exception e) {
            Timber.e(e, "kickServerSync failed");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(runnable);
        try {
            SyncStatusBroadcastReceiver receiver = SyncStatusBroadcastReceiver.getInstance();
            if (receiver != null) receiver.removeSyncStatusListener(this);
        } catch (Exception e) {
            Timber.e(e, "removeSyncStatusListener");
        }
    }

    // ?? Options menu ??????????????????????????????????????????????

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.dash_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.generate_pdf) {
            csvGenerator.generateCSVWithProgress(this, presenter, () ->
                    showCustomDialog(this,
                            getString(R.string.csv_generated_location, getString(R.string.app_name))));
        } else if (id == R.id.import_csv) {
            openCsvPicker();
        }
        return super.onOptionsItemSelected(item);
    }

    // ?? CSV contract ??????????????????????????????????????????????

    @Override
    public void showCSVGeneratedMessage(String filePath) {}

    @Override
    public void showError(String errorMessage) {}

    // ?? Keycloak token / credential refresh ???????????????????????

    private void getToken(final String username, final String password) {
        String url = "https://keycloak.zeir.smartregister.org/auth/realms/chimwemwe/protocol/openid-connect/token";
        StringRequest req = new StringRequest(Request.Method.POST, url,
                response -> {
                    try {
                        JSONObject obj = new JSONObject(response.trim());
                        String token = obj.getString("access_token");
                        getCreds(token);
                        loadData();
                    } catch (JSONException e) {
                        Timber.e(e, "getToken parse error");
                    }
                },
                error -> Timber.e("getToken error: %s", error.getMessage())) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("grant_type", "password");
                params.put("username", username);
                params.put("password", password);
                params.put("scope", "openid");
                params.put("client_id", BuildConfig.OAUTH_CLIENT_ID);
                params.put("client_secret", BuildConfig.OAUTH_CLIENT_SECRET);
                return params;
            }
        };
        ChwApplication.getApplicationFlavor().chwAppInstance().addToRequestQueue(req, "req_login");
    }

    private void getCreds(String token) {
        String url = "https://keycloak.zeir.smartregister.org/auth/realms/chimwemwe/protocol/openid-connect/userinfo";
        StringRequest req = new StringRequest(Request.Method.GET, url,
                response -> {
                    try {
                        JSONObject obj = new JSONObject(response);
                        SharedPreferences.Editor edit = PreferenceManager
                                .getDefaultSharedPreferences(this).edit();
                        edit.putString("sub",            obj.optString("sub"));
                        edit.putString("code",           obj.optString("code"));
                        edit.putString("caseworker_name",obj.optString("name"));
                        edit.putString("given_name",     obj.optString("given_name"));
                        edit.putString("family_name",    obj.optString("family_name"));
                        edit.putString("province",       obj.optString("province"));
                        edit.putString("partner",        obj.optString("partner"));
                        edit.putString("phone",          obj.optString("phone"));
                        edit.putString("district",       obj.optString("district"));
                        edit.putString("facility",       obj.optString("facility"));
                        edit.putString("email",          obj.optString("email"));
                        edit.putString("nrc",            obj.optString("nrc"));
                        edit.apply();
                        recreate();
                    } catch (JSONException e) {
                        Timber.e(e, "getCreds parse error");
                    }
                },
                error -> Timber.e("getCreds error: %s", error.getMessage())) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };
        ChwApplication.getApplicationFlavor().chwAppInstance().addToRequestQueue(req, "req_creds");
    }

    // ?? CSV import ????????????????????????????????????????????????

    private void openCsvPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"text/csv", "text/comma-separated-values", "application/vnd.ms-excel"});
        startActivityForResult(intent, REQUEST_CODE_IMPORT_CSV);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CODE_IMPORT_CSV || resultCode != RESULT_OK || data == null) return;

        List<Uri> csvUris = extractCsvUris(data);
        if (csvUris.isEmpty()) {
            showCustomDialog(this, "No CSV file selected.");
            return;
        }

        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setIndeterminate(false);
        progressDialog.setMax(100);
        progressDialog.setProgress(0);
        progressDialog.setMessage("Preparing import...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        Threading.io(() -> {
            final int[] lastPercent = {-1};
            Map<Uri, Integer> perFileRows = new LinkedHashMap<>();
            int totalRowsAllFiles = 0;
            for (Uri uri : csvUris) {
                int rows = CsvFormImportService.getDataRowCount(this, uri);
                int normalized = Math.max(1, rows);
                perFileRows.put(uri, normalized);
                totalRowsAllFiles += normalized;
            }
            final int overallTotal = totalRowsAllFiles;

            int importedAll = 0, skippedAll = 0, failedAll = 0, processedAll = 0;
            List<String> summary = new ArrayList<>();

            for (int fi = 0; fi < csvUris.size(); fi++) {
                Uri uri = csvUris.get(fi);
                int fileIdx = fi + 1;
                int totalFiles = csvUris.size();
                int rowsForFile = perFileRows.get(uri);
                int processedBefore = processedAll;

                CsvFormImportService.ImportSummary s = CsvFormImportService.importFromCsvUri(
                        this, uri,
                        (proc, tot, imp, skip, fail) -> {
                            int cur = tot > 0 ? tot : rowsForFile;
                            int globalProc = Math.min(overallTotal, processedBefore + Math.min(proc, cur));
                            int pct = overallTotal > 0 ? Math.min(95, (globalProc * 95) / overallTotal) : 95;
                            if (pct == lastPercent[0]) return;
                            lastPercent[0] = pct;
                            Threading.main(() -> {
                                progressDialog.setProgress(pct);
                                progressDialog.setMessage("Importing file " + fileIdx + "/" + totalFiles
                                        + ": " + pct + "%\nRows " + globalProc + "/" + overallTotal
                                        + "\nImported " + imp + ", Skipped " + skip + ", Failed " + fail);
                            });
                        });

                importedAll += s.importedRows;
                skippedAll  += s.skippedRows;
                failedAll   += s.failedRows;
                processedAll += rowsForFile;

                String label = s.fileName != null ? s.fileName : ("File " + fileIdx);
                String status = s.timedOutDuringProcessing ? "TIMEOUT" : (s.hasFileFailure() ? "FAILED" : "OK");
                summary.add(label + " [" + status + "]: Imported " + s.importedRows
                        + ", Skipped " + s.skippedRows + ", Failed " + s.failedRows);
            }

            StringBuilder msg = new StringBuilder("CSV import finished for ")
                    .append(csvUris.size()).append(" file(s).")
                    .append("\nImported: ").append(importedAll)
                    .append(", Skipped: ").append(skippedAll)
                    .append(", Failed: ").append(failedAll);
            if (!summary.isEmpty()) {
                msg.append("\n\nPer-file summary:");
                for (String line : summary) msg.append("\n- ").append(line);
            }
            String finalMsg = msg.toString();

            Threading.main(() -> {
                progressDialog.setProgress(100);
                progressDialog.setMessage("Finalizing: 100%");
                progressDialog.dismiss();
                showCustomDialog(this, finalMsg, this::loadData);
            });
        });
    }

    private List<Uri> extractCsvUris(Intent data) {
        List<Uri> uris = new ArrayList<>();
        if (data.getData() != null) uris.add(data.getData());
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                Uri u = clip.getItemAt(i).getUri();
                if (u != null && !uris.contains(u)) uris.add(u);
            }
        }
        return uris;
    }

    // ?? Dimension helper ??????????????????????????????????????????

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    // ?? Dialog helper ?????????????????????????????????????????????

    public void showCustomDialog(Context context, String message) {
        showCustomDialog(context, message, null);
    }

    public void showCustomDialog(Context context, String message, Runnable onDismiss) {
        Dialog dialog = new Dialog(context);
        dialog.setContentView(R.layout.custom_dialog);
        dialog.setCancelable(false);
        ((TextView) dialog.findViewById(R.id.dialog_message)).setText(message);
        Button ok = dialog.findViewById(R.id.dialog_ok_button);
        ok.setOnClickListener(v -> {
            dialog.dismiss();
            if (onDismiss != null) onDismiss.run();
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            int w = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.95f);
            int h = (int) (context.getResources().getDisplayMetrics().heightPixels * 0.8f);
            dialog.getWindow().setLayout(w, h);
        }
    }
}
