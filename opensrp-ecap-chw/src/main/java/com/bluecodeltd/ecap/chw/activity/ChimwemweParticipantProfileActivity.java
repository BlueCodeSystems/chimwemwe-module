package com.bluecodeltd.ecap.chw.activity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.tabs.TabLayout;

import com.bluecodeltd.ecap.chw.R;
import com.bluecodeltd.ecap.chw.dao.ChimwemweReferralDao;
import com.bluecodeltd.ecap.chw.dao.HotspotGroupDao;
import com.bluecodeltd.ecap.chw.dao.MonthlyReviewDao;
import com.bluecodeltd.ecap.chw.dao.ParticipantDao;
import com.bluecodeltd.ecap.chw.model.ChimwemweReferralModel;
import com.bluecodeltd.ecap.chw.model.HotspotGroupModel;
import com.bluecodeltd.ecap.chw.model.MonthlyReviewModel;
import com.bluecodeltd.ecap.chw.model.ParticipantModel;
import com.bluecodeltd.ecap.chw.util.ChimwemweFormUtils;
import com.bluecodeltd.ecap.chw.util.Threading;

import org.json.JSONArray;
import org.json.JSONObject;
import org.smartregister.opd.utils.OpdConstants;
import org.smartregister.util.FormUtils;

import java.util.List;
import timber.log.Timber;

public class ChimwemweParticipantProfileActivity extends AppCompatActivity {

    public static final String EXTRA_PARTICIPANT_ID = "participant_id";

    private static final int REQ_PARTICIPANT = 3001;
    private static final int REQ_REVIEW      = 3002;
    private static final int REQ_REFERRAL    = 3003;

    private long             participantId;
    private ParticipantModel participant;
    private long             pendingEditReviewId   = -1;
    private long             pendingEditReferralId = -1;

