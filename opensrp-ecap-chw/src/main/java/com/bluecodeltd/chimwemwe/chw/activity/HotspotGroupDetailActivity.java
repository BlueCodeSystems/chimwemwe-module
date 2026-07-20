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
import android.widget.ArrayAdapter;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
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
import com.bluecodeltd.chimwemwe.chw.dao.SessionAttendanceParticipantDao;
import com.bluecodeltd.chimwemwe.chw.fragment.HotspotGroupOverviewFragment;
import com.bluecodeltd.chimwemwe.chw.fragment.HotspotGroupParticipantsFragment;
import com.bluecodeltd.chimwemwe.chw.fragment.HotspotGroupSessionsFragment;
import com.bluecodeltd.chimwemwe.chw.model.ChimwemweIndexModel;
import com.bluecodeltd.chimwemwe.chw.model.HotspotGroupModel;
import com.bluecodeltd.chimwemwe.chw.model.MonthlyReviewModel;
import com.bluecodeltd.chimwemwe.chw.model.ParticipantModel;
import com.bluecodeltd.chimwemwe.chw.util.ChimwemweFormUtils;
import com.bluecodeltd.chimwemwe.chw.util.DistrictNameUtils;
import com.bluecodeltd.chimwemwe.chw.util.Threading;
import com.bluecodeltd.chimwemwe.chw.util.SupervisorSignOffHelper;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.vijay.jsonwizard.constants.JsonFormConstants;
import com.vijay.jsonwizard.domain.Form;

import org.json.JSONArray;
import org.json.JSONObject;
import org.smartregister.chw.core.utils.CoreJsonFormUtils;
import org.smartregister.family.util.Utils;
import org.smartregister.opd.utils.OpdConstants;
import org.smartregister.util.FormUtils;

