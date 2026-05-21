package com.bluecodeltd.chimwemwe.chw.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
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
import com.bluecodeltd.chimwemwe.chw.fragment.ParticipantOverviewFragment;
import com.bluecodeltd.chimwemwe.chw.fragment.ParticipantProfileSection;
import com.bluecodeltd.chimwemwe.chw.fragment.ParticipantReferralsFragment;
import com.bluecodeltd.chimwemwe.chw.fragment.ParticipantReviewsFragment;
import com.bluecodeltd.chimwemwe.chw.model.ChimwemweReferralModel;
import com.bluecodeltd.chimwemwe.chw.model.HotspotGroupModel;
import com.bluecodeltd.chimwemwe.chw.model.ParticipantModel;
import com.bluecodeltd.chimwemwe.chw.model.chimwemweParticipantReviewModel;
import com.bluecodeltd.chimwemwe.chw.util.ChimwemweFormUtils;
import com.bluecodeltd.chimwemwe.chw.util.Threading;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.json.JSONObject;
import org.smartregister.chw.core.utils.CoreJsonFormUtils;
import org.smartregister.util.FormUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

public class ChimwemweParticipantProfileActivity extends AppCompatActivity
        implements ParticipantReviewsFragment.ParticipantFormHost,
        ParticipantReferralsFragment.ParticipantFormHost {

    public static final String EXTRA_PARTICIPANT_ID = "participant_id";
    public static final String EXTRA_PARTICIPANT_CODE = "participant_code";

    private static final int REQ_PARTICIPANT = 3001;
    private static final int REQ_REVIEW      = 3002;
    private static final int REQ_REFERRAL    = 3003;

    private static final String DASH = "\u2014";

    private long participantId;
    private String participantCode;

    private ParticipantModel participant;
    private HotspotGroupModel group;
    private int reviewsCount = 0;
    private int referralsCount = 0;

    private TextView tvCaregiverName;
    private TextView tvChildName;
    private TextView tvSessionsBadge;
    private TextView tvOvcBadge;
    private ProgressBar pbProfile;

    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private TabLayoutMediator tabMediator;
    private FloatingActionButton fabAdd;

    private int currentPage = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chimwemwe_participant_profile_fragments);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        participantId = getIntent().getLongExtra(EXTRA_PARTICIPANT_ID, -1);
        participantCode = getIntent().getStringExtra(EXTRA_PARTICIPANT_CODE);

        tvCaregiverName = findViewById(R.id.tv_caregiver_name);
        tvChildName = findViewById(R.id.tv_child_name);
        tvSessionsBadge = findViewById(R.id.tv_sessions_badge);
        tvOvcBadge = findViewById(R.id.tv_ovc_badge);
        pbProfile = findViewById(R.id.pb_sessions_profile);

        tabLayout = findViewById(R.id.tab_layout);
        viewPager = findViewById(R.id.view_pager);
        fabAdd = findViewById(R.id.fab_add);

        setupViewPager();

        fabAdd.setOnClickListener(v -> {
            if (currentPage == 1) {
                ParticipantReviewsFragment f = findReviewsFragment();
                if (f != null) f.launchReviewForm(null);
            } else if (currentPage == 2) {
                launchReferralForm(null);
            }
        });

        loadData();
    }

    public long getParticipantRowId() {
        return participantId;
    }

    @Nullable
    public ParticipantModel getParticipant() {
        return participant;
    }

    @Nullable
    public HotspotGroupModel getGroup() {
        return group;
    }

    public void setReviewsCount(int count) {
        reviewsCount = Math.max(0, count);
        updateTabTitles();
    }

    public void setReferralsCount(int count) {
        referralsCount = Math.max(0, count);
        updateTabTitles();
    }

    public void refreshHeader() {
        bindHeader();
    }

    @Override
    protected void onDestroy() {
        if (tabMediator != null) tabMediator.detach();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.menu_chimwemwe_participant_profile, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull android.view.MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.edit_record) {
            launchParticipantForm();
            return true;
        }
        if (id == R.id.delete_record) {
            promptDeleteParticipant();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupViewPager() {
        List<Fragment> fragments = Arrays.asList(
                new ParticipantOverviewFragment(),
                new ParticipantReviewsFragment(),
                new ParticipantReferralsFragment()
        );
        ViewPager2Adapter adapter = new ViewPager2Adapter(this, fragments);
        viewPager.setAdapter(adapter);
        viewPager.setOffscreenPageLimit(fragments.size());

        tabLayout.removeAllTabs();
        tabLayout.addTab(tabLayout.newTab());
        tabLayout.addTab(tabLayout.newTab());
        tabLayout.addTab(tabLayout.newTab());

        tabMediator = new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            // Titles set by updateTabTitles()
        });
        tabMediator.attach();

        setTabTitle(0, "Overview", -1);
        setTabTitle(1, "Reviews", 0);
        setTabTitle(2, "Referrals", 0);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentPage = position;
                updateFabVisibility();
            }
        });
        updateFabVisibility();
    }

    private void updateFabVisibility() {
        if (fabAdd == null) return;
        fabAdd.setVisibility(currentPage == 0 ? View.GONE : View.VISIBLE);
    }

    private void updateTabTitles() {
        setTabTitle(0, "Overview", -1);
        setTabTitle(1, "Reviews", reviewsCount);
        setTabTitle(2, "Referrals", referralsCount);
    }

    private void setTabTitle(int position, String title, int count) {
        TabLayout.Tab tab = tabLayout.getTabAt(position);
        if (tab == null) return;
        View custom = LayoutInflater.from(this).inflate(R.layout.item_chimwemwe_group_tab, null);
        ((TextView) custom.findViewById(R.id.tab_title)).setText(title);
        TextView tvCount = custom.findViewById(R.id.tab_count);
        if (count >= 0) {
            tvCount.setText(String.valueOf(count));
        } else {
            tvCount.setVisibility(View.GONE);
        }
        tab.setCustomView(custom);
    }

    @Override
    public void reloadParticipant() {
        loadData();
    }

    private void loadData() {
        Threading.io(() -> {
            ParticipantModel resolved = participantId > 0 ? ParticipantDao.getParticipant(participantId) : null;
            if (resolved == null && participantCode != null && !participantCode.trim().isEmpty()) {
                resolved = ParticipantDao.getParticipantByCode(participantCode);
            }
            final long resolvedRowId = resolved != null ? resolved.getId() : participantId;
            final ParticipantModel p = resolved;
            final HotspotGroupModel g = (p != null)
                    ? HotspotGroupDao.getGroupByBusinessId(p.getGroupId())
                    : null;
            Threading.main(() -> {
                if (p == null) {
                    finish();
                    return;
                }
                participantId = resolvedRowId;
                participant = p;
                group = g;
                // Let the fragments load their own lists and update tab counts via setReviewsCount()/setReferralsCount().
                reviewsCount = 0;
                referralsCount = 0;

                bindHeader();
                updateTabTitles();
                notifySectionFragments();
            });
        });
    }

    private void notifySectionFragments() {
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment instanceof ParticipantProfileSection) {
                ((ParticipantProfileSection) fragment).refreshContent();
            }
        }
    }

    private void bindHeader() {
        if (participant == null) return;
        String caregiver = participant.getCaregiverFullName();
        String child = participant.getChildFullName();
        tvCaregiverName.setText(caregiver.isEmpty() ? DASH : caregiver);
        tvChildName.setText(child.isEmpty() ? DASH : child);

        int sessionsCompleted = participant.getSessionsCompleted();
        tvSessionsBadge.setText(sessionsCompleted + " / 14 sessions");
        if (pbProfile != null) pbProfile.setProgress(sessionsCompleted);

        boolean enrolledOvc = "1".equals(String.valueOf(participant.getIsEnrolledOvc()).trim())
                || "yes".equalsIgnoreCase(String.valueOf(participant.getIsEnrolledOvc()).trim())
                || "true".equalsIgnoreCase(String.valueOf(participant.getIsEnrolledOvc()).trim());
        if (tvOvcBadge != null) tvOvcBadge.setVisibility(enrolledOvc ? View.VISIBLE : View.GONE);

    }

    public void navigateToGroup() {
        String gid = participant != null ? participant.getGroupId() : null;
        if (gid == null || gid.trim().isEmpty()) return;
        Intent intent = new Intent(this, HotspotGroupDetailActivity.class);
        intent.putExtra(HotspotGroupDetailActivity.EXTRA_GROUP_ID, gid);
        startActivity(intent);
    }

    private void promptDeleteParticipant() {
        if (participantId <= 0) return;
        new AlertDialog.Builder(this)
                .setTitle("Delete participant?")
                .setMessage("This will remove the participant and clear any attendance, reviews and referrals linked to them.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> Threading.io(() -> {
                    boolean ok = false;
                    try {
                        // 1. Cascading local soft-delete (immediate UX + cleans up
                        //    attendance / review / referral child rows).
                        ParticipantDao.deleteParticipant(participantId);
                        // 2. Emit a syncable Event so other devices see the delete
                        //    on next pull. The form now carries a delete_status field
                        //    that maps to Client.attributes.delete_status and through
                        //    ec_client_fields.json onto the table column.
                        emitParticipantDeleteEvent(participant);
                        ok = true;
                    } catch (Exception e) {
                        Timber.e(e, "Delete participant failed");
                    }
                    final boolean success = ok;
                    Threading.main(() -> {
                        Toast.makeText(this,
                                success ? "Participant deleted" : "Could not delete participant",
                                Toast.LENGTH_SHORT).show();
                        if (success) {
                            setResult(RESULT_OK);
                            finish();
                        }
                    });
                }))
                .show();
    }

    private void launchParticipantForm() {
        if (participant == null) return;
        Threading.io(() -> {
            try {
                FormUtils formUtils = new FormUtils(this);
                JSONObject form = formUtils.getFormJson("chimwemwe_participant_register");
                if (form == null) return;
                form.put("entity_id", participant.getParticipantId());
                CoreJsonFormUtils.populateJsonForm(form, participantToMap(participant));
                launchJsonForm(form, REQ_PARTICIPANT);
            } catch (Exception e) {
                Timber.e(e, "launchParticipantForm");
            }
        });
    }

    @Override
    public void launchReviewForm(@Nullable chimwemweParticipantReviewModel existing) {
        ParticipantReviewsFragment f = findReviewsFragment();
        if (f != null) f.launchReviewForm(existing);
    }

    @Override
    public void launchReferralForm(@Nullable ChimwemweReferralModel existing) {
        Threading.io(() -> {
            try {
                FormUtils formUtils = new FormUtils(this);
                JSONObject form = formUtils.getFormJson("chimwemwe_referral");
                if (form == null) return;

                if (participant != null) {
                    ChimwemweFormUtils.ensureFieldValue(form, "group_id", participant.getGroupId());
                    ChimwemweFormUtils.ensureFieldValue(form, "participant_id", participant.getParticipantId());
                }

                if (existing != null) {
                    try {
                        CoreJsonFormUtils.populateJsonForm(
                                form,
                                new ObjectMapper().convertValue(existing, Map.class)
                        );
                    } catch (Exception e) {
                        Timber.w(e, "populateJsonForm referral");
                    }
                    String baseEntityId = existing.getBase_entity_id();
                    if (baseEntityId != null && !baseEntityId.trim().isEmpty()) form.put("entity_id", baseEntityId);
                }

                launchJsonForm(form, REQ_REFERRAL);
            } catch (Exception e) {
                Timber.e(e, "launchReferralForm");
            }
        });
    }

    @Nullable
    private ParticipantReviewsFragment findReviewsFragment() {
        try {
            return (ParticipantReviewsFragment) getSupportFragmentManager().findFragmentByTag("f1");
        } catch (Exception ignored) {
            return null;
        }
    }

    private void launchJsonForm(JSONObject form, int requestCode) {
        Threading.main(() -> {
            try {
                Intent intent = new Intent(this,
                        org.smartregister.family.util.Utils.metadata().familyFormActivity);
                com.vijay.jsonwizard.domain.Form cfg = new com.vijay.jsonwizard.domain.Form();
                cfg.setWizard(true);
                cfg.setHideSaveLabel(true);
                cfg.setNextLabel(getString(R.string.next));
                cfg.setPreviousLabel(getString(R.string.previous));
                cfg.setSaveLabel(getString(R.string.submit));
                cfg.setNavigationBackground(R.color.chimwemwe_primary);
                intent.putExtra(com.vijay.jsonwizard.constants.JsonFormConstants.JSON_FORM_KEY.FORM, cfg);
                intent.putExtra(com.vijay.jsonwizard.constants.JsonFormConstants.JSON_FORM_KEY.JSON, form.toString());
                startActivityForResult(intent, requestCode);
            } catch (Exception e) {
                Timber.e(e, "launchJsonForm");
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Reviews are launched/saved from ParticipantReviewsFragment.
        if (requestCode == REQ_REVIEW) return;
        if (resultCode != Activity.RESULT_OK || data == null) return;
        String jsonString = data.getStringExtra(com.vijay.jsonwizard.constants.JsonFormConstants.JSON_FORM_KEY.JSON);
        if (jsonString == null) return;

        Threading.io(() -> {
            try {
                JSONObject form = new JSONObject(jsonString);

                if (requestCode == REQ_PARTICIPANT) {
                    String participantCode = form.optString("entity_id", participant.getParticipantId());
                    if (participantCode == null || participantCode.trim().isEmpty()) {
                        participantCode = "chm-participant-" + participantId;
                    }
                    ChimwemweFormUtils.ensureFieldValue(form, "group_id", participant.getGroupId());
                    ChimwemweFormUtils.ensureFieldValue(form, "sn", String.valueOf(participant.getSn()));
                    ChimwemweFormUtils.ensureFieldValue(form, "participant_id", participantCode);
                    ChimwemweFormUtils.saveRegistration(
                            ChimwemweFormUtils.processRegistration(form, "ec_chimwemwe_participant", participantCode),
                            true
                    );

                } else if (requestCode == REQ_REVIEW) {
                    boolean isEdit = !form.optString("entity_id", "").isEmpty();
                    ChimwemweFormUtils.ensureFieldValue(form, "group_id",
                            participant != null ? participant.getGroupId() : "");
                    ChimwemweFormUtils.ensureFieldValue(form, "participant_id",
                            participant != null ? participant.getParticipantId() : "");
                    ChimwemweFormUtils.saveRegistration(
                            ChimwemweFormUtils.processRegistration(form, "ec_chimwemwe_review", null),
                            isEdit
                    );

                } else if (requestCode == REQ_REFERRAL) {
                    boolean isEdit = !form.optString("entity_id", "").isEmpty();
                    ChimwemweFormUtils.ensureFieldValue(form, "group_id",
                            participant != null ? participant.getGroupId() : "");
                    ChimwemweFormUtils.ensureFieldValue(form, "participant_id",
                            participant != null ? participant.getParticipantId() : "");
                    ChimwemweFormUtils.saveRegistration(
                            ChimwemweFormUtils.processRegistration(form, "ec_chimwemwe_referral", null),
                            isEdit
                    );
                }

                loadData();
            } catch (Exception e) {
                Timber.e(e, "onActivityResult profile");
            }
        });
    }

    /**
     * Builds a Chimwemwe Participant Registration form populated with the
     * participant's current values plus delete_status=1, and submits it through
     * ChimwemweFormUtils so an Event is recorded locally and queued for the next
     * /rest/event/add push. ec_client_fields.json maps Client.attributes.delete_status
     * onto the ec_chimwemwe_participant.delete_status column on other devices on pull.
     */
    private void emitParticipantDeleteEvent(ParticipantModel p) {
        if (p == null) return;
        String entityId = p.getParticipantId();
        if (entityId == null || entityId.trim().isEmpty()) return;
        try {
            FormUtils formUtils = new FormUtils(this);
            JSONObject form = formUtils.getFormJson("chimwemwe_participant_register");
            if (form == null) return;
            form.put("entity_id", entityId);
            java.util.Map<String, String> map = participantToMap(p);
            map.put("delete_status", "1");
            CoreJsonFormUtils.populateJsonForm(form, map);
            ChimwemweFormUtils.saveRegistration(
                    ChimwemweFormUtils.processRegistration(form, "ec_chimwemwe_participant", entityId),
                    true);
        } catch (Exception e) {
            Timber.w(e, "emitParticipantDeleteEvent failed; local delete persists but sync may not");
        }
    }

    private java.util.Map<String, String> participantToMap(ParticipantModel p) {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        if (p == null) return map;
        map.put("participant_id",      p.getParticipantId());
        map.put("group_id",            p.getGroupId());
        map.put("sn",                  String.valueOf(p.getSn()));
        map.put("caregiver_first_name", p.getCaregiverFirstName());
        map.put("caregiver_surname",    p.getCaregiverSurname());
        map.put("child_first_name",     p.getChildFirstName());
        map.put("child_surname",        p.getChildSurname());
        map.put("child_dob",            p.getChildDob());
        map.put("child_sex",            p.getChildSex());
        map.put("is_enrolled_ovc",      p.getIsEnrolledOvc());
        map.put("caregiver_id",         p.getCaregiverId());
        map.put("vca_id",               p.getVcaId());
        return map;
    }
}
