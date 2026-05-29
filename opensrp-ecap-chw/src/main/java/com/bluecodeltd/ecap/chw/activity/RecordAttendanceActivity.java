package com.bluecodeltd.chimwemwe.chw.activity;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bluecodeltd.chimwemwe.chw.R;
import com.bluecodeltd.chimwemwe.chw.dao.ParticipantDao;
import com.bluecodeltd.chimwemwe.chw.dao.SessionAttendanceDao;
import com.bluecodeltd.chimwemwe.chw.dao.SessionAttendanceParticipantDao;
import com.bluecodeltd.chimwemwe.chw.model.AttendanceModel;
import com.bluecodeltd.chimwemwe.chw.model.ParticipantModel;
import com.bluecodeltd.chimwemwe.chw.util.ChimwemweFormUtils;
import com.bluecodeltd.chimwemwe.chw.util.Threading;

import org.json.JSONObject;
import org.smartregister.util.FormUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RecordAttendanceActivity extends AppCompatActivity {

    public static final String EXTRA_GROUP_ID      = "group_id";
    public static final String EXTRA_SESSION_NUMBER = "session_number";

    private static final String[] ATTENDANCE_OPTIONS = {"Absent", "Group", "Home Visit"};

    private String groupId;
    private int    sessionNumber;

    private TextView    etDate;
    private String      capturedSignature = "";
    private AttendanceAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record_attendance);

        groupId       = getIntent().getStringExtra(EXTRA_GROUP_ID);
        sessionNumber = getIntent().getIntExtra(EXTRA_SESSION_NUMBER, 1);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        TextView tvSession = findViewById(R.id.tv_session_label);
        tvSession.setText("Session " + sessionNumber);

        etDate      = findViewById(R.id.et_session_date);

        RecyclerView recycler = findViewById(R.id.recycler_attendance);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));

        adapter = new AttendanceAdapter();
        recycler.setAdapter(adapter);

        EditText etSearch = findViewById(R.id.et_search_participant);
        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(android.text.Editable s) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s == null ? "" : s.toString().trim());
            }
        });

        Button btnSave = findViewById(R.id.btn_save_attendance);
        btnSave.setOnClickListener(v -> saveAll());

        loadData();
    }

    private void loadData() {
        Threading.io(() -> {
            List<ParticipantModel> participants = ParticipantDao.getParticipants(groupId);
            // Read from the normalized per-participant lines table. Map is keyed by the
            // participant business code (participant_id) — the row PK is unusable because
            // OpenSRP overwrites it with a non-numeric base_entity_id.
            Map<String, AttendanceModel> existingMap =
                    SessionAttendanceParticipantDao.getSessionAttendanceMap(groupId, sessionNumber);

            // Build combined list — keyed by the participant business code (participant_id).
            List<AttendanceRowItem> rows = new ArrayList<>();
            for (ParticipantModel p : participants) {
                AttendanceModel att = existingMap.get(p.getParticipantId());
                String cgAtt = att != null ? att.getCaregiverAttendance() : "";
                String chAtt = att != null ? att.getChildAttendance()     : "";
                rows.add(new AttendanceRowItem(p, cgAtt, chAtt));
            }

            // Pre-fill date from first existing record
            String existingDate = SessionAttendanceDao.getSessionDate(groupId, sessionNumber);
            String defaultDate = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date());

            Threading.main(() -> {
                etDate.setText(existingDate != null && !existingDate.trim().isEmpty() ? existingDate : defaultDate);
                adapter.setData(rows);
            });
        });
    }

    // ── Save flow: signature is captured inside the save flow itself ──

    /**
     * Entry point bound to the "Save Attendance" button. Validates the date,
     * then opens the caregiver-signature dialog. The actual write to the DB
     * happens inside the dialog's Save callback (see {@link #promptCaregiverSignatureThenSave}).
     */
    private void saveAll() {
        String date = etDate.getText().toString().trim();
        if (date.isEmpty()) {
            Toast.makeText(this, "Please enter the session date", Toast.LENGTH_SHORT).show();
            return;
        }
        promptCaregiverSignatureThenSave(date);
    }

    /**
     * Opens the signature pad dialog. Tapping Save on the dialog captures the
     * signature and immediately runs {@link #persistAttendance(String)} so the
     * caseworker doesn't have to tap Save twice.
     */
    private void promptCaregiverSignatureThenSave(final String date) {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_caregiver_signature, null);
        final com.github.gcacace.signaturepad.views.SignaturePad pad =
                dialogView.findViewById(R.id.dialog_signature_pad);

        final androidx.appcompat.app.AlertDialog dialog =
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Primary caregiver signature")
                        .setView(dialogView)
                        .setCancelable(false)
                        .create();

        dialogView.findViewById(R.id.dialog_signature_clear)
                .setOnClickListener(v -> pad.clear());
        dialogView.findViewById(R.id.dialog_signature_cancel)
                .setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.dialog_signature_save)
                .setOnClickListener(v -> {
                    if (pad.isEmpty()) {
                        Toast.makeText(this, "Please sign before saving.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        android.graphics.Bitmap bmp = pad.getSignatureBitmap();
                        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                        bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bos);
                        capturedSignature = android.util.Base64.encodeToString(
                                bos.toByteArray(), android.util.Base64.DEFAULT);
                        dialog.dismiss();
                        persistAttendance(date);
                    } catch (Exception e) {
                        Toast.makeText(this, "Could not capture signature. Please retry.", Toast.LENGTH_SHORT).show();
                    }
                });

        dialog.show();
    }

    private void persistAttendance(String date) {
        // Modal indeterminate spinner over the attendance screen while the IO block runs.
        // Blocks accidental re-taps and tells the user the form is being saved after they
        // tapped Save on the signature dialog.
        final android.app.ProgressDialog saving = new android.app.ProgressDialog(this);
        saving.setMessage("Saving attendance…");
        saving.setIndeterminate(true);
        saving.setCancelable(false);
        saving.show();

        List<AttendanceRowItem> rows = adapter.getRows();
        Threading.io(() -> {
            boolean isEditMode = SessionAttendanceDao.hasSession(groupId, sessionNumber);
            saveFormEvent(date, rows, isEditMode);
            Threading.main(() -> {
                if (saving.isShowing() && !isFinishing() && !isDestroyed()) saving.dismiss();
                Toast.makeText(this, "Attendance saved", Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }

    // ── Client processing ─────────────────────────────────────

    private void saveFormEvent(String date, List<AttendanceRowItem> rows, boolean isEditMode) {
        try {
            FormUtils formUtils = new FormUtils(this);
            JSONObject template = formUtils.getFormJson("chimwemwe_session_attendance");
            if (template == null) return;

            JSONObject sessionForm = new JSONObject(template.toString());

            // Preserve base_entity_id on edits: if a session already exists, reuse its original base_entity_id
            // instead of generating/overriding it (which would create a duplicate Client record).
            if (isEditMode) {
                String existingEntityId = SessionAttendanceDao.getSessionBaseEntityId(groupId, sessionNumber);
                if (existingEntityId != null && !existingEntityId.trim().isEmpty()) {
                    sessionForm.put("entity_id", existingEntityId.trim());
                }
            }

            ChimwemweFormUtils.ensureFieldValue(sessionForm, "group_id", groupId);
            ChimwemweFormUtils.ensureFieldValue(sessionForm, "session_number", String.valueOf(sessionNumber));
            ChimwemweFormUtils.ensureFieldValue(sessionForm, "session_date", date);

            String sessionType = "Group Session";
            for (AttendanceRowItem row : rows) {
                if ("Home Visit".equalsIgnoreCase(row.caregiverAttendance) ||
                        "Home Visit".equalsIgnoreCase(row.childAttendance)) {
                    sessionType = "Home Visit";
                    break;
                }
            }
            ChimwemweFormUtils.ensureFieldValue(sessionForm, "session_type", sessionType);
            ChimwemweFormUtils.ensureFieldValue(sessionForm, "caregiver_signature", capturedSignature);

            for (int i = 0; i < rows.size() && i < 20; i++) {
                AttendanceRowItem row = rows.get(i);
                int slot = i + 1;
                String fullName = row.participant.getCaregiverFullName()
                        + " / " + row.participant.getChildFullName();
                String participantCode = row.participant.getParticipantId() != null
                        ? row.participant.getParticipantId() : "";
                ChimwemweFormUtils.ensureFieldValue(sessionForm, "p" + slot + "_label", fullName);
                ChimwemweFormUtils.ensureFieldValue(sessionForm, "p" + slot + "_participant_id", participantCode);
                ChimwemweFormUtils.ensureFieldValue(sessionForm, "p" + slot + "_cg_attendance", row.caregiverAttendance);
                ChimwemweFormUtils.ensureFieldValue(sessionForm, "p" + slot + "_child_attendance", row.childAttendance);
            }

            ChimwemweFormUtils.ProcessedForm processedSessionForm = ChimwemweFormUtils.processRegistration(
                    sessionForm,
                    "ec_chimwemwe_session_attendance",
                    ChimwemweFormUtils.attendanceEntityId(groupId, sessionNumber)
            );
            ChimwemweFormUtils.saveRegistration(processedSessionForm, isEditMode);

            // Persist normalized per-participant lines (unlimited participants per session).
            // entity_id and participant_id use the stable "CHIM-..." business code, NOT the row PK
            // (OpenSRP's CONFLICT_REPLACE corrupts the participant row's INTEGER id with a string,
            // so String.valueOf(participant.getId()) returns "0" for every participant — which
            // would make all line entity_ids collide and overwrite each other).
            JSONObject lineTemplate = formUtils.getFormJson("chimwemwe_session_attendance_participant");
            if (lineTemplate == null) return;
            for (AttendanceRowItem row : rows) {
                if (row == null || row.participant == null) continue;
                String pid = row.participant.getParticipantId();
                if (pid == null || pid.trim().isEmpty()) continue;
                String entityId = "chimwemwe-session-attendance-" + groupId + "-" + sessionNumber + "-" + pid;

                JSONObject lineForm = new JSONObject(lineTemplate.toString());
                lineForm.put("entity_id", entityId);
                ChimwemweFormUtils.ensureFieldValue(lineForm, "group_id", groupId);
                ChimwemweFormUtils.ensureFieldValue(lineForm, "session_number", String.valueOf(sessionNumber));
                ChimwemweFormUtils.ensureFieldValue(lineForm, "session_date", date);
                ChimwemweFormUtils.ensureFieldValue(lineForm, "participant_id", pid);
                ChimwemweFormUtils.ensureFieldValue(lineForm, "caregiver_attendance", row.caregiverAttendance);
                ChimwemweFormUtils.ensureFieldValue(lineForm, "child_attendance", row.childAttendance);

                ChimwemweFormUtils.ProcessedForm processedLine = ChimwemweFormUtils.processRegistration(
                        lineForm,
                        "ec_chimwemwe_session_attendance_participant",
                        entityId
                );
                ChimwemweFormUtils.saveRegistration(processedLine, true);

                // Force the bind_type table columns to the values the user actually picked.
                // The OpenSRP saveRegistration above runs Client merge in edit mode, which
                // preserves existing non-empty attribute values when the new value is empty —
                // so toggling a participant from Group/Home Visit to Absent ('') would silently
                // be discarded. upsertLine writes the columns directly to bypass that.
                SessionAttendanceParticipantDao.upsertLine(
                        groupId, sessionNumber, date, pid,
                        row.caregiverAttendance, row.childAttendance);
            }
        } catch (Exception e) {
            timber.log.Timber.e(e, "saveFormEvent failed for session attendance");
        }
    }

    // ── Data holder ──────────────────────────────────────────

    static class AttendanceRowItem {
        ParticipantModel participant;
        String caregiverAttendance;
        String childAttendance;

        AttendanceRowItem(ParticipantModel p, String cg, String ch) {
            this.participant         = p;
            this.caregiverAttendance = cg;
            this.childAttendance     = ch;
        }
    }

    // ── Adapter ──────────────────────────────────────────────

    class AttendanceAdapter extends RecyclerView.Adapter<AttendanceAdapter.VH> {
        private List<AttendanceRowItem> allRows      = new ArrayList<>();
        private List<AttendanceRowItem> filteredRows = new ArrayList<>();

        void setData(List<AttendanceRowItem> data) {
            allRows      = data != null ? new ArrayList<>(data) : new ArrayList<>();
            filteredRows = new ArrayList<>(allRows);
            notifyDataSetChanged();
        }

        void filter(String query) {
            if (query == null || query.isEmpty()) {
                filteredRows = new ArrayList<>(allRows);
            } else {
                String q = query.toLowerCase(java.util.Locale.getDefault());
                filteredRows = new ArrayList<>();
                for (AttendanceRowItem item : allRows) {
                    String cg = item.participant.getCaregiverFullName().toLowerCase(java.util.Locale.getDefault());
                    String ch = item.participant.getChildFullName().toLowerCase(java.util.Locale.getDefault());
                    if (cg.contains(q) || ch.contains(q)) filteredRows.add(item);
                }
            }
            notifyDataSetChanged();
        }

        /** Always return all rows for saving — filtered view is display-only. */
        List<AttendanceRowItem> getRows() {
            return allRows;
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_attendance_row, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH h, int pos) {
            AttendanceRowItem row = filteredRows.get(pos);
            h.tvCaregiverName.setText(row.participant.getCaregiverFullName());
            h.tvChildName.setText(row.participant.getChildFullName());

            // Null old listeners before setAdapter/setSelection so recycled ViewHolders
            // don't fire stale callbacks that overwrite row data with "".
            h.spinnerCaregiver.setOnItemSelectedListener(null);
            h.spinnerChild.setOnItemSelectedListener(null);

            // Setup spinners
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    RecordAttendanceActivity.this,
                    android.R.layout.simple_spinner_item,
                    ATTENDANCE_OPTIONS);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

            ArrayAdapter<String> adapter2 = new ArrayAdapter<>(
                    RecordAttendanceActivity.this,
                    android.R.layout.simple_spinner_item,
                    ATTENDANCE_OPTIONS);
            adapter2.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

            h.spinnerCaregiver.setAdapter(adapter);
            h.spinnerChild.setAdapter(adapter2);

            // Restore selections
            setSpinnerValue(h.spinnerCaregiver, row.caregiverAttendance);
            setSpinnerValue(h.spinnerChild, row.childAttendance);

            // Track changes — write back into filteredRows (which are shared refs from allRows)
            h.spinnerCaregiver.post(() ->
                    h.spinnerCaregiver.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                            int adapterPos = h.getAdapterPosition();
                            if (adapterPos != RecyclerView.NO_POSITION && adapterPos < filteredRows.size())
                                filteredRows.get(adapterPos).caregiverAttendance =
                                        position == 0 ? "" : ATTENDANCE_OPTIONS[position];
                        }
                        @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
                    }));

            h.spinnerChild.post(() ->
                    h.spinnerChild.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                            int adapterPos = h.getAdapterPosition();
                            if (adapterPos != RecyclerView.NO_POSITION && adapterPos < filteredRows.size())
                                filteredRows.get(adapterPos).childAttendance =
                                        position == 0 ? "" : ATTENDANCE_OPTIONS[position];
                        }
                        @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
                    }));
        }

        private void setSpinnerValue(Spinner spinner, String value) {
            if (value == null || value.isEmpty()) { spinner.setSelection(0); return; }
            for (int i = 0; i < ATTENDANCE_OPTIONS.length; i++) {
                if (ATTENDANCE_OPTIONS[i].equals(value)) { spinner.setSelection(i); return; }
            }
            spinner.setSelection(0);
        }

        @Override
        public int getItemCount() { return filteredRows.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvCaregiverName, tvChildName;
            Spinner  spinnerCaregiver, spinnerChild;

            VH(View v) {
                super(v);
                tvCaregiverName  = v.findViewById(R.id.tv_caregiver_name);
                tvChildName      = v.findViewById(R.id.tv_child_name);
                spinnerCaregiver = v.findViewById(R.id.spinner_caregiver);
                spinnerChild     = v.findViewById(R.id.spinner_child);
            }
        }
    }
}
