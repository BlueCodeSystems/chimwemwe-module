package com.bluecodeltd.chimwemwe.chw.activity;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.bluecodeltd.chimwemwe.chw.R;
import com.bluecodeltd.chimwemwe.chw.adapter.ViewPager2Adapter;
import com.bluecodeltd.chimwemwe.chw.dao.HotspotGroupDao;
import com.bluecodeltd.chimwemwe.chw.dao.MonthlyReviewDao;
import com.bluecodeltd.chimwemwe.chw.dao.ParticipantDao;
import com.bluecodeltd.chimwemwe.chw.dao.SessionAttendanceDao;
import com.bluecodeltd.chimwemwe.chw.fragment.HotspotGroupOverviewFragment;
import com.bluecodeltd.chimwemwe.chw.fragment.HotspotGroupParticipantsFragment;
import com.bluecodeltd.chimwemwe.chw.fragment.HotspotGroupSessionsFragment;
import com.bluecodeltd.chimwemwe.chw.model.ChimwemweIndexModel;
import com.bluecodeltd.chimwemwe.chw.model.HotspotGroupModel;
import com.bluecodeltd.chimwemwe.chw.model.MonthlyReviewModel;
import com.bluecodeltd.chimwemwe.chw.model.ParticipantModel;
import com.bluecodeltd.chimwemwe.chw.util.ChimwemweFormUtils;
import com.bluecodeltd.chimwemwe.chw.util.Threading;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.vijay.jsonwizard.constants.JsonFormConstants;
import com.vijay.jsonwizard.domain.Form;

import org.json.JSONArray;
import org.json.JSONObject;
import org.smartregister.family.util.Utils;
import org.smartregister.opd.utils.OpdConstants;
import org.smartregister.util.FormUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import androidx.preference.PreferenceManager;

import timber.log.Timber;

public class HotspotGroupDetailActivity extends AppCompatActivity {

    public static final String EXTRA_GROUP_ID = "group_id";

    private static final int REQUEST_CODE_PARTICIPANT_FORM = 2001;
    private static final int REQUEST_CODE_REVIEW_FORM     = 2002;
    private static final int REQUEST_CODE_OVC_SEARCH      = 2003;
    private static final int REQUEST_CODE_GROUP_FORM      = 2004;

