package com.bluecodeltd.ecap.chw.activity;

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

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bluecodeltd.ecap.chw.R;
import com.bluecodeltd.ecap.chw.dao.AttendanceDao;
import com.bluecodeltd.ecap.chw.dao.ParticipantDao;
import com.bluecodeltd.ecap.chw.model.AttendanceModel;
import com.bluecodeltd.ecap.chw.model.ParticipantModel;
import com.bluecodeltd.ecap.chw.util.ChimwemweFormUtils;
import com.bluecodeltd.ecap.chw.util.Threading;

import org.json.JSONArray;
import org.json.JSONObject;
import org.smartregister.util.FormUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecordAttendanceActivity extends AppCompatActivity {

    public static final String EXTRA_GROUP_ID      = "group_id";
    public static final String EXTRA_SESSION_NUMBER = "session_number";

    private static final String[] ATTENDANCE_OPTIONS = {"Absent", "Group", "Home Visit"};

    private long groupId;
    private int  sessionNumber;

    private EditText    etDate;
    private AttendanceAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record_attendance);

        groupId       = getIntent().getLongExtra(EXTRA_GROUP_ID, -1);
        sessionNumber = getIntent().getIntExtra(EXTRA_SESSION_NUMBER, 1);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        TextView tvSession = findViewById(R.id.tv_session_label);
        tvSession.setText("Session " + sessionNumber);

        etDate = findViewById(R.id.et_session_date);

        RecyclerView recycler = findViewById(R.id.recycler_attendance);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));

        adapter = new AttendanceAdapter();
        recycler.setAdapter(adapter);

        Button btnSave = findViewById(R.id.btn_save_attendance);
        btnSave.setOnClickListener(v -> saveAll());

        loadData();
    }

    private void loadData() {
        Threading.io(() -> {
            List<ParticipantModel> participants = ParticipantDao.getParticipants(groupId);
            List<AttendanceModel>  existing     = AttendanceDao.getSessionAttendance(groupId, sessionNumber);

            // Map existing attendance by participant_id
            Map<Long, AttendanceModel> existingMap = new HashMap<>();
            for (AttendanceModel a : existing) {
                existingMap.put(a.getParticipantId(), a);
            }

            // Build combined list
            List<AttendanceRowItem> rows = new ArrayList<>();
            for (ParticipantModel p : participants) {
                AttendanceModel att = existingMap.get(p.getId());
                String cgAtt = att != null ? att.getCaregiverAttendance() : "";
                String chAtt = att != null ? att.getChildAttendance()     : "";
                rows.add(new AttendanceRowItem(p, cgAtt, chAtt));
            }

            // Pre-fill date from first existing record
            String existingDate = existing.isEmpty() ? null : existing.get(0).getSessionDate();

            Threading.main(() -> {
                if (existingDate != null) etDate.setText(existingDate);
                adapter.setData(rows);
            });
        });
    }

    private void saveAll() {
        String date = etDate.getText().toString().trim();
        if (date.isEmpty()) {
            Toast.makeText(this, "Please enter the session date", Toast.LENGTH_SHORT).show();
            return;
        }

        List<AttendanceRowItem> rows = adapter.getRows();
        Threading.io(() -> {
            boolean isEditMode = !AttendanceDao.getSessionAttendance(groupId, sessionNumber).isEmpty();
            for (AttendanceRowItem row : rows) {
                AttendanceModel a = new AttendanceModel();
                a.setGroupId(groupId);
                a.setParticipantId(row.participant.getId());
                a.setSessionNumber(sessionNumber);
                a.setSessionDate(date);
                a.setCaregiverAttendance(row.caregiverAttendance);
                a.setChildAttendance(row.childAttendance);
                AttendanceDao.saveAttendance(a);
            }
            saveFormEvent(date, rows, isEditMode);
            Threading.main(() -> {
                Toast.makeText(this, "Attendance saved", Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }

    // ── Client processing ─────────────────────────────────────

    private void saveFormEvent(String date, List<AttendanceRowItem> rows, boolean isEditMode) {
        try {
            FormUtils formUtils = new FormUtils(this);
            JSONObject form = formUtils.getFormJson("chimwemwe_session_attendance");
            if (form == null) return;

            // Populate session info fields
            setFieldValue(form, "step1", "session_number", String.valueOf(sessionNumber));
            setFieldValue(form, "step1", "session_date",   date);

            // Populate per-participant attendance into p1..p20 slots
            String[] steps = {"step2", "step3"};
            for (int i = 0; i < rows.size() && i < 20; i++) {
                AttendanceRowItem row = rows.get(i);
                int slot = i + 1;
                String step = slot <= 10 ? steps[0] : steps[1];
                String fullName = row.participant.getCaregiverFullName()
                        + " / " + row.participant.getChildFullName();
                setFieldValue(form, step, "p" + slot + "_label",          fullName);
                setFieldValue(form, step, "p" + slot + "_cg_attendance",   row.caregiverAttendance);
                setFieldValue(form, step, "p" + slot + "_child_attendance", row.childAttendance);
            }

            ChimwemweFormUtils.ProcessedForm processedForm = ChimwemweFormUtils.processRegistration(
                    form,
                    "ec_chimwemwe_session_attendance",
                    ChimwemweFormUtils.attendanceEntityId(groupId, sessionNumber)
            );
            ChimwemweFormUtils.saveRegistration(processedForm, isEditMode);
        } catch (Exception e) {
            timber.log.Timber.e(e, "saveFormEvent failed for session attendance");
        }
    }

    private void setFieldValue(JSONObject form, String stepKey, String fieldKey, String value) {
        if (value == null) return;
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
        private List<AttendanceRowItem> rows = new ArrayList<>();

        void setData(List<AttendanceRowItem> data) {
            rows = data;
            notifyDataSetChanged();
        }

        List<AttendanceRowItem> getRows() {
            return rows;
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_attendance_row, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH h, int pos) {
            AttendanceRowItem row = rows.get(pos);
            h.tvCaregiverName.setText(row.participant.getCaregiverFullName());
            h.tvChildName.setText(row.participant.getChildFullName());

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

            // Track changes
            h.spinnerCaregiver.post(() ->
                    h.spinnerCaregiver.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                            if (h.getAdapterPosition() != RecyclerView.NO_ID)
                                rows.get(h.getAdapterPosition()).caregiverAttendance =
                                        position == 0 ? "" : ATTENDANCE_OPTIONS[position];
                        }
                        @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
                    }));

            h.spinnerChild.post(() ->
                    h.spinnerChild.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                            if (h.getAdapterPosition() != RecyclerView.NO_ID)
                                rows.get(h.getAdapterPosition()).childAttendance =
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
        public int getItemCount() { return rows.size(); }

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