import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
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
    // Session progression gate: sessionUnlocked[i] == true means session i+1 may be opened.
    // Session 1 is always unlocked; session N unlocks only once session N-1 is complete
    // (>=1 participant marked Group or Home Visit). All-Absent sessions leave the next locked.
    private final boolean[] sessionUnlocked = new boolean[14];
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
        final String gid = resolveActiveGroupIdentifier();
        // Old groups created before the business group_id column (DB v37) have only a SQLite row id,
        // no group_id. Resolve that so they can still be deleted; we only truly can't identify the
        // group when BOTH the business id and the row id are missing.
        final long dbId = resolveActiveGroupDbId();
        if (gid.isEmpty() && dbId <= 0) {
            Toast.makeText(this, "Missing group id", Toast.LENGTH_SHORT).show();
            return;
        }

        // The participant guard can only match participants by business group_id. When there is no
        // business id (legacy group), there are no participants that can be linked to it, so skip
        // the guard rather than block deletion.
        if (!gid.isEmpty()) {
            int participantCount = ParticipantDao.countParticipants(gid);
            if (participantCount > 0) {
                new AlertDialog.Builder(this)
                        .setTitle("Cannot delete group")
                        .setMessage("This group has " + participantCount + " participant(s). Remove participants first.")
                        .setPositiveButton("OK", null)
                        .show();
                return;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Delete group?")
                .setMessage("This will delete the group and all its session attendance and review records.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> Threading.io(() -> {
                    boolean ok = false;
                    try {
                        if (dbId > 0) {
                            // 1. Cascading local soft-delete by row id. deleteGroup falls back to the
                            //    row id for child-table cleanup when the group has no business id, and
                            //    always soft-deletes the group row itself so it leaves the register.
                            HotspotGroupDao.deleteGroup(dbId);
                            ok = true;
                        } else if (!gid.isEmpty()) {
                            // No row id resolved but we do have a business id — soft-delete by it.
                            HotspotGroupDao.deleteGroupByBusinessId(gid);
                            ok = true;
                        }
                        // 2. Emit a syncable delete Event when we have a business id (the Client's
                        //    baseEntityId). Legacy groups without one are deleted locally only.
                        if (ok && !gid.isEmpty() && currentGroup != null) {
                            emitGroupDeleteEvent(currentGroup, gid);
                        }
                    } catch (Exception e) {
                        Timber.e(e, "Delete group failed");
                    }
                    final boolean success = ok;
                    Threading.main(() -> {
                        Toast.makeText(this,
                                success ? "Group deleted" : "Could not delete group",
                                Toast.LENGTH_SHORT).show();
                        if (success) {
                            setResult(RESULT_OK);
                            finish();
                        }
                    });
                }))
                .show();
    }

    /**
     * Resolve the group's SQLite row id from whatever we have loaded: the in-memory model, the
     * intent-supplied db id, or a last-resort lookup by business id. Returns -1 when none resolves.
     * Lets legacy groups (no business group_id) still be deleted by their row id.
     */
    private long resolveActiveGroupDbId() {
        if (currentGroup != null && currentGroup.getId() > 0) return currentGroup.getId();
        if (groupDbId > 0) return groupDbId;
        String gid = groupIdentifier != null ? groupIdentifier.trim() : "";
        if (!gid.isEmpty()) {
            try {
                HotspotGroupModel g = HotspotGroupDao.getGroupByBusinessId(gid);
                if (g != null && g.getId() > 0) {
                    currentGroup = g;
                    return g.getId();
                }
            } catch (Exception e) {
                Timber.w(e, "resolveActiveGroupDbId");
            }
        }
        return -1L;
    }

    private String resolveActiveGroupIdentifier() {
        if (groupIdentifier != null && !groupIdentifier.trim().isEmpty()) {
            return groupIdentifier.trim();
        }
        if (currentGroup != null && currentGroup.getGroupId() != null && !currentGroup.getGroupId().trim().isEmpty()) {
            groupIdentifier = currentGroup.getGroupId().trim();
            return groupIdentifier;
        }
        if (groupDbId != -1) {
            try {
                HotspotGroupModel group = HotspotGroupDao.getGroup(groupDbId);
                if (group != null && group.getGroupId() != null && !group.getGroupId().trim().isEmpty()) {
                    currentGroup = group;
                    groupIdentifier = group.getGroupId().trim();
                    return groupIdentifier;
                }
            } catch (Exception e) {
                Timber.w(e, "resolveActiveGroupIdentifier");
            }
        }
        return "";
    }

    /**
     * Builds a Chimwemwe Group Registration form populated with the group's current
     * values plus delete_status=1, and submits it through ChimwemweFormUtils so an
     * Event is recorded locally and queued for the next /rest/event/add push. The
     * server-side ec_client_fields.json mapping materialises Client.attributes.delete_status
     * into the ec_chimwemwe_group.delete_status column on other devices on next pull.
     */
    private void emitGroupDeleteEvent(HotspotGroupModel group, String entityId) {
        if (group == null || entityId == null || entityId.trim().isEmpty()) return;
        try {
            FormUtils formUtils = new FormUtils(this);
            JSONObject form = formUtils.getFormJson("chimwemwe_group_register");
            if (form == null) return;
            form.put("entity_id", entityId);
            java.util.Map<String, String> map = groupToMap(group);
            map.put("delete_status", "1");
            CoreJsonFormUtils.populateJsonForm(form, map);
            ChimwemweFormUtils.saveRegistration(
                    ChimwemweFormUtils.processRegistration(form, "ec_chimwemwe_group", entityId),
                    true);
        } catch (Exception e) {
            Timber.w(e, "emitGroupDeleteEvent failed; local delete persists but sync may not");
        }
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

            // Session progression gate. Session 1 is always open; each later session unlocks only
            // once the immediately preceding session is complete (>=1 participant Group/Home Visit).
            boolean[] loadedSessionUnlocked = new boolean[14];
            loadedSessionUnlocked[0] = true;
            boolean hasGroupId = resolvedGroupIdentifier != null && !resolvedGroupIdentifier.isEmpty();
            for (int i = 2; i <= 14; i++) {
                loadedSessionUnlocked[i - 1] = hasGroupId
                        && SessionAttendanceParticipantDao.isSessionComplete(resolvedGroupIdentifier, i - 1);
            }

            Threading.main(() -> {
                groupDbId = resolvedDbId;
                groupIdentifier = resolvedGroupIdentifier;
                currentGroup = group;
                participants = loadedParticipants != null ? loadedParticipants : Collections.emptyList();
                reviews = loadedReviews != null ? loadedReviews : Collections.emptyList();
                System.arraycopy(loadedSessionDates, 0, sessionDates, 0, sessionDates.length);
                System.arraycopy(loadedSessionUnlocked, 0, sessionUnlocked, 0, sessionUnlocked.length);
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
        tvName.setText(name.isEmpty() ? "?" : name);
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
                new HotspotGroupParticipantsFragment(),
                new HotspotGroupSessionsFragment()
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
        setTabTitle(1, "Participants",  participants.size());
        setTabTitle(2, "Sessions",      sessionsRecorded);
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
        View btnViewAll = root.findViewById(R.id.btn_view_reviews_referrals);
        if (btnViewAll != null) btnViewAll.setOnClickListener(v -> openReviewsReferrals());

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
            if (tvDistrict != null)        tvDistrict.setText(DistrictNameUtils.display(currentGroup.getDistrict()));
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
        if (tvCaseworkerName != null) {
            tvCaseworkerName.setClickable(true);
            tvCaseworkerName.setOnClickListener(v -> startActivity(new Intent(this, FacilitatorProfileActivity.class)));
        }

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
                populateFacilitatorDropdown(form);
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
            // Session 1 is always open; later sessions follow the progression gate.
            final boolean locked = sessionNum > 1 && !sessionUnlocked[i];

            Button btn = new Button(this);
            btn.setText("S" + sessionNum + (hasDate ? "\n" + date : "\n--"));
            btn.setTextSize(10f);
            btn.setAllCaps(false);
            btn.setGravity(Gravity.CENTER);
            btn.setLines(2);

            if (locked) {
                // Greyed / muted: the session cannot be opened until the previous one is complete.
                btn.setBackground(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_session_pending));
                btn.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.chimwemwe_text_secondary));
                btn.setAlpha(0.4f);
            } else if (hasDate) {
                btn.setBackground(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_session_recorded));
                btn.setTextColor(Color.WHITE);
                btn.setAlpha(1f);
            } else {
                btn.setBackground(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_session_pending));
                btn.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.chimwemwe_primary));
                btn.setAlpha(1f);
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
                if (locked) {
                    Toast.makeText(this,
                            "You cannot open Session " + sessionNum + " because Session " + (sessionNum - 1) +
                                    " does not have any participants marked as Group or Home Visit.",
                            Toast.LENGTH_LONG).show();
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

        for (int i = 0; i < participants.size(); i++) {
            ParticipantModel p = participants.get(i);
            View row = LayoutInflater.from(this).inflate(R.layout.item_participant_row, llParticipants, false);

            ((TextView) row.findViewById(R.id.tv_sn)).setText(String.format(java.util.Locale.getDefault(), "%02d", i + 1));
            ((TextView) row.findViewById(R.id.tv_caregiver_name)).setText(
                    p.getCaregiverFullName().isEmpty() ? "?" : p.getCaregiverFullName());
            ((TextView) row.findViewById(R.id.tv_child_name)).setText(
                    p.getChildFullName().isEmpty() ? "?" : p.getChildFullName());
            ((TextView) row.findViewById(R.id.tv_child_demographics))
                    .setText(formatChildDemographics(p.getChildDob(), p.getChildSex()));

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
                                ParticipantDao.deleteParticipant(p.getParticipantId());
                                Threading.main(this::loadGroup);
                            }))
                            .setNegativeButton("Cancel", null)
                            .show());

            llParticipants.addView(row);
        }
    }

    /**
     * Builds the child's "age/sex" tag shown next to the child name, e.g. "11/F".
     * Degrades gracefully: shows just the age or just the sex initial when only one is
     * available, and returns "" when neither is known (the tag then renders empty).
     */
    private String formatChildDemographics(String dob, String sex) {
        int age = computeAgeYears(dob);
        String sexInitial = "";
        if (sex != null) {
            String s = sex.trim();
            if (!s.isEmpty() && !s.equalsIgnoreCase("null")) {
                sexInitial = s.substring(0, 1).toUpperCase(java.util.Locale.getDefault());
            }
        }
        if (age >= 0 && !sexInitial.isEmpty()) return age + "/" + sexInitial;
        if (age >= 0)                          return String.valueOf(age);
        return sexInitial; // "" when unknown
    }

    /**
     * Age in whole years from a stored date of birth, or -1 if the DOB is missing or
     * unparseable. Accepts the same range of formats as the participant profile's DOB
     * normaliser, since child_dob is stored as whatever the register form wrote.
     */
    private int computeAgeYears(String dob) {
        if (dob == null) return -1;
        String datePart = dob.trim();
        if (datePart.isEmpty() || datePart.equalsIgnoreCase("null")) return -1;
        if (datePart.contains("T")) datePart = datePart.substring(0, datePart.indexOf('T'));
        else if (datePart.contains(" ")) datePart = datePart.substring(0, datePart.indexOf(' '));
        datePart = datePart.trim();

        String[] inputFormats = {
                "yyyy-MM-dd", "dd-MM-yyyy", "dd/MM/yyyy", "MM/dd/yyyy",
                "yyyy/MM/dd", "dd.MM.yyyy", "yyyy.MM.dd",
                "yyyy-MM-dd'Z'", "d-M-yyyy", "d/M/yyyy", "M/d/yyyy", "yyyy-M-d"
        };
        for (String fmt : inputFormats) {
            try {
                java.time.LocalDate d = java.time.LocalDate.parse(datePart,
                        java.time.format.DateTimeFormatter.ofPattern(fmt));
                java.time.LocalDate today = java.time.LocalDate.now();
                if (d.isAfter(today)) return -1; // future DOB ? treat as unknown
                return java.time.Period.between(d, today).getYears();
            } catch (Exception ignored) {}
        }

        DateTimeFormatter[] twoDigitYearFormats = {
                new DateTimeFormatterBuilder()
                        .appendPattern("d/M/")
                        .appendValueReduced(ChronoField.YEAR, 2, 2, 2000)
                        .toFormatter(),
                new DateTimeFormatterBuilder()
                        .appendPattern("d-M-")
                        .appendValueReduced(ChronoField.YEAR, 2, 2, 2000)
                        .toFormatter()
        };
        for (DateTimeFormatter formatter : twoDigitYearFormats) {
            try {
                LocalDate d = LocalDate.parse(datePart, formatter);
                LocalDate today = LocalDate.now();
                if (d.isAfter(today)) return -1;
                return java.time.Period.between(d, today).getYears();
            } catch (Exception ignored) {}
        }

        try {
            long raw = Long.parseLong(datePart);
            LocalDate d = raw > 100000000000L
                    ? Instant.ofEpochMilli(raw).atZone(ZoneId.systemDefault()).toLocalDate()
                    : LocalDate.ofEpochDay(raw);
            LocalDate today = LocalDate.now();
            if (d.isAfter(today)) return -1;
            return java.time.Period.between(d, today).getYears();
        } catch (Exception ignored) {}
        return -1;
    }

    // ?? Participant form ??????????????????????????????????????

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
     *   null            ? blank form, new insert (sn = next available)
     *   model, id=-1    ? caregiver fields pre-filled, child fields blank, new insert (copied caregiver)
     *   model, id>0     ? all fields pre-filled, update existing record (edit flow)
     */
    private void launchParticipantForm(ParticipantModel existing, int sn) {
        Threading.io(() -> {
            try {
                FormUtils formUtils = new FormUtils(this);
                JSONObject form = formUtils.getFormJson("chimwemwe_participant_register");
                populateParticipantForm(form, existing, sn);
                applyParticipantIdLabel(form, existing != null && "Yes".equalsIgnoreCase(existing.getIsEnrolledOvc()));
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
                applyParticipantIdLabel(form, true);
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
        if (dob == null || dob.isEmpty() || dob.equalsIgnoreCase("null")) return "";
        // Strip time component: "2020-04-20T00:00:00" or "2020-04-20 00:00:00"
        String datePart = dob.trim();
        if (datePart.contains("T")) datePart = datePart.substring(0, datePart.indexOf('T'));
        else if (datePart.contains(" ")) datePart = datePart.substring(0, datePart.indexOf(' '));
        datePart = datePart.trim();

        String[] inputFormats = {
                "yyyy-MM-dd", "dd-MM-yyyy", "dd/MM/yyyy", "MM/dd/yyyy",
                "yyyy/MM/dd", "dd.MM.yyyy", "yyyy.MM.dd",
                "yyyy-MM-dd'Z'", "d-M-yyyy", "d/M/yyyy", "M/d/yyyy", "yyyy-M-d",
                "dd-MM-yy", "dd/MM/yy", "d-M-yy", "d/M/yy"
        };
        DateTimeFormatter out = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        for (String fmt : inputFormats) {
            try {
                LocalDate d = LocalDate.parse(datePart, DateTimeFormatter.ofPattern(fmt));
                return d.format(out);
            } catch (Exception ignored) {}
        }
        return datePart; // unrecognised format ? pass through and let the form handle it
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
            String jsonString = data.getStringExtra(com.vijay.jsonwizard.constants.JsonFormConstants.JSON_FORM_KEY.JSON);
            if (jsonString == null) return;
            try {
                JSONObject form = new JSONObject(jsonString);
                boolean isEdit = !form.optString("entity_id", "").isEmpty();
                ChimwemweFormUtils.ensureFieldValue(form, "group_id", groupIdentifier);
                SupervisorSignOffHelper.prompt(this, (signature, gps) -> Threading.io(() -> {
                    try {
                        ChimwemweFormUtils.ensureFieldValue(form, "supervisor_signature", signature);
                        ChimwemweFormUtils.ensureFieldValue(form, "supervisor_gps", gps);
                        boolean saved = ChimwemweFormUtils.saveRegistration(
                                ChimwemweFormUtils.processRegistration(form, "ec_chimwemwe_review", null),
                                isEdit
                        );

                        Threading.main(() -> {
                            Toast.makeText(
                                    this,
                                    saved ? "Review saved" : "Could not save review. Please try again.",
                                    saved ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG
                            ).show();
                            if (saved) {
                                loadGroup();
                            }
                        });
                    } catch (Exception e) {
                        Timber.e(e, "Error saving monthly review");
                    }
                }));
            } catch (Exception e) {
                Timber.e(e, "Error preparing monthly review sign-off");
            }
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
            Timber.d("OVC record: firstName=%s, lastName=%s, gender=%s, birthdate='%s', uniqueId=%s, householdId=%s, caregiverName=%s",
                    ovcRecord.getFirstName(), ovcRecord.getLastName(), ovcRecord.getGender(),
                    ovcRecord.getBirthdate(), ovcRecord.getUniqueId(), ovcRecord.getHouseholdId(),
                    ovcRecord.getCaregiverName());
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
                m.setEnrollmentDate(fieldValue(childStep,      "enrollment_date"));
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
 
                if (participantId == -1L) {
                    if (m.getChildSurname() == null || m.getChildSurname().trim().isEmpty()) {
                        Threading.main(() -> Toast.makeText(this,
                                "Child surname is required",
                                Toast.LENGTH_LONG).show());
                        return;
                    }
                    if (m.getChildDob() == null || m.getChildDob().trim().isEmpty()) {
                        Threading.main(() -> Toast.makeText(this,
                                "Child date of birth is required",
                                Toast.LENGTH_LONG).show());
                        return;
                    }
                }


                if (participantId == -1L) {
                    ParticipantModel existingByVca = ParticipantDao.getParticipantByVcaId(m.getVcaId());
                    if (existingByVca != null) {
                        String existingGroupId = existingByVca.getGroupId();
                        String currentGroupId = groupIdentifier != null ? groupIdentifier.trim() : "";
                        if (existingGroupId != null && !existingGroupId.trim().isEmpty()
                                && !existingGroupId.trim().equals(currentGroupId)) {
                            Threading.main(() -> Toast.makeText(this,
                                    "This CA is already enrolled in another group.",
                                    Toast.LENGTH_LONG).show());
                            return;
                        }
                    }
                }

                // Enrollment age gate (issue #46): only accept children aged 10?14 at the time of
                // enrollment. Applied only to NEW enrollments (participantId == -1) ? editing an
                // existing participant is not an enrollment, so it isn't re-gated (this also avoids
                // wrongly blocking legacy participants with no stored enrollment date who have since
                // aged out). Age is measured against the enrollment date, falling back to today.
                // A missing/unparseable DOB (age == -1) is not blocked.
                if (participantId == -1L) {
                    int ageAtEnrollment = ChimwemweFormUtils.ageAtEnrollment(m.getChildDob(), m.getEnrollmentDate());
                    if (ageAtEnrollment != -1
                            && (ageAtEnrollment < ChimwemweFormUtils.MIN_ENROLLMENT_AGE
                                || ageAtEnrollment > ChimwemweFormUtils.MAX_ENROLLMENT_AGE)) {
                        final int rejectedAge = ageAtEnrollment;
                        Threading.main(() -> Toast.makeText(this,
                                "Child must be " + ChimwemweFormUtils.MIN_ENROLLMENT_AGE + "?"
                                        + ChimwemweFormUtils.MAX_ENROLLMENT_AGE + " years old at enrollment "
                                        + "(this child is " + rejectedAge + "). Participant not saved.",
                                Toast.LENGTH_LONG).show());
                        return;
                    }
                }

                // Standard OpenSRP save only: the client processor writes to ec_chimwemwe_participant via ec_client_fields.json.
                ChimwemweFormUtils.ensureFieldValue(form, "group_id", groupIdentifier);
                ChimwemweFormUtils.ensureFieldValue(form, "sn", String.valueOf(sn));
                ChimwemweFormUtils.ensureFieldValue(form, "participant_id", participantIdCode);
                boolean isEdit = form.optBoolean("_is_edit", participantId != -1L);
                ChimwemweFormUtils.saveRegistration(
                        ChimwemweFormUtils.processRegistration(
                                form,
                                "ec_chimwemwe_participant",
                                m.getParticipantId()
                        ),
                        isEdit
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

    // ?? Standard event persistence (sync) ????????????????????



    // ?? Helpers ???????????????????????????????????????????????

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
        try {
            CoreJsonFormUtils.populateJsonForm(form, groupToMap(currentGroup));
        } catch (Exception e) {
            Timber.w(e, "populateGroupEditForm");
        }
    }

    private void populateFacilitatorDropdown(JSONObject form) {
        if (form == null) return;
        try {
            String district = currentGroup != null ? currentGroup.getDistrict() : null;
            java.util.LinkedHashSet<String> options = new java.util.LinkedHashSet<>();
            options.add("-- Select --");

            String currentFacilitator = currentGroup != null ? currentGroup.getFacilitatorName2() : null;
            if (currentFacilitator != null && !currentFacilitator.trim().isEmpty()) {
                options.add(currentFacilitator.trim());
            }

            String loggedIn = PreferenceManager.getDefaultSharedPreferences(this)
                    .getString("caseworker_name", "");
            String defaultFacilitator = null;
            if (loggedIn != null && !loggedIn.trim().isEmpty()) {
                defaultFacilitator = loggedIn.trim();
                options.add(defaultFacilitator);
            }

            boolean matchedDistrictOption = false;
            if (district != null && !district.trim().isEmpty()) {
                List<HotspotGroupModel> groups = HotspotGroupDao.getAllGroups();
                if (groups != null) {
                    for (HotspotGroupModel g : groups) {
                        if (g == null) continue;
                        if (g.getDistrict() != null && !g.getDistrict().trim().isEmpty()
                                && !g.getDistrict().trim().equalsIgnoreCase(district.trim())) {
                            continue;
                        }
                        matchedDistrictOption = true;
                        if (g.getFacilitatorName1() != null && !g.getFacilitatorName1().trim().isEmpty()) {
                            options.add(g.getFacilitatorName1().trim());
                        }
                        if (g.getFacilitatorName2() != null && !g.getFacilitatorName2().trim().isEmpty()) {
                            options.add(g.getFacilitatorName2().trim());
                        }
                    }
                }
            }

            if (!matchedDistrictOption && defaultFacilitator != null && !defaultFacilitator.isEmpty()) {
                options.clear();
                options.add("-- Select --");
                options.add(defaultFacilitator);
            }

            JSONArray steps = form.names();
            if (steps == null) return;
            for (int i = 0; i < steps.length(); i++) {
                String stepName = steps.optString(i);
                JSONObject step = form.optJSONObject(stepName);
                if (step == null) continue;
                JSONArray fields = step.optJSONArray("fields");
                if (fields == null) continue;
                for (int j = 0; j < fields.length(); j++) {
                    JSONObject field = fields.optJSONObject(j);
                    if (field == null) continue;
                    if (!"facilitator_name_2".equals(field.optString("key"))) continue;
                    field.put("type", "spinner");
                    JSONArray values = new JSONArray();
                    for (String option : options) {
                        values.put(option);
                    }
                    field.put("values", values);
                    String selected = currentGroup != null ? currentGroup.getFacilitatorName2() : null;
                    if (selected != null && !selected.trim().isEmpty()) {
                        field.put("value", selected.trim());
                    } else if (defaultFacilitator != null && !defaultFacilitator.isEmpty()) {
                        field.put("value", defaultFacilitator);
                    }
                    return;
                }
            }
        } catch (Exception e) {
            Timber.w(e, "populateFacilitatorDropdown");
        }
    }

    private java.util.Map<String, String> groupToMap(HotspotGroupModel g) {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        if (g == null) return map;
        map.put("group_id",                 g.getGroupId());
        map.put("group_name",               g.getGroupName());
        map.put("hotspot_name",             g.getHotspotName());
        map.put("province",                 g.getProvince());
        map.put("district",                 g.getDistrict());
        map.put("location_of_session",      g.getLocationOfSession());
        map.put("location_gps",             g.getLocationGps());
        map.put("nearest_health_facility",  g.getNearestHealthFacility());
        map.put("facilitator_name_1",       g.getFacilitatorName1());
        map.put("facilitator_name_2",       g.getFacilitatorName2());
        map.put("created_date",             g.getCreatedDate());
        return map;
    }

    private void populateParticipantForm(JSONObject form, ParticipantModel existing, int sn)
            throws Exception {
        if (form == null) return;
        // Detect edit by the stable participant code, NOT existing.getId(): OpenSRP's
        // CONFLICT_REPLACE corrupts the row PK to a non-numeric base_entity_id, so getId()
        // reads back as 0 for every saved participant. Keying off getId()>0 misclassified
        // every edit as a new insert, regenerated the participant_id, and created a duplicate.
        boolean isEdit = existing != null
                && existing.getParticipantId() != null
                && !existing.getParticipantId().trim().isEmpty();
        String participantCode = isEdit ? existing.getParticipantId().trim() : null;
        if (participantCode == null || participantCode.trim().isEmpty()) {
            participantCode = "CHIM-" + System.currentTimeMillis();
        }

        if (existing != null) {
            setFieldValue(form, "step1", "caregiver_first_name", existing.getCaregiverFirstName());
            setFieldValue(form, "step1", "caregiver_surname", existing.getCaregiverSurname());
            setFieldValue(form, "step1", "child_first_name", existing.getChildFirstName());
            setFieldValue(form, "step1", "child_surname", existing.getChildSurname());
            setFieldValue(form, "step1", "child_dob", normalizeDob(existing.getChildDob()));
            setFieldValue(form, "step1", "child_sex", existing.getChildSex());
            setFieldValue(form, "step1", "enrollment_date", normalizeDob(existing.getEnrollmentDate()));
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
        form.put("_is_edit", isEdit);
    }

    private void applyParticipantIdLabel(JSONObject form, boolean enrolledInOvc) {
        if (form == null) return;
        String label = enrolledInOvc ? "CA ID" : "Child ID";
        try {
            JSONArray steps = form.names();
            if (steps == null) return;
            for (int i = 0; i < steps.length(); i++) {
                String stepName = steps.optString(i);
                JSONObject step = form.optJSONObject(stepName);
                if (step == null) continue;
                JSONArray fields = step.optJSONArray("fields");
                if (fields == null) continue;
                for (int j = 0; j < fields.length(); j++) {
                    JSONObject field = fields.optJSONObject(j);
                    if (field == null || !"vca_id".equals(field.optString("key"))) continue;
                    field.put("hint", label);
                    field.put("label", label);
                    return;
                }
            }
        } catch (Exception e) {
            Timber.w(e, "applyParticipantIdLabel");
        }
    }

    private void populateOvcParticipantForm(JSONObject form, ChimwemweIndexModel ovcRecord, int sn)
            throws Exception {
        if (form == null || ovcRecord == null) return;
        setFieldValue(form, "step1", "group_id", groupIdentifier);
        setFieldValue(form, "step1", "child_first_name", ovcRecord.getFirstName());
        setFieldValue(form, "step1", "child_surname", ovcRecord.getLastName());
        String normalizedDob = normalizeDob(ovcRecord.getBirthdate());
        setFieldValue(form, "step1", "child_dob", normalizedDob);
        setFieldValue(form, "step1", "birthdate", normalizedDob);
        setFieldValue(form, "step1", "dob", normalizedDob);
        setFieldValue(form, "step1", "date_of_birth", normalizedDob);
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

    // ?? Monthly review ???????????????????????????????????????

    public void openMonthlyReview() {
        openMonthlyReview(null);
    }

    public void openMonthlyReview(@Nullable MonthlyReviewModel existing) {
        if (groupIdentifier == null || groupIdentifier.trim().isEmpty()) {
            Toast.makeText(this, "Save the group first", Toast.LENGTH_SHORT).show();
            return;
        }
        Threading.io(() -> {
            try {
                FormUtils formUtils = new FormUtils(this);
                JSONObject form = formUtils.getFormJson("chimwemwe_participant_review");
                if (form == null) return;
                if (existing != null) {
                    try {
                        CoreJsonFormUtils.populateJsonForm(form, new com.fasterxml.jackson.databind.ObjectMapper().convertValue(existing, java.util.Map.class));
                    } catch (Exception e) {
                        Timber.w(e, "populateJsonForm group review");
                    }
                    String baseEntityId = existing.getBaseEntityId();
                    if (baseEntityId != null && !baseEntityId.trim().isEmpty()) {
                        form.put("entity_id", baseEntityId);
                    }
                }
                launchJsonWizardForm(form, REQUEST_CODE_REVIEW_FORM, false,
                        "Error launching review form");
            } catch (Exception e) {
                Timber.e(e, "Error preparing review form");
            }
        });
    }

    public void openReviewsReferrals() {
        if (groupIdentifier == null || groupIdentifier.trim().isEmpty()) {
            Toast.makeText(this, "Save the group first", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, GroupReviewsReferralsActivity.class);
        intent.putExtra(GroupReviewsReferralsActivity.EXTRA_GROUP_ID, groupIdentifier);
        startActivity(intent);
    }

    // ?? Attendance ????????????????????????????????????????????

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