    // Internal SQLite row id for ec_chimwemwe_group (only used as a legacy fallback)
    private long         groupDbId = -1;
    // Business identifier (ec_chimwemwe_group.group_id) used for opening the profile and linking child tables
    private String       groupIdentifier;
    private int          pendingNextSn = 1;
    private HotspotGroupModel currentGroup;
    private final String[] sessionDates = new String[14];
    private List<ParticipantModel> participants = Collections.emptyList();
    private List<MonthlyReviewModel> reviews = Collections.emptyList();
    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private TabLayoutMediator tabMediator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hotspot_group_detail);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        resolveGroupIdentifierFromIntent();
        tabLayout = findViewById(R.id.tabs);
        viewPager = findViewById(R.id.viewpager);
        setupViewPager();

        if (groupDbId != -1 || (groupIdentifier != null && !groupIdentifier.isEmpty())) {
            if (getSupportActionBar() != null) getSupportActionBar().setTitle("Group Details");
            loadGroup();
        } else {
            notifySectionFragments();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.menu_hotspot_group_detail, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.edit_record) {
            launchGroupEditForm();
            return true;
        }
        if (id == R.id.delete_record) {
            promptDeleteGroup();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void promptDeleteGroup() {
        final String gid = groupIdentifier != null ? groupIdentifier.trim() : "";
        if (gid.isEmpty()) {
            Toast.makeText(this, "Missing group id", Toast.LENGTH_SHORT).show();
            return;
        }

        int participantCount = ParticipantDao.countParticipants(gid);
        if (participantCount > 0) {
            new AlertDialog.Builder(this)
                    .setTitle("Cannot delete group")
                    .setMessage("This group has " + participantCount + " participant(s). Remove participants first.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Delete group?")
                .setMessage("This will delete the group and all its session attendance and review records.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> Threading.io(() -> {
                    try {
                        // Mark deleted locally (soft delete)
                        if (groupDbId > 0) HotspotGroupDao.deleteGroup(groupDbId);
                        else HotspotGroupDao.deleteGroupByBusinessId(gid);

                        // Save OpenSRP-standard delete event (do not override base_entity_id)
                        try {
                            FormUtils formUtils = new FormUtils(this);
                            JSONObject form = formUtils.getFormJson("chimwemwe_group_register");
                            if (form != null) {
                                form.put("entity_id", gid);
                                ChimwemweFormUtils.ensureFieldValue(form, "group_id", gid);
                                ChimwemweFormUtils.ensureFieldValue(form, "delete_status", "1");
                                ChimwemweFormUtils.saveRegistration(
                                        ChimwemweFormUtils.processRegistration(form, "ec_chimwemwe_group", gid),
                                        true
                                );
                            }
                        } catch (Exception e) {
                            Timber.e(e, "Save group delete event failed");
                        }
                    } catch (Exception e) {
                        Timber.e(e, "Delete group failed");
                    }
                    Threading.main(() -> {
                        Toast.makeText(this, "Group deleted", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }))
                .show();
    }

    @Override
    protected void onDestroy() {
        if (tabMediator != null) {
            tabMediator.detach();
            tabMediator = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (groupDbId != -1 || (groupIdentifier != null && !groupIdentifier.isEmpty())) loadGroup();
    }

    private void loadGroup() {
        Threading.io(() -> {
            HotspotGroupModel group = groupDbId != -1
                    ? HotspotGroupDao.getGroup(groupDbId)
                    : HotspotGroupDao.getGroupByBusinessId(groupIdentifier);
            long resolvedDbId = group != null ? group.getId() : -1L;
            String tmpGroupIdentifier = group != null ? group.getGroupId() : groupIdentifier;
            if (tmpGroupIdentifier != null) tmpGroupIdentifier = tmpGroupIdentifier.trim();
            final String resolvedGroupIdentifier = tmpGroupIdentifier;

            List<ParticipantModel> loadedParticipants =
                    (resolvedGroupIdentifier != null && !resolvedGroupIdentifier.isEmpty())
                    ? ParticipantDao.getParticipants(resolvedGroupIdentifier)
                    : Collections.emptyList();
            List<MonthlyReviewModel> loadedReviews =
                    (resolvedGroupIdentifier != null && !resolvedGroupIdentifier.isEmpty())
                    ? MonthlyReviewDao.getReviews(resolvedGroupIdentifier)
                    : Collections.emptyList();
            String[] loadedSessionDates = new String[14];
            for (int i = 1; i <= 14; i++) {
                loadedSessionDates[i - 1] = (resolvedGroupIdentifier != null && !resolvedGroupIdentifier.isEmpty())
                        ? SessionAttendanceDao.getSessionDate(resolvedGroupIdentifier, i)
                        : null;
            }

            Threading.main(() -> {
                groupDbId = resolvedDbId;
                groupIdentifier = resolvedGroupIdentifier;
                currentGroup = group;
                participants = loadedParticipants != null ? loadedParticipants : Collections.emptyList();
                reviews = loadedReviews != null ? loadedReviews : Collections.emptyList();
                System.arraycopy(loadedSessionDates, 0, sessionDates, 0, sessionDates.length);
                bindHeader(group);
                updateTabTitles();
                notifySectionFragments();
            });
        });
    }

    private void bindHeader(HotspotGroupModel g) {
        TextView tvAvatar    = findViewById(R.id.tv_group_avatar);
        TextView tvName      = findViewById(R.id.tv_header_group_name);
        TextView tvHotspot   = findViewById(R.id.tv_header_hotspot);
        TextView tvCode      = findViewById(R.id.tv_header_group_id);
        if (g == null) return;
        String name = g.getGroupName() != null ? g.getGroupName() : "";
        tvAvatar.setText(initials(name));
        tvName.setText(name.isEmpty() ? "—" : name);
        tvHotspot.setText(g.getHotspotName() != null ? g.getHotspotName() : "");
        String displayId = g.getGroupId() != null ? g.getGroupId().trim() : "";
        tvCode.setText(displayId);
    }

    private String initials(String name) {
        if (name == null || name.isEmpty()) return "G";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return String.valueOf(parts[0].charAt(0)).toUpperCase();
        return (String.valueOf(parts[0].charAt(0)) + String.valueOf(parts[parts.length - 1].charAt(0))).toUpperCase();
    }

    private void setupViewPager() {
        List<Fragment> fragments = Arrays.asList(
                new HotspotGroupOverviewFragment(),
                new HotspotGroupSessionsFragment(),
                new HotspotGroupParticipantsFragment()
        );
        ViewPager2Adapter adapter = new ViewPager2Adapter(this, fragments);
        viewPager.setAdapter(adapter);
        viewPager.setOffscreenPageLimit(fragments.size());
        tabMediator = new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            // titles set by updateTabTitles() after data loads; mediator just holds positions
        });
        tabMediator.attach();
        // Show initial labels before async load
        setTabTitle(0, "Overview",     -1);
        setTabTitle(1, "Participants",  0);
        setTabTitle(2, "Sessions",      0);
    }

    private void updateTabTitles() {
        setTabTitle(0, "Overview",      -1);
        int sessionsRecorded = 0;
        for (String d : sessionDates) if (d != null) sessionsRecorded++;
        setTabTitle(1, "Sessions",      sessionsRecorded);
        setTabTitle(2, "Participants",  participants.size());
    }

    private void setTabTitle(int position, String title, int count) {
        TabLayout.Tab tab = tabLayout.getTabAt(position);
        if (tab == null) return;
        View custom = LayoutInflater.from(this)
                .inflate(R.layout.item_chimwemwe_group_tab, null);
        ((TextView) custom.findViewById(R.id.tab_title)).setText(title);
        TextView tvCount = custom.findViewById(R.id.tab_count);
        if (count >= 0) {
            tvCount.setText(String.valueOf(count));
        } else {
            tvCount.setVisibility(View.GONE);
        }
        tab.setCustomView(custom);
    }

    private void notifySectionFragments() {
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment instanceof HotspotGroupSectionFragment) {
                ((HotspotGroupSectionFragment) fragment).refreshContent();
            }
        }
    }

    public void bindOverview(View root) {
        if (root == null) return;

        View btnEdit = root.findViewById(R.id.btn_edit_group);
        if (btnEdit != null) btnEdit.setOnClickListener(v -> launchGroupEditForm());
        View btnReview = root.findViewById(R.id.btn_monthly_review);
        if (btnReview != null) btnReview.setOnClickListener(v -> openMonthlyReview());

        TextView tvProvince        = root.findViewById(R.id.tv_province);
        TextView tvDistrict        = root.findViewById(R.id.tv_district);
        TextView tvSessionLocation = root.findViewById(R.id.tv_session_location);
        TextView tvHealthFacility  = root.findViewById(R.id.tv_health_facility);
        TextView tvFacilitators    = root.findViewById(R.id.tv_facilitators);
        TextView tvLastReview      = root.findViewById(R.id.tv_last_review);
        TextView tvCaseworkerName  = root.findViewById(R.id.tv_caseworker_name);
        TextView tvCaseworkerPhone = root.findViewById(R.id.tv_caseworker_phone);

        if (currentGroup != null) {
            if (tvProvince != null)        tvProvince.setText(orDash(currentGroup.getProvince()));
            if (tvDistrict != null)        tvDistrict.setText(orDash(currentGroup.getDistrict()));
            if (tvSessionLocation != null) tvSessionLocation.setText(orDash(currentGroup.getLocationOfSession()));
            if (tvHealthFacility != null)  tvHealthFacility.setText(orDash(currentGroup.getNearestHealthFacility()));
            if (tvFacilitators != null) {
                String f1 = currentGroup.getFacilitatorName1() != null ? currentGroup.getFacilitatorName1().trim() : "";
                String f2 = currentGroup.getFacilitatorName2() != null ? currentGroup.getFacilitatorName2().trim() : "";
                tvFacilitators.setText(orDash(f1.isEmpty() ? f2 : (f2.isEmpty() ? f1 : f1 + ", " + f2)));
            }
        } else {
            if (tvProvince != null)        tvProvince.setText(orDash(""));
            if (tvDistrict != null)        tvDistrict.setText(orDash(""));
            if (tvSessionLocation != null) tvSessionLocation.setText(orDash(""));
            if (tvHealthFacility != null)  tvHealthFacility.setText(orDash(""));
            if (tvFacilitators != null)    tvFacilitators.setText(orDash(""));
        }

        SharedPreferences cp =
                PreferenceManager.getDefaultSharedPreferences(this);
        if (tvCaseworkerName != null)
            tvCaseworkerName.setText(orDash(cp.getString("caseworker_name", null)));
        if (tvCaseworkerPhone != null)
            tvCaseworkerPhone.setText(orDash(cp.getString("phone", null)));

        updateLastReviewLabel(tvLastReview, reviews);
    }

    public void bindSessions(View root) {
        if (root == null) return;
        int recorded = 0;
        for (String d : sessionDates) if (d != null && !d.isEmpty()) recorded++;
        TextView tvDone = root.findViewById(R.id.tv_sessions_done);
        TextView tvRemaining = root.findViewById(R.id.tv_sessions_remaining);
        if (tvDone != null) tvDone.setText(String.valueOf(recorded));
        if (tvRemaining != null) tvRemaining.setText(String.valueOf(14 - recorded));
        buildSessionGrid(root.findViewById(R.id.grid_sessions), sessionDates);
    }

    public void bindParticipants(View root) {
        if (root == null) return;
        root.findViewById(R.id.btn_add_participant).setOnClickListener(v -> openAddParticipant());

        int total = participants.size();
        int completed = 0;
        for (ParticipantModel p : participants) if (p.isCompletedProgram()) completed++;

        TextView tvTotal = root.findViewById(R.id.tv_participant_count);
        TextView tvCompleted = root.findViewById(R.id.tv_completed_count);
        TextView tvInProgress = root.findViewById(R.id.tv_inprogress_count);
        if (tvTotal != null) tvTotal.setText(String.valueOf(total));
        if (tvCompleted != null) tvCompleted.setText(String.valueOf(completed));
        if (tvInProgress != null) tvInProgress.setText(String.valueOf(total - completed));

        buildParticipantList(root.findViewById(R.id.ll_participants), participants);
    }

    private void updateLastReviewLabel(TextView tvLastReview, List<MonthlyReviewModel> reviews) {
        if (tvLastReview == null) return;
        if (reviews == null || reviews.isEmpty()) {
            tvLastReview.setText("No reviews recorded");
        } else {
            MonthlyReviewModel last = reviews.get(0);
            tvLastReview.setText("Last reviewed: " + last.getReviewDate()
                    + " by " + last.getReviewerName());
        }
    }

    private void launchGroupEditForm() {
        if (currentGroup == null) return;
        Threading.io(() -> {
            try {
                FormUtils formUtils = new FormUtils(this);
                JSONObject form = formUtils.getFormJson("chimwemwe_group_register");
                if (form == null) return;
                populateGroupEditForm(form);
                launchJsonWizardForm(form, REQUEST_CODE_GROUP_FORM, true, "launchGroupEditForm intent");
            } catch (Exception e) {
                Timber.e(e, "launchGroupEditForm");
            }
        });
    }

    private void buildSessionGrid(GridLayout gridSessions, String[] sessionDates) {
        if (gridSessions == null) return;
        gridSessions.removeAllViews();
        int cols = 4;

        for (int i = 0; i < 14; i++) {
            final int sessionNum = i + 1;
            String date  = sessionDates[i];
            boolean hasDate = date != null && !date.isEmpty();

            Button btn = new Button(this);
            btn.setText("S" + sessionNum + (hasDate ? "\n" + date : "\n--"));
            btn.setTextSize(10f);
            btn.setAllCaps(false);
            btn.setGravity(Gravity.CENTER);
            btn.setLines(2);

            if (hasDate) {
                btn.setBackground(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_session_recorded));
                btn.setTextColor(Color.WHITE);
            } else {
                btn.setBackground(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_session_pending));
                btn.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.chimwemwe_primary));
            }

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width       = 0;
            params.height      = GridLayout.LayoutParams.WRAP_CONTENT;
            params.columnSpec  = GridLayout.spec(i % cols, 1, 1f);
            params.rowSpec     = GridLayout.spec(i / cols, 1, 1f);
            params.setMargins(4, 4, 4, 4);
            btn.setLayoutParams(params);

            btn.setOnClickListener(v -> {
                if (groupIdentifier == null || groupIdentifier.trim().isEmpty()) {
                    Toast.makeText(this, "Save the group first", Toast.LENGTH_SHORT).show();
                    return;
                }
                openRecordAttendance(sessionNum);
            });

            gridSessions.addView(btn);
        }
    }

    private void buildParticipantList(LinearLayout llParticipants, List<ParticipantModel> participants) {
        if (llParticipants == null) return;
        llParticipants.removeAllViews();
        if (participants == null || participants.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("No participants yet. Tap '+ Add' to enrol participants.");
            tv.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.chimwemwe_text_secondary));
            tv.setTextSize(13f);
            int pad = (int) (16 * getResources().getDisplayMetrics().density);
            tv.setPadding(pad, pad, pad, pad);
            llParticipants.addView(tv);
            return;
        }

        for (ParticipantModel p : participants) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_participant_row, llParticipants, false);

            ((TextView) row.findViewById(R.id.tv_sn)).setText(String.format("%02d", p.getSn()));
            ((TextView) row.findViewById(R.id.tv_caregiver_name)).setText(
                    p.getCaregiverFullName().isEmpty() ? "—" : p.getCaregiverFullName());
            ((TextView) row.findViewById(R.id.tv_child_name)).setText(
                    p.getChildFullName().isEmpty() ? "—" : p.getChildFullName());

            TextView tvDone = row.findViewById(R.id.tv_sessions_done);
            int sessionsCompleted = p.getSessionsCompleted();
            tvDone.setText(sessionsCompleted + "/14");
            if (p.isCompletedProgram()) {
                tvDone.setTextColor(Color.parseColor("#16A34A"));
                tvDone.setBackground(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_sessions_pill_complete));
            } else {
                tvDone.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.chimwemwe_primary));
                tvDone.setBackground(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_sessions_pill));
            }

            android.widget.ProgressBar pb = row.findViewById(R.id.pb_sessions);
            if (pb != null) pb.setProgress(sessionsCompleted);

            row.setOnClickListener(v -> {
                Intent profileIntent = new Intent(this, ChimwemweParticipantProfileActivity.class);
                profileIntent.putExtra(ChimwemweParticipantProfileActivity.EXTRA_PARTICIPANT_ID, p.getId());
                profileIntent.putExtra(ChimwemweParticipantProfileActivity.EXTRA_PARTICIPANT_CODE, p.getParticipantId());
                startActivity(profileIntent);
            });

            row.findViewById(R.id.btn_edit_participant).setOnClickListener(v ->
                    launchParticipantForm(p, p.getSn()));

            row.findViewById(R.id.btn_delete_participant).setOnClickListener(v ->
                    new AlertDialog.Builder(this)
                            .setTitle("Remove participant?")
                            .setMessage("This will also delete attendance records for this participant.")
                            .setPositiveButton("Remove", (d, w) -> Threading.io(() -> {
                                ParticipantDao.deleteParticipant(p.getId());
                                Threading.main(this::loadGroup);
                            }))
                            .setNegativeButton("Cancel", null)
                            .show());

            llParticipants.addView(row);
        }
    }

    // ── Participant form ──────────────────────────────────────

    void openAddParticipant() {
        if (groupIdentifier == null || groupIdentifier.trim().isEmpty()) {
            Toast.makeText(this, "Save the group first", Toast.LENGTH_SHORT).show();
            return;
        }
        Threading.io(() -> {
            int nextSn = ParticipantDao.nextSn(groupIdentifier);
            Threading.main(() -> showOvcEnrollmentDialog(nextSn));
        });
    }

    private void showOvcEnrollmentDialog(int nextSn) {
        new AlertDialog.Builder(this)
                .setTitle("Add Participant")
                .setMessage("Is the child enrolled in the OVC Comprehensive program?")
                .setPositiveButton("Yes", (d, w) -> {
                    pendingNextSn = nextSn;
                    Intent intent = new Intent(this, ChimwemweSearchActivity.class);
                    intent.putExtra(ChimwemweSearchActivity.EXTRA_SELECTION_MODE, true);
                    startActivityForResult(intent, REQUEST_CODE_OVC_SEARCH);
                })
                .setNegativeButton("No", (d, w) -> launchParticipantForm(null, nextSn))
                .show();
    }


    /**
     * Launch chimwemwe_participant_register.json.
     *   null            → blank form, new insert (sn = next available)
     *   model, id=-1    → caregiver fields pre-filled, child fields blank, new insert (copied caregiver)
     *   model, id>0     → all fields pre-filled, update existing record (edit flow)
     */
    private void launchParticipantForm(ParticipantModel existing, int sn) {
        Threading.io(() -> {
            try {
                FormUtils formUtils = new FormUtils(this);
                JSONObject form = formUtils.getFormJson("chimwemwe_participant_register");
                populateParticipantForm(form, existing, sn);
                launchJsonWizardForm(form, REQUEST_CODE_PARTICIPANT_FORM, true,
                        "Error launching participant form");
            } catch (Exception e) {
                Timber.e(e, "Error preparing participant form");
            }
        });
    }

    private void launchOvcParticipantForm(ChimwemweIndexModel ovcRecord, int sn) {
        Threading.io(() -> {
            try {
                FormUtils formUtils = new FormUtils(this);
                JSONObject form = formUtils.getFormJson("chimwemwe_participant_register_ovc_enrolled");
                populateOvcParticipantForm(form, ovcRecord, sn);
                launchJsonWizardForm(form, REQUEST_CODE_PARTICIPANT_FORM, true,
                        "Error launching OVC participant form");
            } catch (Exception e) {
                Timber.e(e, "Error preparing OVC participant form");
            }
        });
    }

    private String normalizeGender(String gender) {
        if (gender == null || gender.isEmpty()) return "";
        String g = gender.trim().toLowerCase();
        if (g.startsWith("m")) return "male";
        if (g.startsWith("f")) return "female";
        return g;
    }

    private String normalizeDob(String dob) {
        if (dob == null || dob.isEmpty()) return "";
        // Strip time component: "2020-04-20T00:00:00" or "2020-04-20 00:00:00"
        String datePart = dob.trim();
        if (datePart.contains("T")) datePart = datePart.substring(0, datePart.indexOf('T'));
        else if (datePart.contains(" ")) datePart = datePart.substring(0, datePart.indexOf(' '));
        datePart = datePart.trim();

        String[] inputFormats = {
                "yyyy-MM-dd", "dd-MM-yyyy", "dd/MM/yyyy", "MM/dd/yyyy",
                "yyyy/MM/dd", "dd.MM.yyyy", "yyyy.MM.dd",
                "yyyy-MM-dd'Z'", "d-M-yyyy", "d/M/yyyy"
        };
        DateTimeFormatter out = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        for (String fmt : inputFormats) {
            try {
                LocalDate d = LocalDate.parse(datePart, DateTimeFormatter.ofPattern(fmt));
                return d.format(out);
            } catch (Exception ignored) {}
        }
        return datePart; // unrecognised format — pass through and let the form handle it
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null) return;

        if (requestCode == REQUEST_CODE_GROUP_FORM) {
            String jsonString = data.getStringExtra(OpdConstants.JSON_FORM_EXTRA.JSON);
            if (jsonString == null) return;
            Threading.io(() -> {
                try {
                    JSONObject form  = new JSONObject(jsonString);
                    JSONObject step1 = form.optJSONObject("step1");
                    JSONObject step2 = form.optJSONObject("step2");
                    JSONObject step3 = form.optJSONObject("step3");
                    HotspotGroupModel m = currentGroup != null ? currentGroup : new HotspotGroupModel();
                    m.setId(groupDbId);
                    String editedGroupId = fieldValue(step1, "group_id");
                    if (editedGroupId == null || editedGroupId.trim().isEmpty()) {
                        editedGroupId = m.getGroupId();
                    }
                    m.setGroupId(editedGroupId);
                    m.setGroupName(fieldValue(step1,             "group_name"));
                    m.setHotspotName(fieldValue(step1,           "hotspot_name"));
                    m.setProvince(fieldValue(step1,              "province"));
                    m.setDistrict(fieldValue(step1,              "district"));
                    m.setLocationOfSession(fieldValue(step1,     "location_of_session"));
                    m.setNearestHealthFacility(fieldValue(step1, "nearest_health_facility"));
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
                    HotspotGroupDao.updateGroup(m);
                    ChimwemweFormUtils.ensureFieldValue(form, "group_id", m.getGroupId());
                    ChimwemweFormUtils.ensureFieldValue(form, "created_date", m.getCreatedDate());
                    ChimwemweFormUtils.saveRegistration(
                            ChimwemweFormUtils.processRegistration(
                                    form,
                                    "ec_chimwemwe_group",
                                    m.getGroupId()
                            ),
                            true
                    );
                    Threading.main(() -> {
                        Toast.makeText(this, "Group updated", Toast.LENGTH_SHORT).show();
                        loadGroup();
                    });
                } catch (Exception e) {
                    Timber.e(e, "onActivityResult group form");
                }
            });
            return;
        }

        if (requestCode == REQUEST_CODE_REVIEW_FORM) {
            String jsonString = data.getStringExtra(OpdConstants.JSON_FORM_EXTRA.JSON);
            if (jsonString == null) return;
            Threading.io(() -> {
                try {
                    JSONObject form  = new JSONObject(jsonString);
                    JSONObject step1 = form.optJSONObject("step1");

                    MonthlyReviewModel r = new MonthlyReviewModel();
                    r.setGroupId(groupIdentifier);
                    r.setReviewQuarter(fieldValue(step1,    "review_quarter"));
                    r.setReviewDate(fieldValue(step1,       "review_date"));
                    r.setReviewerName(fieldValue(step1,     "reviewer_name"));
                    r.setRegisterAccurate(fieldValue(step1, "register_accurate"));
                    r.setReviewerNotes(fieldValue(step1,    "reviewer_notes"));
                    r.setId(MonthlyReviewDao.insertReview(r));
                    ChimwemweFormUtils.ensureFieldValue(form, "group_id", groupIdentifier);
                    ChimwemweFormUtils.saveRegistration(
                            ChimwemweFormUtils.processRegistration(
                                    form,
                                    "ec_chimwemwe_monthly_review",
                                    ChimwemweFormUtils.reviewEntityId(r.getId())
                            ),
                            false
                    );

                    Threading.main(() -> {
                        Toast.makeText(this, "Review saved", Toast.LENGTH_SHORT).show();
                        loadGroup();
                    });
                } catch (Exception e) {
                    Timber.e(e, "Error saving monthly review");
                }
            });
            return;
        }

        if (requestCode == REQUEST_CODE_OVC_SEARCH) {
            String firstName   = data.getStringExtra(ChimwemweSearchActivity.RESULT_FIRST_NAME);
            String lastName    = data.getStringExtra(ChimwemweSearchActivity.RESULT_LAST_NAME);
            String gender      = data.getStringExtra(ChimwemweSearchActivity.RESULT_GENDER);
            String birthdate   = data.getStringExtra(ChimwemweSearchActivity.RESULT_BIRTHDATE);
            String uniqueId      = data.getStringExtra(ChimwemweSearchActivity.RESULT_UNIQUE_ID);
            String householdId   = data.getStringExtra(ChimwemweSearchActivity.RESULT_HOUSEHOLD_ID);
            String caregiverName = data.getStringExtra(ChimwemweSearchActivity.RESULT_CAREGIVER_NAME);

            ChimwemweIndexModel ovcRecord = new ChimwemweIndexModel();
            ovcRecord.setFirstName(firstName       != null ? firstName     : "");
            ovcRecord.setLastName(lastName         != null ? lastName      : "");
            ovcRecord.setGender(gender             != null ? gender        : "");
            ovcRecord.setBirthdate(birthdate       != null ? birthdate     : "");
            ovcRecord.setUniqueId(uniqueId         != null ? uniqueId      : "");
            ovcRecord.setHouseholdId(householdId   != null ? householdId   : "");
            ovcRecord.setCaregiverName(caregiverName != null ? caregiverName : "");
            launchOvcParticipantForm(ovcRecord, pendingNextSn);
            return;
        }

        if (requestCode != REQUEST_CODE_PARTICIPANT_FORM) return;

        String jsonString = data.getStringExtra(OpdConstants.JSON_FORM_EXTRA.JSON);
        if (jsonString == null) return;

        Threading.io(() -> {
            try {
                JSONObject form         = new JSONObject(jsonString);
                int    sn              = form.optInt("_sn", 1);
                long   participantId   = form.optLong("_participant_id", -1L);

                JSONObject step1 = form.optJSONObject("step1");
                JSONObject step2 = form.optJSONObject("step2");
                JSONObject step3 = form.optJSONObject("step3");

                ParticipantModel m = new ParticipantModel();
                m.setId(participantId);
                m.setGroupId(groupIdentifier);
                m.setSn(sn);
                // step2 may be absent when the form is single-step; fall back to step1
                JSONObject childStep = step2 != null ? step2 : step1;

                m.setCaregiverFirstName(fieldValue(step1,      "caregiver_first_name"));
                m.setCaregiverSurname(fieldValue(step1,        "caregiver_surname"));
                m.setChildFirstName(fieldValue(childStep,      "child_first_name"));
                m.setChildSurname(fieldValue(childStep,        "child_surname"));
                m.setChildDob(fieldValue(childStep,            "child_dob"));
                m.setChildSex(fieldValue(childStep,            "child_sex"));
                m.setIsEnrolledOvc(fieldValue(childStep,       "is_enrolled_ovc"));
                m.setVcaId(fieldValue(childStep,               "vca_id"));
                m.setCaregiverId(fieldValue(childStep,         "caregiver_id"));
                JSONObject referralStep = step3 != null ? step3 : step1;

                m.setWhoReferred(fieldValue(referralStep,        "who_referred"));
                m.setServiceReferredFor(fieldValue(referralStep, "service_referred_for"));
                m.setReferralDate(fieldValue(referralStep,       "referral_date"));
                m.setReceivingOrg(fieldValue(referralStep,       "receiving_org"));
                m.setJobTitle(fieldValue(referralStep,           "job_title"));
                m.setServiceDate(fieldValue(referralStep,        "service_date"));

                String participantIdCode = fieldValue(step1, "participant_id");
                if (participantIdCode == null || participantIdCode.trim().isEmpty()) {
                    participantIdCode = form.optString("entity_id", "").trim();
                }
                if (participantIdCode.isEmpty() && participantId != -1L && participants != null) {
                    for (ParticipantModel existing : participants) {
                        if (existing.getId() == participantId) {
                            participantIdCode = existing.getParticipantId();
                            break;
                        }
                    }
                }
                if (participantIdCode == null || participantIdCode.isEmpty()) {
                    participantIdCode = participantId == -1L
                            ? "CHIM-" + System.currentTimeMillis()
                            : "CHIM-" + participantId;
                }
                m.setParticipantId(participantIdCode);

                // Standard OpenSRP save only: the client processor writes to ec_chimwemwe_participant via ec_client_fields.json.
                ChimwemweFormUtils.ensureFieldValue(form, "group_id", groupIdentifier);
                ChimwemweFormUtils.ensureFieldValue(form, "sn", String.valueOf(sn));
                ChimwemweFormUtils.ensureFieldValue(form, "participant_id", participantIdCode);
                ChimwemweFormUtils.saveRegistration(
                        ChimwemweFormUtils.processRegistration(
                                form,
                                "ec_chimwemwe_participant",
                                m.getParticipantId()
                        ),
                        participantId != -1L
                );

                Threading.main(() -> {
                    Toast.makeText(this, "Participant saved", Toast.LENGTH_SHORT).show();
                    loadGroup();
                });
            } catch (Exception e) {
                Timber.e(e, "Error saving participant from form");
                Threading.main(() ->
                        Toast.makeText(this, "Error saving participant. Please try again.",
                                Toast.LENGTH_SHORT).show());
            }
        });
    }

    // ── Standard event persistence (sync) ────────────────────



    // ── Helpers ───────────────────────────────────────────────

    /** Read the submitted value for a field key from a step's fields array. */
    private String fieldValue(JSONObject step, String key) {
        if (step == null) return "";
        try {
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

    private String orDash(String v) { return (v == null || v.isEmpty()) ? "\u2014" : v; }

/** Inject a value into a field in the form JSON before launching (for pre-population / edit). */
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

    private void populateGroupEditForm(JSONObject form) {
        if (form == null || currentGroup == null) return;
        setFieldValue(form, "step1", "group_id", currentGroup.getGroupId());
        setFieldValue(form, "step1", "group_name", currentGroup.getGroupName());
        setFieldValue(form, "step1", "hotspot_name", currentGroup.getHotspotName());
        setFieldValue(form, "step1", "province", currentGroup.getProvince());
        setFieldValue(form, "step1", "district", currentGroup.getDistrict());
        setFieldValue(form, "step1", "location_of_session", currentGroup.getLocationOfSession());
        setFieldValue(form, "step1", "nearest_health_facility", currentGroup.getNearestHealthFacility());
        setFieldValue(form, "step1", "facilitator_name_1", currentGroup.getFacilitatorName1());
        setFieldValue(form, "step1", "facilitator_name_2", currentGroup.getFacilitatorName2());
    }

    private void populateParticipantForm(JSONObject form, ParticipantModel existing, int sn)
            throws Exception {
        if (form == null) return;
        boolean isEdit = existing != null && existing.getId() > 0;
        String participantCode = isEdit ? existing.getParticipantId() : null;
        if (participantCode == null || participantCode.trim().isEmpty()) {
            participantCode = isEdit
                    ? "CHIM-" + existing.getId()
                    : "CHIM-" + System.currentTimeMillis();
        }

        if (existing != null) {
            setFieldValue(form, "step1", "caregiver_first_name", existing.getCaregiverFirstName());
            setFieldValue(form, "step1", "caregiver_surname", existing.getCaregiverSurname());
            setFieldValue(form, "step1", "child_first_name", existing.getChildFirstName());
            setFieldValue(form, "step1", "child_surname", existing.getChildSurname());
            setFieldValue(form, "step1", "child_dob", existing.getChildDob());
            setFieldValue(form, "step1", "child_sex", existing.getChildSex());
            setFieldValue(form, "step1", "is_enrolled_ovc", existing.getIsEnrolledOvc());
            setFieldValue(form, "step1", "who_referred", existing.getWhoReferred());
            setFieldValue(form, "step1", "service_referred_for", existing.getServiceReferredFor());
            setFieldValue(form, "step1", "referral_date", existing.getReferralDate());
            setFieldValue(form, "step1", "receiving_org", existing.getReceivingOrg());
            setFieldValue(form, "step1", "job_title", existing.getJobTitle());
            setFieldValue(form, "step1", "service_date", existing.getServiceDate());
        } else {
            setFieldValue(form, "step1", "is_enrolled_ovc", "No");
        }

        if (isEdit) {
            setFieldValue(form, "step1", "vca_id", existing.getVcaId());
            setFieldValue(form, "step1", "caregiver_id", existing.getCaregiverId());
        } else {
            setFieldValue(form, "step1", "vca_id", generateNumericIdentifier());
            setFieldValue(form, "step1", "caregiver_id", generateNumericIdentifier());
        }

        setFieldValue(form, "step1", "group_id", groupIdentifier);
        setFieldValue(form, "step1", "participant_id", participantCode);
        form.put("_sn", sn);
        form.put("_participant_id", existing != null ? existing.getId() : -1L);
    }

    private void populateOvcParticipantForm(JSONObject form, ChimwemweIndexModel ovcRecord, int sn)
            throws Exception {
        if (form == null || ovcRecord == null) return;
        String rawDob = ovcRecord.getBirthdate();
        String normalizedDob = normalizeDob(rawDob);
        Timber.d("OVC DOB — raw: [%s]  normalized: [%s]", rawDob, normalizedDob);
        setFieldValue(form, "step1", "group_id", groupIdentifier);
        setFieldValue(form, "step1", "child_first_name", ovcRecord.getFirstName());
        setFieldValue(form, "step1", "child_surname", ovcRecord.getLastName());
        setFieldValue(form, "step1", "child_dob", normalizedDob);
        setFieldValue(form, "step1", "child_sex", normalizeGender(ovcRecord.getGender()));
        setFieldValue(form, "step1", "is_enrolled_ovc", "Yes");
        setFieldValue(form, "step1", "vca_id", ovcRecord.getUniqueId());
        setFieldValue(form, "step1", "caregiver_id", ovcRecord.getHouseholdId());
        String newParticipantId = "CHIM-" + System.currentTimeMillis();
        setFieldValue(form, "step1", "participant_id", newParticipantId);
        form.put("_sn", sn);
        form.put("_participant_id", -1L);
    }

    private String generateNumericIdentifier() {
        return String.valueOf(new Random().nextInt(900_000_000));
    }

    private void resolveGroupIdentifierFromIntent() {
        Bundle extras = getIntent() != null ? getIntent().getExtras() : null;
        if (extras == null) return;

        Object raw = extras.get(EXTRA_GROUP_ID);
        if (raw instanceof Number) {
            groupDbId = ((Number) raw).longValue();
            groupIdentifier = null;
            return;
        }

        if (raw instanceof String) {
            groupIdentifier = ((String) raw).trim();
            // Treat string extras as business IDs (group_id), even if numeric.
            // Numeric parsing here causes us to incorrectly load by internal row id.
            groupDbId = -1L;
        }
    }

    private void launchJsonWizardForm(JSONObject form, int requestCode, boolean wizard, String errorTag) {
        if (form == null) return;
        final JSONObject finalForm = form;
        Threading.main(() -> {
            try {
                Intent intent = new Intent(
                        this, Utils.metadata().familyFormActivity);
                intent.putExtra(
                        JsonFormConstants.JSON_FORM_KEY.FORM,
                        createJsonWizardConfig(wizard));
                intent.putExtra(
                        JsonFormConstants.JSON_FORM_KEY.JSON,
                        finalForm.toString());
                startActivityForResult(intent, requestCode);
            } catch (Exception e) {
                Timber.e(e, errorTag);
            }
        });
    }

    private Form createJsonWizardConfig(boolean wizard) {
        Form cfg = new Form();
        cfg.setWizard(wizard);
        cfg.setHideSaveLabel(true);
        cfg.setSaveLabel(getString(R.string.submit));
        cfg.setNavigationBackground(R.color.chimwemwe_primary);
        if (wizard) {
            cfg.setNextLabel(getString(R.string.next));
            cfg.setPreviousLabel(getString(R.string.previous));
        }
        return cfg;
    }

    // ── Monthly review ───────────────────────────────────────

    void openMonthlyReview() {
        if (groupIdentifier == null || groupIdentifier.trim().isEmpty()) {
            Toast.makeText(this, "Save the group first", Toast.LENGTH_SHORT).show();
            return;
        }
        Threading.io(() -> {
            try {
                FormUtils formUtils = new FormUtils(this);
                JSONObject form = formUtils.getFormJson("chimwemwe_monthly_review");
                launchJsonWizardForm(form, REQUEST_CODE_REVIEW_FORM, false,
                        "Error launching review form");
            } catch (Exception e) {
                Timber.e(e, "Error preparing review form");
            }
        });
    }

    // ── Attendance ────────────────────────────────────────────

    void openRecordAttendance(int sessionNum) {
        Threading.io(() -> {
            int count = ParticipantDao.countParticipants(groupIdentifier);
            Threading.main(() -> {
                if (count == 0) {
                    Toast.makeText(this, "Add participants before recording attendance",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent intent = new Intent(this, RecordAttendanceActivity.class);
                intent.putExtra(RecordAttendanceActivity.EXTRA_GROUP_ID, groupIdentifier);
                intent.putExtra(RecordAttendanceActivity.EXTRA_SESSION_NUMBER, sessionNum);
                startActivity(intent);
            });
        });
    }

    public interface HotspotGroupSectionFragment {
        void refreshContent();
    }
}
