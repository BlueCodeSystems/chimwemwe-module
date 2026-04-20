package com.bluecodeltd.ecap.chw.activity;

import static com.bluecodeltd.ecap.chw.util.IndexClientsUtils.getAllSharedPreferences;
import static com.bluecodeltd.ecap.chw.util.IndexClientsUtils.getFormTag;
import static com.bluecodeltd.ecap.chw.util.JsonFormUtils.tagSyncMetadata;
import static org.smartregister.chw.fp.util.FpUtil.getClientProcessorForJava;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.bluecodeltd.ecap.chw.R;
import com.bluecodeltd.ecap.chw.dao.AttendanceDao;
import com.bluecodeltd.ecap.chw.dao.HotspotGroupDao;
import com.bluecodeltd.ecap.chw.dao.MonthlyReviewDao;
import com.bluecodeltd.ecap.chw.dao.ParticipantDao;
import com.bluecodeltd.ecap.chw.model.ChimwemweIndexModel;
import com.bluecodeltd.ecap.chw.model.HotspotGroupModel;
import com.bluecodeltd.ecap.chw.model.MonthlyReviewModel;
import com.bluecodeltd.ecap.chw.model.ParticipantModel;
import com.bluecodeltd.ecap.chw.util.Threading;

import org.json.JSONArray;
import org.json.JSONObject;
import org.smartregister.opd.utils.OpdConstants;
import org.smartregister.util.FormUtils;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import timber.log.Timber;

public class HotspotGroupDetailActivity extends AppCompatActivity {

    public static final String EXTRA_GROUP_ID = "group_id";

    private static final int REQUEST_CODE_PARTICIPANT_FORM = 2001;
    private static final int REQUEST_CODE_REVIEW_FORM     = 2002;
    private static final int REQUEST_CODE_OVC_SEARCH      = 2003;