    private TextView     tvCaregiverName, tvChildName, tvSessionsBadge, tvOvcBadge;
    private TextView     tvChildDob, tvChildSex, tvVcaId, tvCaregiverId;
    private TextView     tvGroupName, tvHotspotName, tvProvinceDistrict, tvSessionLocation, tvHealthFacility, tvFacilitators;
    private LinearLayout llReviews, llReferrals;
    private LinearLayout tabDetails, tabReviews, tabReferrals;
    private android.widget.ImageButton fabAdd;
    private TabLayout tabLayout;
    private int currentTab = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chimwemwe_participant_profile);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        participantId = getIntent().getLongExtra(EXTRA_PARTICIPANT_ID, -1);

        tvCaregiverName = findViewById(R.id.tv_caregiver_name);
        tvChildName     = findViewById(R.id.tv_child_name);
        tvSessionsBadge = findViewById(R.id.tv_sessions_badge);
        tvOvcBadge      = findViewById(R.id.tv_ovc_badge);
        tvChildDob      = findViewById(R.id.tv_child_dob);
        tvChildSex      = findViewById(R.id.tv_child_sex);
        tvVcaId         = findViewById(R.id.tv_vca_id);
        tvCaregiverId        = findViewById(R.id.tv_caregiver_id);
        tvGroupName          = findViewById(R.id.tv_group_name);
        tvHotspotName        = findViewById(R.id.tv_hotspot_name);
        tvProvinceDistrict   = findViewById(R.id.tv_province_district);
        tvSessionLocation    = findViewById(R.id.tv_session_location);
        tvHealthFacility     = findViewById(R.id.tv_health_facility);
        tvFacilitators       = findViewById(R.id.tv_facilitators);
        llReviews       = findViewById(R.id.ll_reviews);
        llReferrals     = findViewById(R.id.ll_referrals);
        tabDetails      = findViewById(R.id.tab_details);
        tabReviews      = findViewById(R.id.tab_reviews);
        tabReferrals    = findViewById(R.id.tab_referrals);

        tabLayout = findViewById(R.id.tab_layout);
        tabLayout.addTab(tabLayout.newTab());
        tabLayout.addTab(tabLayout.newTab());
        tabLayout.addTab(tabLayout.newTab());
        setTabTitle(0, "Details",   -1);
        setTabTitle(1, "Reviews",    0);
        setTabTitle(2, "Referrals",  0);
        fabAdd = findViewById(R.id.fab_add);
        fabAdd.setOnClickListener(v -> {
            if (currentTab == 1) launchReviewForm(null);
            else if (currentTab == 2) launchReferralForm(null);
        });

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(@NonNull TabLayout.Tab tab) { showTab(tab.getPosition()); }
            @Override public void onTabUnselected(@NonNull TabLayout.Tab tab) {}
            @Override public void onTabReselected(@NonNull TabLayout.Tab tab) {}
        });

        findViewById(R.id.btn_edit_participant).setOnClickListener(v -> launchParticipantForm());

        loadData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (participantId != -1) loadData();
    }

    private void loadData() {
        Threading.io(() -> {
            participant = ParticipantDao.getParticipant(participantId);
            List<MonthlyReviewModel>    reviews   = MonthlyReviewDao.getParticipantReviews(participantId);
            List<ChimwemweReferralModel> referrals = ChimwemweReferralDao.getParticipantReferrals(participantId);
            HotspotGroupModel group = (participant != null)
                    ? HotspotGroupDao.getGroup(participant.getGroupId()) : null;
            Threading.main(() -> {
                if (participant == null) { finish(); return; }
                bindParticipant();
                bindGroup(group);
                buildReviewList(reviews);
                buildReferralList(referrals);
                updateTabTitles(reviews != null ? reviews.size() : 0,
                               referrals != null ? referrals.size() : 0);
            });
        });
    }

    private void bindParticipant() {
        String childFull     = participant.getChildFullName();
        String caregiverFull = participant.getCaregiverFullName();
        tvCaregiverName.setText(caregiverFull.isEmpty() ? "—" : caregiverFull);
        tvChildName.setText(childFull.isEmpty() ? "—" : childFull);
        tvSessionsBadge.setText(participant.getSessionsCompleted() + "/14 sessions");
        if ("yes".equalsIgnoreCase(participant.getIsEnrolledOvc())) tvOvcBadge.setVisibility(View.VISIBLE);
        tvChildDob.setText(orDash(participant.getChildDob()));
        tvChildSex.setText(orDash(participant.getChildSex()));
        tvVcaId.setText(orDash(participant.getVcaId()));
        tvCaregiverId.setText(orDash(participant.getCaregiverId()));
        TextView tvAvatar = findViewById(R.id.tv_avatar);
        tvAvatar.setText(initials(caregiverFull.isEmpty() ? childFull : caregiverFull));
    }

    private void updateTabTitles(int reviewCount, int referralCount) {
        setTabTitle(0, "Details",   -1);
        setTabTitle(1, "Reviews",    reviewCount);
        setTabTitle(2, "Referrals",  referralCount);
    }

    private void setTabTitle(int position, String title, int count) {
        if (tabLayout == null) return;
        TabLayout.Tab tab = tabLayout.getTabAt(position);
        if (tab == null) return;
        android.view.View custom = getLayoutInflater()
                .inflate(R.layout.item_chimwemwe_group_tab, null);
        ((android.widget.TextView) custom.findViewById(R.id.tab_title)).setText(title);
        android.widget.TextView tvCount = custom.findViewById(R.id.tab_count);
        if (count >= 0) {
            tvCount.setText(String.valueOf(count));
        } else {
            tvCount.setVisibility(android.view.View.GONE);
        }
        tab.setCustomView(custom);
    }

    private void showTab(int pos) {
        currentTab = pos;
        tabDetails.setVisibility(pos == 0 ? View.VISIBLE : View.GONE);
        tabReviews.setVisibility(pos == 1 ? View.VISIBLE : View.GONE);
        tabReferrals.setVisibility(pos == 2 ? View.VISIBLE : View.GONE);
        fabAdd.setVisibility(pos == 0 ? View.GONE : View.VISIBLE);
    }

    private void bindGroup(HotspotGroupModel g) {
        if (g == null) return;
        tvGroupName.setText(orDash(g.getGroupName()));
        tvHotspotName.setText(orDash(g.getHotspotName()));
        String province = orDash(g.getProvince());
        String district = orDash(g.getDistrict());
        tvProvinceDistrict.setText(province + " / " + district);
        tvSessionLocation.setText(orDash(g.getLocationOfSession()));
        tvHealthFacility.setText(orDash(g.getNearestHealthFacility()));
        String f1 = g.getFacilitatorName1() != null ? g.getFacilitatorName1().trim() : "";
        String f2 = g.getFacilitatorName2() != null ? g.getFacilitatorName2().trim() : "";
        String facilitators = f1.isEmpty() ? f2 : (f2.isEmpty() ? f1 : f1 + ", " + f2);
        tvFacilitators.setText(orDash(facilitators));
    }

    // ── Reviews list ─────────────────────────────────────────

    private void buildReviewList(List<MonthlyReviewModel> reviews) {
        llReviews.removeAllViews();
        if (reviews == null || reviews.isEmpty()) {
            llReviews.addView(emptyHint("No reviews recorded yet"));
            return;
        }
        for (MonthlyReviewModel r : reviews) {
            View card = LayoutInflater.from(this).inflate(R.layout.item_record_card, llReviews, false);
            ((TextView) card.findViewById(R.id.tv_record_title)).setText(
                    (r.getReviewQuarter() != null ? r.getReviewQuarter() + " — " : "") + orDash(r.getReviewDate()));
            ((TextView) card.findViewById(R.id.tv_record_subtitle)).setText(
                    "Reviewer: " + orDash(r.getReviewerName()));
            ((TextView) card.findViewById(R.id.tv_record_detail)).setText(
                    "Register accurate: " + orDash(r.getRegisterAccurate()));
            card.findViewById(R.id.btn_edit_record).setOnClickListener(v -> launchReviewForm(r));
            llReviews.addView(card);
        }
    }

    // ── Referrals list ───────────────────────────────────────

    private void buildReferralList(List<ChimwemweReferralModel> referrals) {
        llReferrals.removeAllViews();
        if (referrals == null || referrals.isEmpty()) {
            llReferrals.addView(emptyHint("No referrals recorded yet"));
            return;
        }
        for (ChimwemweReferralModel r : referrals) {
            View card = LayoutInflater.from(this).inflate(R.layout.item_record_card, llReferrals, false);
            ((TextView) card.findViewById(R.id.tv_record_title)).setText(orDash(r.getServiceReferredFor()));
            ((TextView) card.findViewById(R.id.tv_record_subtitle)).setText(
                    "Referred: " + orDash(r.getWhoReferred()) + "  \u2022  Date: " + orDash(r.getReferralDate()));
            ((TextView) card.findViewById(R.id.tv_record_detail)).setText("To: " + orDash(r.getReceivingOrg()));
            card.findViewById(R.id.btn_edit_record).setOnClickListener(v -> launchReferralForm(r));
            llReferrals.addView(card);
        }
    }

    private TextView emptyHint(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13f);
        tv.setTextColor(Color.parseColor("#9E9E9E"));
        tv.setPadding(4, 8, 4, 12);
        return tv;
    }

    // ── Form launchers ───────────────────────────────────────

    private void launchParticipantForm() {
        if (participant == null) return;
        Threading.io(() -> {
            try {
                FormUtils formUtils = new FormUtils(this);
                JSONObject form = formUtils.getFormJson("chimwemwe_participant_register");
                if (form == null) return;
                setFieldValue(form, "step1", "caregiver_first_name", participant.getCaregiverFirstName());
                setFieldValue(form, "step1", "caregiver_surname",    participant.getCaregiverSurname());
                setFieldValue(form, "step1", "child_first_name",     participant.getChildFirstName());
                setFieldValue(form, "step1", "child_surname",        participant.getChildSurname());
                setFieldValue(form, "step1", "child_dob",            participant.getChildDob());
                setFieldValue(form, "step1", "child_sex",            participant.getChildSex());
                setFieldValue(form, "step1", "is_enrolled_ovc",      participant.getIsEnrolledOvc());
                setFieldValue(form, "step1", "vca_id",               participant.getVcaId());
                setFieldValue(form, "step1", "caregiver_id",         participant.getCaregiverId());
                setFieldValue(form, "step1", "who_referred",         participant.getWhoReferred());
                setFieldValue(form, "step1", "service_referred_for", participant.getServiceReferredFor());
                setFieldValue(form, "step1", "referral_date",        participant.getReferralDate());
                setFieldValue(form, "step1", "receiving_org",        participant.getReceivingOrg());
                setFieldValue(form, "step1", "job_title",            participant.getJobTitle());
                setFieldValue(form, "step1", "service_date",         participant.getServiceDate());
                String participantCode = participant.getParticipantId();
                if (participantCode == null || participantCode.trim().isEmpty()) {
                    participantCode = "CHIM-" + participantId;
                }
                form.put("entity_id", participantCode);
                form.put("_sn", participant.getSn());
                form.put("_participant_id", participant.getId());
                final JSONObject finalForm = form;
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
                        intent.putExtra(com.vijay.jsonwizard.constants.JsonFormConstants.JSON_FORM_KEY.JSON, finalForm.toString());
                        startActivityForResult(intent, REQ_PARTICIPANT);
                    } catch (Exception e) {
                        Timber.e(e, "launchParticipantForm intent");
                    }
                });
            } catch (Exception e) {
                Timber.e(e, "launchParticipantForm");
            }
        });
    }

    private void launchReviewForm(MonthlyReviewModel existing) {
        pendingEditReviewId = existing != null ? existing.getId() : -1;
        Threading.io(() -> {
            try {
                FormUtils formUtils = new FormUtils(this);
                JSONObject form = formUtils.getFormJson("chimwemwe_monthly_review");
                if (form == null) return;
                if (existing != null) {
                    setFieldValue(form, "step1", "review_quarter",    existing.getReviewQuarter());
                    setFieldValue(form, "step1", "review_date",       existing.getReviewDate());
                    setFieldValue(form, "step1", "reviewer_name",     existing.getReviewerName());
                    setFieldValue(form, "step1", "register_accurate", existing.getRegisterAccurate());
                    setFieldValue(form, "step1", "reviewer_notes",    existing.getReviewerNotes());
                    form.put("entity_id", ChimwemweFormUtils.reviewEntityId(existing.getId()));
                }
                launchJsonForm(form, REQ_REVIEW);
            } catch (Exception e) {
                Timber.e(e, "launchReviewForm");
            }
        });
    }

    private void launchReferralForm(ChimwemweReferralModel existing) {
        pendingEditReferralId = existing != null ? existing.getId() : -1;
        Threading.io(() -> {
            try {
                FormUtils formUtils = new FormUtils(this);
                JSONObject form = formUtils.getFormJson("chimwemwe_referral");
                if (form == null) return;
                if (existing != null) {
                    setFieldValue(form, "step1", "who_referred",          existing.getWhoReferred());
                    setFieldValue(form, "step1", "service_referred_for",  existing.getServiceReferredFor());
                    setFieldValue(form, "step1", "referral_date",         existing.getReferralDate());
                    setFieldValue(form, "step1", "receiving_org",         existing.getReceivingOrg());
                    setFieldValue(form, "step1", "job_title",             existing.getJobTitle());
                    setFieldValue(form, "step1", "service_date",          existing.getServiceDate());
                    form.put("entity_id", ChimwemweFormUtils.referralEntityId(existing.getId()));
                }
                launchJsonForm(form, REQ_REFERRAL);
            } catch (Exception e) {
                Timber.e(e, "launchReferralForm");
            }
        });
    }

    private void launchJsonForm(JSONObject form, int requestCode) {
        Threading.main(() -> {
            try {
                Intent intent = new Intent(this,
                        org.smartregister.family.util.Utils.metadata().familyFormActivity);
                com.vijay.jsonwizard.domain.Form cfg = new com.vijay.jsonwizard.domain.Form();
                cfg.setWizard(false);
                intent.putExtra(com.vijay.jsonwizard.constants.JsonFormConstants.JSON_FORM_KEY.FORM, cfg);
                intent.putExtra(com.vijay.jsonwizard.constants.JsonFormConstants.JSON_FORM_KEY.JSON, form.toString());
                startActivityForResult(intent, requestCode);
            } catch (Exception e) {
                Timber.e(e, "launchJsonForm");
            }
        });
    }

    // ── Activity result ──────────────────────────────────────

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null) return;
        String jsonString = data.getStringExtra(OpdConstants.JSON_FORM_EXTRA.JSON);
        if (jsonString == null) return;

        Threading.io(() -> {
            try {
                JSONObject form = new JSONObject(jsonString);

                if (requestCode == REQ_PARTICIPANT) {
                    ParticipantModel updated = buildParticipantModel(form);
                    updated.setId(participantId);
                    updated.setGroupId(participant.getGroupId());
                    updated.setSn(participant.getSn());
                    String participantCode = form.optString("entity_id", participant.getParticipantId());
                    if (participantCode == null || participantCode.trim().isEmpty()) {
                        participantCode = "chm-participant-" + participantId;
                    }
                    updated.setParticipantId(participantCode);
                    ParticipantDao.updateParticipant(updated);
                    ChimwemweFormUtils.saveRegistration(
                            ChimwemweFormUtils.processRegistration(
                                    form,
                                    "ec_chimwemwe_participant",
                                    updated.getParticipantId()
                            ),
                            true
                    );

                } else if (requestCode == REQ_REVIEW) {
                    MonthlyReviewModel m = buildReviewModel(form);
                    if (pendingEditReviewId > 0) {
                        m.setId(pendingEditReviewId);
                        MonthlyReviewDao.updateReview(m);
                    } else {
                        m.setId(MonthlyReviewDao.insertReview(m));
                    }
                    ChimwemweFormUtils.saveRegistration(
                            ChimwemweFormUtils.processRegistration(
                                    form,
                                    "ec_chimwemwe_monthly_review",
                                    ChimwemweFormUtils.reviewEntityId(m.getId())
                            ),
                            pendingEditReviewId > 0
                    );

                } else if (requestCode == REQ_REFERRAL) {
                    ChimwemweReferralModel r = buildReferralModel(form);
                    if (pendingEditReferralId > 0) {
                        r.setId(pendingEditReferralId);
                        ChimwemweReferralDao.updateReferral(r);
                    } else {
                        r.setId(ChimwemweReferralDao.insertReferral(r));
                    }
                    ChimwemweFormUtils.saveRegistration(
                            ChimwemweFormUtils.processRegistration(
                                    form,
                                    "ec_chimwemwe_referral",
                                    ChimwemweFormUtils.referralEntityId(r.getId())
                            ),
                            pendingEditReferralId > 0
                    );
                }

                pendingEditReviewId   = -1;
                pendingEditReferralId = -1;
                loadData();
            } catch (Exception e) {
                Timber.e(e, "onActivityResult profile");
            }
        });
    }

    // ── Model builders ───────────────────────────────────────

    private ParticipantModel buildParticipantModel(JSONObject form) {
        ParticipantModel m = new ParticipantModel();
        m.setCaregiverFirstName(fieldValue(form, "step1", "caregiver_first_name"));
        m.setCaregiverSurname(fieldValue(form, "step1", "caregiver_surname"));
        m.setChildFirstName(fieldValue(form, "step1", "child_first_name"));
        m.setChildSurname(fieldValue(form, "step1", "child_surname"));
        m.setChildDob(fieldValue(form, "step1", "child_dob"));
        m.setChildSex(fieldValue(form, "step1", "child_sex"));
        m.setIsEnrolledOvc(fieldValue(form, "step1", "is_enrolled_ovc"));
        m.setVcaId(fieldValue(form, "step1", "vca_id"));
        m.setCaregiverId(fieldValue(form, "step1", "caregiver_id"));
        m.setWhoReferred(fieldValue(form, "step1", "who_referred"));
        m.setServiceReferredFor(fieldValue(form, "step1", "service_referred_for"));
        m.setReferralDate(fieldValue(form, "step1", "referral_date"));
        m.setReceivingOrg(fieldValue(form, "step1", "receiving_org"));
        m.setJobTitle(fieldValue(form, "step1", "job_title"));
        m.setServiceDate(fieldValue(form, "step1", "service_date"));
        return m;
    }

    private MonthlyReviewModel buildReviewModel(JSONObject form) {
        MonthlyReviewModel m = new MonthlyReviewModel();
        m.setParticipantId(participantId);
        m.setGroupId(participant != null ? participant.getGroupId() : 0);
        m.setReviewQuarter(fieldValue(form, "step1", "review_quarter"));
        m.setReviewDate(fieldValue(form, "step1", "review_date"));
        m.setReviewerName(fieldValue(form, "step1", "reviewer_name"));
        m.setRegisterAccurate(fieldValue(form, "step1", "register_accurate"));
        m.setReviewerNotes(fieldValue(form, "step1", "reviewer_notes"));
        return m;
    }

    private ChimwemweReferralModel buildReferralModel(JSONObject form) {
        ChimwemweReferralModel r = new ChimwemweReferralModel();
        r.setParticipantId(participantId);
        r.setGroupId(participant != null ? participant.getGroupId() : 0);
        r.setWhoReferred(fieldValue(form, "step1", "who_referred"));
        r.setServiceReferredFor(fieldValue(form, "step1", "service_referred_for"));
        r.setReferralDate(fieldValue(form, "step1", "referral_date"));
        r.setReceivingOrg(fieldValue(form, "step1", "receiving_org"));
        r.setJobTitle(fieldValue(form, "step1", "job_title"));
        r.setServiceDate(fieldValue(form, "step1", "service_date"));
        return r;
    }

    // ── Client processing ────────────────────────────────────



    // ── Helpers ──────────────────────────────────────────────

    private String fieldValue(JSONObject form, String stepKey, String fieldKey) {
        try {
            JSONObject step = form.optJSONObject(stepKey);
            if (step == null) return "";
            JSONArray fields = step.optJSONArray("fields");
            if (fields == null) return "";
            for (int i = 0; i < fields.length(); i++) {
                JSONObject f = fields.getJSONObject(i);
                if (fieldKey.equals(f.optString("key"))) return f.optString("value", "");
            }
        } catch (Exception ignored) {}
        return "";
    }

    private void setFieldValue(JSONObject form, String stepKey, String fieldKey, String value) {
        if (value == null) return;
        try {
            JSONObject step = form.optJSONObject(stepKey);
            if (step == null) return;
            JSONArray fields = step.optJSONArray("fields");
            if (fields == null) return;
            for (int i = 0; i < fields.length(); i++) {
                JSONObject f = fields.getJSONObject(i);
                if (fieldKey.equals(f.optString("key"))) { f.put("value", value); return; }
            }
        } catch (Exception ignored) {}
    }

    private String initials(String name) {
        if (name == null || name.isEmpty()) return "P";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return String.valueOf(parts[0].charAt(0)).toUpperCase();
        return (String.valueOf(parts[0].charAt(0)) + String.valueOf(parts[parts.length - 1].charAt(0))).toUpperCase();
    }

    private String orDash(String v) { return (v == null || v.isEmpty()) ? "\u2014" : v; }

}
