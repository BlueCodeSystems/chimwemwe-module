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

    private static final int REQ_LOCATION = 4001;

    private String groupId;
    private int    sessionNumber;

    private TextView    etDate;
    private String      capturedGps       = "";
    // Session-level GPS loaded on edit, reused when every present participant already signed so the
    // save does not overwrite the stored session_gps with an empty value (Option A).
    private String      existingSessionGps  = "";
    // Pending callback, set when the user taps "Get location" before the
    // permission has been granted. Fired in onRequestPermissionsResult once
    // the user grants ACCESS_FINE_LOCATION.
    private Runnable    pendingLocationCallback;
    private AttendanceAdapter adapter;

    /** Receives the captured "lat,lng" string so a single capture routine can feed
     *  the participant/session GPS fields. */
    private interface GpsSink { void accept(String gps); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record_attendance);

        groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);
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

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void loadData() {
        Threading.io(() -> {
            List<ParticipantModel> participants = ParticipantDao.getParticipants(groupId);
            Map<String, AttendanceModel> existingMap =
                    SessionAttendanceParticipantDao.getSessionAttendanceMap(groupId, sessionNumber);
            List<AttendanceRowItem> rows = new ArrayList<>();
            for (ParticipantModel p : participants) {
                AttendanceModel att = existingMap.get(p.getParticipantId());
                String cgAtt = att != null ? att.getCaregiverAttendance() : "";
                String chAtt = att != null ? att.getChildAttendance() : "";
                AttendanceRowItem item = new AttendanceRowItem(p, cgAtt, chAtt);
                // Carry the existing signature + GPS forward so present participants who already signed
                // are not re-prompted or re-located on edit (Option A).
                item.caregiverSignature = att != null && att.getCaregiverSignature() != null
                        ? att.getCaregiverSignature() : "";
                item.caregiverGps = att != null && att.getCaregiverGps() != null
                        ? att.getCaregiverGps() : "";

                // Sequential eligibility: if this participant missed an earlier session, hard-block
                // them for this one. Force Absent so the persisted state can never contradict the
                // block, even if a recycled/stale spinner reports a present value.
                item.missedSession = SessionAttendanceParticipantDao
                        .firstMissedSessionBefore(groupId, sessionNumber, p.getParticipantId());
                if (item.isBlocked()) {
                    item.caregiverAttendance = "";
                    item.childAttendance     = "";
                }

                rows.add(item);
            }
            String existingDate = SessionAttendanceDao.getSessionDate(groupId, sessionNumber);
            String defaultDate = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date());
            // Preserve the existing session location when editing participant attendance.
            String loadedSessionGps  = SessionAttendanceDao.getSessionGps(groupId, sessionNumber);
            Threading.main(() -> {
                etDate.setText(existingDate != null && !existingDate.trim().isEmpty() ? existingDate : defaultDate);
                existingSessionGps  = loadedSessionGps != null ? loadedSessionGps : "";
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
        promptCaregiverSignaturesThenSave(date);
    }
    private void promptCaregiverSignaturesThenSave(final String date) {
        List<AttendanceRowItem> rows = adapter.getRows();
        // Seed the session GPS from the stored value so that when every present participant already
        // signed (nothing new to capture), the save keeps the existing session_gps instead of blanking
        // it. A per-participant dialog that does open will overwrite this with a fresh fix.
        capturedGps = existingSessionGps != null ? existingSessionGps : "";
        if (rows == null || rows.isEmpty()) {
            persistAttendance(date, new HashMap<>());
            return;
        }
        promptSignatureForRow(date, rows, 0, new HashMap<>());
    }

    private void promptSignatureForRow(final String date, final List<AttendanceRowItem> rows,
                                       final int index, final Map<String, String> signatures) {
        if (index >= rows.size()) {
            persistAttendance(date, signatures);
            return;
        }

        AttendanceRowItem row = rows.get(index);
        if (row == null || row.participant == null) {
            promptSignatureForRow(date, rows, index + 1, signatures);
            return;
        }

        boolean needsSignature = isPresentAttendance(row.caregiverAttendance);
        if (!needsSignature) {
            // Absent (or not present): no signature required.
            signatures.put(row.participant.getParticipantId(), "");
            promptSignatureForRow(date, rows, index + 1, signatures);
            return;
        }

        if (row.hasSignature()) {
            // Option A: this present participant already signed in a prior save — reuse it and don't
            // re-prompt. Editing another (e.g. previously-absent) participant no longer forces
            // everyone who was Group/Home Visit to sign again.
            signatures.put(row.participant.getParticipantId(), row.caregiverSignature);
            promptSignatureForRow(date, rows, index + 1, signatures);
            return;
        }

        showCaregiverSignatureDialog(row, (signature, gps) -> {
            capturedGps = gps;
            row.caregiverSignature = signature;
            row.caregiverGps = gps;
            signatures.put(row.participant.getParticipantId(), signature);
            promptSignatureForRow(date, rows, index + 1, signatures);
        });
    }

    /** Receives a captured caregiver signature (Base64 PNG) and its GPS "lat,lng". */
    private interface SignatureSink { void accept(String signature, String gps); }

    /**
     * Shows the caregiver signature + GPS dialog for one participant and hands the captured pair to
     * {@code onCaptured} on Save. Both a signature and a GPS fix are mandatory before it dismisses.
     * Shared by the save-flow ({@link #promptSignatureForRow}) and the per-row "tap to re-sign"
     * action so there is a single signature-capture implementation.
     */
    private void showCaregiverSignatureDialog(final AttendanceRowItem row, final SignatureSink onCaptured) {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_caregiver_signature, null);
        final com.github.gcacace.signaturepad.views.SignaturePad pad =
                dialogView.findViewById(R.id.dialog_signature_pad);
        final TextView tvLocation =
                dialogView.findViewById(R.id.dialog_location_text);
        // Local capture buffer so re-signing one row can't leave a half-written session GPS behind
        // if the user cancels; the caller commits capturedGps only on a successful capture.
        final String[] gpsBuffer = { "" };

        final androidx.appcompat.app.AlertDialog dialog =
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Signature for " + row.participant.getCaregiverFullName())
                        .setView(dialogView)
                        .setCancelable(false)
                        .create();

        dialogView.findViewById(R.id.dialog_signature_clear)
                .setOnClickListener(v -> pad.clear());
        dialogView.findViewById(R.id.dialog_signature_cancel)
                .setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.dialog_location_capture)
                .setOnClickListener(v -> captureLocation(tvLocation, gps -> gpsBuffer[0] = gps));
        dialogView.findViewById(R.id.dialog_signature_save)
                .setOnClickListener(v -> {
                    if (pad.isEmpty()) {
                        Toast.makeText(this, "Please sign before saving.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (gpsBuffer[0] == null || gpsBuffer[0].trim().isEmpty()) {
                        Toast.makeText(this, "Capture GPS before saving this signature.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        android.graphics.Bitmap bmp = pad.getSignatureBitmap();
                        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                        bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bos);
                        String signature = android.util.Base64.encodeToString(
                                bos.toByteArray(), android.util.Base64.DEFAULT);
                        dialog.dismiss();
                        onCaptured.accept(signature, gpsBuffer[0]);
                    } catch (Exception e) {
                        Toast.makeText(this, "Could not capture signature. Please retry.", Toast.LENGTH_SHORT).show();
                    }
                });

        dialog.show();
    }


    /**
     * GPS capture for the session attendance — mirrors what the type:gps
     * widget on the household service form does. User-initiated: only fires
     * when the worker taps "Get location" in the signature dialog. Reads
     * last-known fix from GPS first, NETWORK_PROVIDER as fallback. Hands the
     * result to {@code sink} and updates the readout TextView.
     */
    private void captureLocation(final TextView readout, final GpsSink sink) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
                && androidx.core.content.ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pendingLocationCallback = () -> captureLocation(readout, sink);
            androidx.core.app.ActivityCompat.requestPermissions(this,
                    new String[]{
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    REQ_LOCATION);
            return;
        }

        readout.setText("Acquiring location... keep GPS on and wait for a fix.");
        android.location.LocationManager lm =
                (android.location.LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm == null) {
            readout.setText("Location service is unavailable on this device.");
            return;
        }

        final boolean[] delivered = {false};
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        android.location.LocationListener listener = new android.location.LocationListener() {
            @Override public void onLocationChanged(android.location.Location location) {
                if (location == null || delivered[0]) return;
                delivered[0] = true;
                try { lm.removeUpdates(this); } catch (Exception ignored) {}
                publishLocation(location, readout, sink);
            }
            @Override public void onStatusChanged(String provider, int status, android.os.Bundle extras) {}
            @Override public void onProviderEnabled(String provider) {}
            @Override public void onProviderDisabled(String provider) {}
        };

        boolean requested = false;
        try {
            if (lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER, 0L, 0f,
                        listener, android.os.Looper.getMainLooper());
                requested = true;
            }
        } catch (SecurityException | IllegalArgumentException ignored) {}
        try {
            if (lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(android.location.LocationManager.NETWORK_PROVIDER, 0L, 0f,
                        listener, android.os.Looper.getMainLooper());
                requested = true;
            }
        } catch (SecurityException | IllegalArgumentException ignored) {}

        if (!requested) {
            readout.setText("Enable device location/GPS, then tap Get location again.");
            return;
        }

        handler.postDelayed(() -> {
            if (delivered[0]) return;
            delivered[0] = true;
            try { lm.removeUpdates(listener); } catch (Exception ignored) {}
            android.location.Location fallback = bestLastKnownLocation(lm);
            if (fallback != null) {
                publishLocation(fallback, readout, sink);
            } else {
                readout.setText("Still acquiring location. Move near a window/outdoors and tap Get location again.");
            }
        }, 15000L);
    }

    private android.location.Location bestLastKnownLocation(android.location.LocationManager lm) {
        android.location.Location best = null;
        try { best = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER); } catch (Exception ignored) {}
        try {
            android.location.Location network = lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER);
            if (best == null || (network != null && network.getTime() > best.getTime())) best = network;
        } catch (Exception ignored) {}
        return best;
    }

    private void publishLocation(android.location.Location loc, TextView readout, GpsSink sink) {
        sink.accept(String.format(java.util.Locale.US, "%.6f,%.6f", loc.getLatitude(), loc.getLongitude()));
        readout.setText(String.format(java.util.Locale.US,
                "Latitude: %.5f\nLongitude: %.5f\nAccuracy: %dm",
                loc.getLatitude(), loc.getLongitude(), Math.round(loc.getAccuracy())));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
            Runnable cb = pendingLocationCallback;
            pendingLocationCallback = null;
            if (granted && cb != null) cb.run();
            else if (!granted) Toast.makeText(this,
                    "Location permission denied — session will save without GPS.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void persistAttendance(String date) {
        persistAttendance(date, new HashMap<>());
    }

    private void persistAttendance(String date, Map<String, String> signatures) {
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
            saveFormEvent(date, rows, signatures, isEditMode);
            Threading.main(() -> {
                if (saving.isShowing() && !isFinishing() && !isDestroyed()) saving.dismiss();
                Toast.makeText(this, "Attendance saved", Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }

    // ── Client processing ─────────────────────────────────────

    private void saveFormEvent(String date, List<AttendanceRowItem> rows, boolean isEditMode) {
        saveFormEvent(date, rows, new HashMap<>(), isEditMode);
    }

    private void saveFormEvent(String date, List<AttendanceRowItem> rows, Map<String, String> signatures, boolean isEditMode) {
        try {
            // Enforce sequential eligibility at the persistence layer: a participant blocked for this
            // session (missed an earlier one) is always saved Absent, no matter what the row/spinner
            // holds. The disabled UI is the visible half of the hard block; this is the enforcement.
            if (rows != null) {
                for (AttendanceRowItem row : rows) {
                    if (row != null && row.isBlocked()) {
                        row.caregiverAttendance = "";
                        row.childAttendance     = "";
                    }
                }
            }

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
            ChimwemweFormUtils.ensureFieldValue(sessionForm, "caregiver_signature", "");
            // session_gps captured via the dialog's "Get location" button.
            // Empty string is fine — the column accepts NULL/blank.
            ChimwemweFormUtils.ensureFieldValue(sessionForm, "session_gps", capturedGps);

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
                String signature = signatures != null ? signatures.get(pid) : "";
                // Per-participant GPS: only a present participant carries a location; absent/blocked
                // rows are forced empty so a stale fix can't linger after they're marked Absent.
                String gps = isPresentAttendance(row.caregiverAttendance)
                        ? (row.caregiverGps != null ? row.caregiverGps : "") : "";
                ChimwemweFormUtils.ensureFieldValue(lineForm, "caregiver_attendance", row.caregiverAttendance);
                ChimwemweFormUtils.ensureFieldValue(lineForm, "child_attendance", row.childAttendance);
                ChimwemweFormUtils.ensureFieldValue(lineForm, "caregiver_signature", signature);
                ChimwemweFormUtils.ensureFieldValue(lineForm, "caregiver_gps", gps);

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
                SessionAttendanceParticipantDao.updateSignature(groupId, sessionNumber, pid, signature);
                SessionAttendanceParticipantDao.updateGps(groupId, sessionNumber, pid, gps);
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
        // Existing caregiver signature loaded on edit. Reused so a present participant who already
        // signed in a prior save is not asked to sign again (Option A). Empty for new/never-signed.
        String caregiverSignature = "";
        // Per-participant GPS ("lat,lng") captured with the signature. Loaded on edit and reused
        // alongside the signature so present participants are not re-located on every save.
        String caregiverGps = "";
        // Sequential eligibility: >0 means this participant was absent in that earlier session and
        // is hard-blocked for the current session until the earlier absence is corrected. 0 = eligible.
        int missedSession;

        AttendanceRowItem(ParticipantModel p, String cg, String ch) {
            this.participant         = p;
            this.caregiverAttendance = normalizeAttendance(cg);
            this.childAttendance     = normalizeAttendance(ch);
        }

        boolean hasSignature() {
            return caregiverSignature != null && !caregiverSignature.trim().isEmpty();
        }

        boolean isBlocked() { return missedSession > 0; }
    }

    private static boolean isPresentAttendance(String value) {
        return "Group".equalsIgnoreCase(value) || "Home Visit".equalsIgnoreCase(value);
    }

    private static String normalizeAttendance(String value) {
        if ("Group".equalsIgnoreCase(value)) return "Group";
        if ("Home Visit".equalsIgnoreCase(value)) return "Home Visit";
        return "";
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

            // Sequential eligibility gate. Reset both ways every bind — ViewHolders are recycled.
            boolean blocked = row.isBlocked();
            if (blocked) {
                h.tvIneligibleReason.setText("Ineligible: missed Session " + row.missedSession);
                h.tvIneligibleReason.setVisibility(View.VISIBLE);
            } else {
                h.tvIneligibleReason.setVisibility(View.GONE);
            }
            h.spinnerCaregiver.setEnabled(!blocked);
            h.spinnerChild.setEnabled(!blocked);

            // Option B: per-row signature status + "tap to re-sign". Only meaningful for a present,
            // already-signed, eligible participant. Reset every bind (ViewHolders are recycled).
            if (!blocked && isPresentAttendance(row.caregiverAttendance) && row.hasSignature()) {
                h.tvSignatureStatus.setText("✓ Signed · tap to re-sign");
                h.tvSignatureStatus.setVisibility(View.VISIBLE);
                h.tvSignatureStatus.setOnClickListener(v -> showCaregiverSignatureDialog(row, (signature, gps) -> {
                    row.caregiverSignature = signature;
                    row.caregiverGps = gps;
                    // A fresh capture updates the session GPS too, mirroring the save-flow dialog.
                    capturedGps = gps;
                    existingSessionGps = gps;
                    int p = h.getAdapterPosition();
                    if (p != RecyclerView.NO_POSITION) notifyItemChanged(p);
                    Toast.makeText(RecordAttendanceActivity.this,
                            "Signature updated for " + row.participant.getCaregiverFullName(),
                            Toast.LENGTH_SHORT).show();
                }));
            } else {
                h.tvSignatureStatus.setVisibility(View.GONE);
                h.tvSignatureStatus.setOnClickListener(null);
            }

            // Null old listeners before setAdapter/setSelection so recycled ViewHolders
            // don't fire stale callbacks that overwrite row data.
            h.spinnerCaregiver.setOnItemSelectedListener(null);
            h.spinnerChild.setOnItemSelectedListener(null);
            // Any selection change during (re)binding is PROGRAMMATIC and must not be written back.
            // Only a real user tap flips these true. This closes a recycling race where a stray
            // programmatic onItemSelected wrote another participant's "present" value into this row,
            // making an Absent participant get asked to sign.
            h.userTouchedCaregiver = false;
            h.userTouchedChild     = false;

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

            // Restore selections (programmatic — guarded by the touch flags above)
            setSpinnerValue(h.spinnerCaregiver, row.caregiverAttendance);
            setSpinnerValue(h.spinnerChild, row.childAttendance);

            // A real tap marks the spinner user-driven; only then is its selection written back.
            h.spinnerCaregiver.setOnTouchListener((v, e) -> { h.userTouchedCaregiver = true; return false; });
            h.spinnerChild.setOnTouchListener((v, e) -> { h.userTouchedChild = true; return false; });

            h.spinnerCaregiver.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    if (!h.userTouchedCaregiver) return; // ignore programmatic selection during (re)bind
                    int adapterPos = h.getAdapterPosition();
                    if (adapterPos != RecyclerView.NO_POSITION && adapterPos < filteredRows.size())
                        filteredRows.get(adapterPos).caregiverAttendance =
                                position == 0 ? "" : ATTENDANCE_OPTIONS[position];
                }
                @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });

            h.spinnerChild.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    if (!h.userTouchedChild) return; // ignore programmatic selection during (re)bind
                    int adapterPos = h.getAdapterPosition();
                    if (adapterPos != RecyclerView.NO_POSITION && adapterPos < filteredRows.size())
                        filteredRows.get(adapterPos).childAttendance =
                                position == 0 ? "" : ATTENDANCE_OPTIONS[position];
                }
                @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });
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
            TextView tvCaregiverName, tvChildName, tvIneligibleReason, tvSignatureStatus;
            Spinner  spinnerCaregiver, spinnerChild;
            // True only after a real user tap on the respective spinner; gates write-back so
            // programmatic selection during (re)binding never mutates a participant's attendance.
            boolean  userTouchedCaregiver, userTouchedChild;

            VH(View v) {
                super(v);
                tvCaregiverName    = v.findViewById(R.id.tv_caregiver_name);
                tvChildName        = v.findViewById(R.id.tv_child_name);
                tvIneligibleReason = v.findViewById(R.id.tv_ineligible_reason);
                tvSignatureStatus  = v.findViewById(R.id.tv_signature_status);
                spinnerCaregiver   = v.findViewById(R.id.spinner_caregiver);
                spinnerChild       = v.findViewById(R.id.spinner_child);
            }
        }
    }
}