    private long         groupId = -1;
    private int          pendingNextSn = 1;
    private EditText     etHotspotName, etGroupName;
    private GridLayout   gridSessions;
    private LinearLayout llParticipants;
    private TextView     tvLastReview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hotspot_group_detail);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        groupId        = getIntent().getLongExtra(EXTRA_GROUP_ID, -1);
        etHotspotName  = findViewById(R.id.et_hotspot_name);
        etGroupName    = findViewById(R.id.et_group_name);
        gridSessions   = findViewById(R.id.grid_sessions);
        llParticipants = findViewById(R.id.ll_participants);
        tvLastReview   = findViewById(R.id.tv_last_review);

        Button btnSave = findViewById(R.id.btn_save_group);
        btnSave.setOnClickListener(v -> saveGroup());

        Button btnAddParticipant = findViewById(R.id.btn_add_participant);
        btnAddParticipant.setOnClickListener(v -> openAddParticipant());

        Button btnReview = findViewById(R.id.btn_monthly_review);
        btnReview.setOnClickListener(v -> openMonthlyReview());

        if (groupId != -1) {
            if (getSupportActionBar() != null) getSupportActionBar().setTitle("Group Details");
            loadGroup();
        } else {
            if (getSupportActionBar() != null) getSupportActionBar().setTitle("New Group");
            buildSessionGrid(new String[14]);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (groupId != -1) loadGroup();
    }

    private void loadGroup() {
        Threading.io(() -> {
            HotspotGroupModel group        = HotspotGroupDao.getGroup(groupId);
            List<ParticipantModel> participants = ParticipantDao.getParticipants(groupId);
            List<MonthlyReviewModel> reviews   = MonthlyReviewDao.getReviews(groupId);

            String[] sessionDates = new String[14];
            for (int i = 1; i <= 14; i++) {
                sessionDates[i - 1] = AttendanceDao.getSessionDate(groupId, i);
            }

            Threading.main(() -> {
                if (group != null) {
                    etHotspotName.setText(group.getHotspotName());
                    etGroupName.setText(group.getGroupName());
                }
                buildSessionGrid(sessionDates);
                buildParticipantList(participants);
                updateLastReviewLabel(reviews);
            });
        });
    }

    private void updateLastReviewLabel(List<MonthlyReviewModel> reviews) {
        if (tvLastReview == null) return;
        if (reviews == null || reviews.isEmpty()) {
            tvLastReview.setText("No reviews recorded");
        } else {
            MonthlyReviewModel last = reviews.get(0);
            tvLastReview.setText("Last reviewed: " + last.getReviewDate()
                    + " by " + last.getReviewerName());
        }
    }

    private void saveGroup() {
        String hotspot   = etHotspotName.getText().toString().trim();
        String groupName = etGroupName.getText().toString().trim();
        if (hotspot.isEmpty() || groupName.isEmpty()) {
            Toast.makeText(this, "Please fill in Hotspot Name and Group Name", Toast.LENGTH_SHORT).show();
            return;
        }

        Threading.io(() -> {
            if (groupId == -1) {
                HotspotGroupModel m = new HotspotGroupModel();
                m.setHotspotName(hotspot);
                m.setGroupName(groupName);
                m.setCreatedDate(LocalDate.now().toString());
                groupId = HotspotGroupDao.insertGroup(m);
            } else {
                HotspotGroupModel m = new HotspotGroupModel();
                m.setId(groupId);
                m.setHotspotName(hotspot);
                m.setGroupName(groupName);
                HotspotGroupDao.updateGroup(m);
            }
            Threading.main(() -> {
                Toast.makeText(this, "Group saved", Toast.LENGTH_SHORT).show();
                findViewById(R.id.btn_add_participant).setEnabled(true);
                buildSessionGrid(new String[14]);
            });
        });
    }

    private void buildSessionGrid(String[] sessionDates) {
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
                btn.setBackgroundColor(Color.parseColor("#0D5C73"));
                btn.setTextColor(Color.WHITE);
            } else {
                btn.setBackgroundColor(Color.parseColor("#E0F7FA"));
                btn.setTextColor(Color.parseColor("#0D5C73"));
            }

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width       = 0;
            params.height      = GridLayout.LayoutParams.WRAP_CONTENT;
            params.columnSpec  = GridLayout.spec(i % cols, 1, 1f);
            params.rowSpec     = GridLayout.spec(i / cols, 1, 1f);
            params.setMargins(4, 4, 4, 4);
            btn.setLayoutParams(params);

            btn.setOnClickListener(v -> {
                if (groupId == -1) {
                    Toast.makeText(this, "Save the group first", Toast.LENGTH_SHORT).show();
                    return;
                }
                openRecordAttendance(sessionNum);
            });

            gridSessions.addView(btn);
        }
    }

    private void buildParticipantList(List<ParticipantModel> participants) {
        llParticipants.removeAllViews();
        if (participants == null || participants.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("No participants yet. Tap '+ Add' to add participants.");
            tv.setTextColor(Color.parseColor("#607D8B"));
            tv.setTextSize(13f);
            tv.setPadding(8, 8, 8, 8);
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
            tvDone.setText(p.getSessionsCompleted() + "/14");
            if (p.isCompletedProgram()) {
                tvDone.setTextColor(Color.parseColor("#2E7D32"));
            }

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

    private void openAddParticipant() {
        if (groupId == -1) {
            Toast.makeText(this, "Save the group first", Toast.LENGTH_SHORT).show();
            return;
        }
        Threading.io(() -> {
            List<ParticipantModel> existing = ParticipantDao.getParticipants(groupId);
            int nextSn = ParticipantDao.nextSn(groupId);

            // Deduplicate caregivers by full name for the picker list
            java.util.LinkedHashMap<String, ParticipantModel> uniqueCaregivers = new java.util.LinkedHashMap<>();
            for (ParticipantModel p : existing) {
                String name = p.getCaregiverFullName().trim();
                if (!name.isEmpty() && !uniqueCaregivers.containsKey(name)) {
                    uniqueCaregivers.put(name, p);
                }
            }

            Threading.main(() ->
                    showOvcEnrollmentDialog(
                            new java.util.ArrayList<>(uniqueCaregivers.values()), nextSn));
        });
    }

    private void showOvcEnrollmentDialog(List<ParticipantModel> uniqueCaregivers, int nextSn) {
        new AlertDialog.Builder(this)
                .setTitle("Add Participant")
                .setMessage("Is the child enrolled in the OVC Comprehensive program?")
                .setPositiveButton("Yes", (d, w) -> {
                    pendingNextSn = nextSn;
                    Intent intent = new Intent(this, ChimwemweSearchActivity.class);
                    intent.putExtra(ChimwemweSearchActivity.EXTRA_SELECTION_MODE, true);
                    startActivityForResult(intent, REQUEST_CODE_OVC_SEARCH);
                })
                .setNegativeButton("No", (d, w) -> {
                    if (uniqueCaregivers.isEmpty()) {
                        launchParticipantForm(null, nextSn);
                    } else {
                        showCaregiverChoiceDialog(uniqueCaregivers, nextSn);
                    }
                })
                .show();
    }

    private void showCaregiverChoiceDialog(List<ParticipantModel> uniqueCaregivers, int nextSn) {
        new AlertDialog.Builder(this)
                .setTitle("Add Participant")
                .setMessage("Is this a new caregiver or does the caregiver already attend this group?")
                .setPositiveButton("New Caregiver", (d, w) -> launchParticipantForm(null, nextSn))
                .setNegativeButton("Copy Existing", (d, w) ->
                        showCaregiverPickerDialog(uniqueCaregivers, nextSn))
                .show();
    }

    private void showCaregiverPickerDialog(List<ParticipantModel> caregivers, int nextSn) {
        String[] names = new String[caregivers.size()];
        for (int i = 0; i < caregivers.size(); i++) {
            ParticipantModel p = caregivers.get(i);
            String name = p.getCaregiverFullName();
            String id   = p.getCaregiverId();
            names[i] = name + (id != null && !id.isEmpty() ? "  (" + id + ")" : "");
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Caregiver")
                .setItems(names, (d, which) -> {
                    ParticipantModel source = caregivers.get(which);
                    // Build a template: caregiver fields copied, child fields blank, id = -1 (new insert)
                    ParticipantModel template = new ParticipantModel();
                    template.setId(-1L);
                    template.setCaregiverFirstName(source.getCaregiverFirstName());
                    template.setCaregiverSurname(source.getCaregiverSurname());
                    template.setCaregiverId(source.getCaregiverId());
                    launchParticipantForm(template, nextSn);
                })
                .setNegativeButton("Cancel", null)
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

                boolean isEdit = existing != null && existing.getId() > 0;

                if (existing != null) {
                    setFieldValue(form, "step1", "caregiver_first_name", existing.getCaregiverFirstName());
                    setFieldValue(form, "step1", "caregiver_surname",    existing.getCaregiverSurname());
                    setFieldValue(form, "step2", "child_first_name",     existing.getChildFirstName());
                    setFieldValue(form, "step2", "child_surname",        existing.getChildSurname());
                    setFieldValue(form, "step2", "child_dob",            existing.getChildDob());
                    setFieldValue(form, "step2", "child_sex",            existing.getChildSex());
                    setFieldValue(form, "step2", "is_enrolled_ovc",      existing.getIsEnrolledOvc());
                    setFieldValue(form, "step3", "who_referred",         existing.getWhoReferred());
                    setFieldValue(form, "step3", "service_referred_for", existing.getServiceReferredFor());
                    setFieldValue(form, "step3", "referral_date",        existing.getReferralDate());
                    setFieldValue(form, "step3", "receiving_org",        existing.getReceivingOrg());
                    setFieldValue(form, "step3", "job_title",            existing.getJobTitle());
                    setFieldValue(form, "step3", "service_date",         existing.getServiceDate());
                }

                // Auto-generate IDs for new participants; preserve existing IDs in edit mode
                if (isEdit) {
                    setFieldValue(form, "step2", "vca_id",       existing.getVcaId());
                    setFieldValue(form, "step2", "caregiver_id", existing.getCaregiverId());
                } else {
                    setFieldValue(form, "step2", "vca_id",       String.valueOf(new Random().nextInt(900_000_000)));
                    setFieldValue(form, "step2", "caregiver_id", String.valueOf(new Random().nextInt(900_000_000)));
                }

                // Stash routing metadata in the form root so onActivityResult can retrieve them
                form.put("_sn",             sn);
                form.put("_participant_id", existing != null ? existing.getId() : -1L);

                final JSONObject finalForm = form;
                Threading.main(() -> {
                    try {
                        Intent intent = new Intent(
                                this, org.smartregister.family.util.Utils.metadata().familyFormActivity);
                        com.vijay.jsonwizard.domain.Form cfg = new com.vijay.jsonwizard.domain.Form();
                        cfg.setWizard(true);
                        cfg.setHideSaveLabel(true);
                        cfg.setNextLabel(getString(R.string.next));
                        cfg.setPreviousLabel(getString(R.string.previous));
                        cfg.setSaveLabel(getString(R.string.submit));
                        cfg.setNavigationBackground(R.color.chimwemwe_primary);
                        intent.putExtra(
                                com.vijay.jsonwizard.constants.JsonFormConstants.JSON_FORM_KEY.FORM, cfg);
                        intent.putExtra(
                                com.vijay.jsonwizard.constants.JsonFormConstants.JSON_FORM_KEY.JSON,
                                finalForm.toString());
                        startActivityForResult(intent, REQUEST_CODE_PARTICIPANT_FORM);
                    } catch (Exception e) {
                        Timber.e(e, "Error launching participant form");
                    }
                });
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

                setFieldValue(form, "step1", "child_first_name",     ovcRecord.getFirstName());
                setFieldValue(form, "step1", "child_surname",        ovcRecord.getLastName());
                setFieldValue(form, "step1", "child_dob",            normalizeDob(ovcRecord.getBirthdate()));
                setFieldValue(form, "step1", "child_sex",            normalizeGender(ovcRecord.getGender()));
                setFieldValue(form, "step1", "is_enrolled_ovc",      "Yes");
                setFieldValue(form, "step1", "vca_id",               ovcRecord.getUniqueId());
                setFieldValue(form, "step1", "caregiver_id",         ovcRecord.getHouseholdId());

                form.put("_sn", sn);
                form.put("_participant_id", -1L);

                final JSONObject finalForm = form;
                Threading.main(() -> {
                    try {
                        Intent intent = new Intent(
                                this, org.smartregister.family.util.Utils.metadata().familyFormActivity);
                        com.vijay.jsonwizard.domain.Form cfg = new com.vijay.jsonwizard.domain.Form();
                        cfg.setWizard(true);
                        cfg.setHideSaveLabel(true);
                        cfg.setNextLabel(getString(R.string.next));
                        cfg.setPreviousLabel(getString(R.string.previous));
                        cfg.setSaveLabel(getString(R.string.submit));
                        cfg.setNavigationBackground(R.color.chimwemwe_primary);
                        intent.putExtra(
                                com.vijay.jsonwizard.constants.JsonFormConstants.JSON_FORM_KEY.FORM, cfg);
                        intent.putExtra(
                                com.vijay.jsonwizard.constants.JsonFormConstants.JSON_FORM_KEY.JSON,
                                finalForm.toString());
                        startActivityForResult(intent, REQUEST_CODE_PARTICIPANT_FORM);
                    } catch (Exception e) {
                        Timber.e(e, "Error launching OVC participant form");
                    }
                });
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
        // Strip time component if present (e.g. "2020-04-20T00:00:00")
        String datePart = dob.contains("T") ? dob.substring(0, dob.indexOf('T')) : dob.trim();
        String[] inputFormats = {
                "yyyy-MM-dd", "dd-MM-yyyy", "dd/MM/yyyy", "MM/dd/yyyy",
                "yyyy/MM/dd", "dd.MM.yyyy", "yyyy.MM.dd"
        };
        java.time.format.DateTimeFormatter out =
                java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy");
        for (String fmt : inputFormats) {
            try {
                java.time.LocalDate d = java.time.LocalDate.parse(
                        datePart, java.time.format.DateTimeFormatter.ofPattern(fmt));
                return d.format(out);
            } catch (Exception ignored) {}
        }
        return "";
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null) return;

        if (requestCode == REQUEST_CODE_REVIEW_FORM) {
            String jsonString = data.getStringExtra(OpdConstants.JSON_FORM_EXTRA.JSON);
            if (jsonString == null) return;
            Threading.io(() -> {
                try {
                    JSONObject form  = new JSONObject(jsonString);
                    JSONObject step1 = form.optJSONObject("step1");

                    MonthlyReviewModel r = new MonthlyReviewModel();
                    r.setGroupId(groupId);
                    r.setReviewQuarter(fieldValue(step1,    "review_quarter"));
                    r.setReviewDate(fieldValue(step1,       "review_date"));
                    r.setReviewerName(fieldValue(step1,     "reviewer_name"));
                    r.setRegisterAccurate(fieldValue(step1, "register_accurate"));
                    r.setReviewerNotes(fieldValue(step1,    "reviewer_notes"));
                    MonthlyReviewDao.insertReview(r);
                    saveFormEvent(form, "ec_chimwemwe_monthly_review");

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
                m.setGroupId(groupId);
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

                if (participantId == -1L) {
                    m.setParticipantCode(UUID.randomUUID().toString());
                    ParticipantDao.insertParticipant(m);
                } else {
                    ParticipantDao.updateParticipant(m);
                }
                saveFormEvent(form, "ec_chimwemwe_participant");

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

    private void saveFormEvent(JSONObject form, String bindType) {
        try {
            org.smartregister.repository.AllSharedPreferences prefs = getAllSharedPreferences();
            org.smartregister.domain.tag.FormTag formTag = getFormTag();

            String entityId = org.smartregister.util.JsonFormUtils.generateRandomUUIDString();
            org.json.JSONArray fields = org.smartregister.util.JsonFormUtils.fields(form);
            JSONObject metadata = form.optJSONObject("metadata");
            String encounterType = form.optString("encounter_type", "");

            if (fields == null || metadata == null || encounterType.isEmpty()) return;

            org.smartregister.clientandeventmodel.Event event =
                    org.smartregister.util.JsonFormUtils.createEvent(
                            fields, metadata, formTag, entityId, encounterType, bindType);
            tagSyncMetadata(event);

            org.smartregister.clientandeventmodel.Client client =
                    org.smartregister.util.JsonFormUtils.createBaseClient(fields, formTag, entityId);

            org.smartregister.sync.helper.ECSyncHelper syncHelper =
                    com.bluecodeltd.ecap.chw.application.ChwApplication.getInstance().getEcSyncHelper();
            syncHelper.addClient(entityId,
                    new JSONObject(org.smartregister.util.JsonFormUtils.gson.toJson(client)));
            syncHelper.addEvent(entityId,
                    new JSONObject(org.smartregister.util.JsonFormUtils.gson.toJson(event)));

            java.util.Date currentSyncDate = new java.util.Date(prefs.fetchLastUpdatedAtDate(0));
            List<org.smartregister.domain.db.EventClient> saved =
                    syncHelper.getEvents(Collections.singletonList(event.getFormSubmissionId()));
            getClientProcessorForJava().processClient(saved);
            prefs.saveLastUpdatedAtDate(currentSyncDate.getTime());

        } catch (Exception e) {
            Timber.e(e, "saveFormEvent failed for bindType=%s", bindType);
        }
    }

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

    // ── Monthly review ───────────────────────────────────────

    private void openMonthlyReview() {
        if (groupId == -1) {
            Toast.makeText(this, "Save the group first", Toast.LENGTH_SHORT).show();
            return;
        }
        Threading.io(() -> {
            try {
                FormUtils formUtils = new FormUtils(this);
                JSONObject form = formUtils.getFormJson("chimwemwe_monthly_review");
                final JSONObject finalForm = form;
                Threading.main(() -> {
                    try {
                        Intent intent = new Intent(
                                this, org.smartregister.family.util.Utils.metadata().familyFormActivity);
                        com.vijay.jsonwizard.domain.Form cfg = new com.vijay.jsonwizard.domain.Form();
                        cfg.setWizard(false);
                        cfg.setHideSaveLabel(true);
                        cfg.setSaveLabel(getString(R.string.submit));
                        cfg.setNavigationBackground(R.color.chimwemwe_primary);
                        intent.putExtra(
                                com.vijay.jsonwizard.constants.JsonFormConstants.JSON_FORM_KEY.FORM, cfg);
                        intent.putExtra(
                                com.vijay.jsonwizard.constants.JsonFormConstants.JSON_FORM_KEY.JSON,
                                finalForm.toString());
                        startActivityForResult(intent, REQUEST_CODE_REVIEW_FORM);
                    } catch (Exception e) {
                        Timber.e(e, "Error launching review form");
                    }
                });
            } catch (Exception e) {
                Timber.e(e, "Error preparing review form");
            }
        });
    }

    // ── Attendance ────────────────────────────────────────────

    private void openRecordAttendance(int sessionNum) {
        Threading.io(() -> {
            int count = ParticipantDao.countParticipants(groupId);
            Threading.main(() -> {
                if (count == 0) {
                    Toast.makeText(this, "Add participants before recording attendance",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent intent = new Intent(this, RecordAttendanceActivity.class);
                intent.putExtra(RecordAttendanceActivity.EXTRA_GROUP_ID, groupId);
                intent.putExtra(RecordAttendanceActivity.EXTRA_SESSION_NUMBER, sessionNum);
                startActivity(intent);
            });
        });
    }
}
