package com.bluecodeltd.chimwemwe.chw.activity;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.bluecodeltd.chimwemwe.chw.BuildConfig;
import com.bluecodeltd.chimwemwe.chw.R;
import com.bluecodeltd.chimwemwe.chw.dao.ParticipantDao;
import com.bluecodeltd.chimwemwe.chw.model.ParticipantModel;
import com.bluecodeltd.chimwemwe.chw.util.Threading;

import org.smartregister.location.helper.LocationHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class AddParticipantActivity extends AppCompatActivity {

    public static final String EXTRA_GROUP_ID      = "group_id";
    public static final String EXTRA_PARTICIPANT_ID = "participant_id";
    public static final String EXTRA_SN             = "sn";

    private String groupId;
    private long participantId = -1;
    private int  sn;

    private EditText etCaregiverFirst, etCaregiverSurname, etCaregiverId;
    private EditText etChildFirst, etChildSurname, etChildDob, etVcaId;
    private Spinner  spinnerSex, spinnerOvc;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_participant);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        groupId       = getIntent().getStringExtra(EXTRA_GROUP_ID);
        participantId = getIntent().getLongExtra(EXTRA_PARTICIPANT_ID, -1);
        sn            = getIntent().getIntExtra(EXTRA_SN, 1);

        etCaregiverFirst   = findViewById(R.id.et_caregiver_first_name);
        etCaregiverSurname = findViewById(R.id.et_caregiver_surname);
        etCaregiverId      = findViewById(R.id.et_caregiver_id);
        etChildFirst       = findViewById(R.id.et_child_first_name);
        etChildSurname     = findViewById(R.id.et_child_surname);
        etChildDob         = findViewById(R.id.et_child_dob);
        etVcaId            = findViewById(R.id.et_vca_id);
        spinnerSex         = findViewById(R.id.spinner_child_sex);
        spinnerOvc         = findViewById(R.id.spinner_enrolled_ovc);

        ArrayAdapter<String> sexAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"-- Select --", "F", "M"});
        sexAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSex.setAdapter(sexAdapter);

        ArrayAdapter<String> ovcAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"-- Select --", "Yes", "No"});
        ovcAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerOvc.setAdapter(ovcAdapter);

        if (participantId != -1) {
            if (getSupportActionBar() != null) getSupportActionBar().setTitle("Edit Participant");
            loadExisting();
        } else {
            etVcaId.setText(generateVcaId());
            etCaregiverId.setText(generateCaregiverId());
        }

        Button btnSave = findViewById(R.id.btn_save_participant);
        btnSave.setOnClickListener(v -> save());
    }

    private void loadExisting() {
        Threading.io(() -> {
            ParticipantModel m = ParticipantDao.getParticipant(participantId);
            if (m == null) return;
            Threading.main(() -> {
                etCaregiverFirst.setText(m.getCaregiverFirstName());
                etCaregiverSurname.setText(m.getCaregiverSurname());
                etCaregiverId.setText(m.getCaregiverId());
                etChildFirst.setText(m.getChildFirstName());
                etChildSurname.setText(m.getChildSurname());
                etChildDob.setText(m.getChildDob());
                etVcaId.setText(m.getVcaId());
                setSpinner(spinnerSex, m.getChildSex());
                setSpinner(spinnerOvc, m.getIsEnrolledOvc());
            });
        });
    }

    private String generateVcaId() {
        int num = 1000000 + new Random().nextInt(9000000);
        return String.valueOf(num);
    }

    private String generateCaregiverId() {
        String prefix = "";
        try {
            List<String> hierarchy = LocationHelper.getInstance()
                    .generateDefaultLocationHierarchy(
                            new ArrayList<>(Arrays.asList(BuildConfig.ALLOWED_LOCATION_LEVELS)));
            if (hierarchy != null && hierarchy.size() > 2 && hierarchy.get(2) != null) {
                String district = hierarchy.get(2).trim().replaceAll("\\s+", "");
                prefix = district.substring(0, Math.min(3, district.length())).toUpperCase();
            }
        } catch (Exception ignored) {}
        int num = 100000 + new Random().nextInt(900000);
        return prefix + num;
    }

    private void setSpinner(Spinner spinner, String value) {
        if (value == null) return;
        ArrayAdapter adapter = (ArrayAdapter) spinner.getAdapter();
        for (int i = 0; i < adapter.getCount(); i++) {
            if (value.equals(adapter.getItem(i))) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    private void save() {
        String cgFirst = etCaregiverFirst.getText().toString().trim();
        String cgSur   = etCaregiverSurname.getText().toString().trim();
        String chFirst = etChildFirst.getText().toString().trim();
        String chSur   = etChildSurname.getText().toString().trim();

        if (cgFirst.isEmpty() && cgSur.isEmpty() && chFirst.isEmpty() && chSur.isEmpty()) {
            Toast.makeText(this, "Please enter at least one name", Toast.LENGTH_SHORT).show();
            return;
        }

        ParticipantModel m = new ParticipantModel();
        m.setId(participantId);
        m.setGroupId(groupId);
        m.setSn(sn);
        m.setCaregiverFirstName(cgFirst.isEmpty() ? null : cgFirst);
        m.setCaregiverSurname(cgSur.isEmpty() ? null : cgSur);
        m.setChildFirstName(chFirst.isEmpty() ? null : chFirst);
        m.setChildSurname(chSur.isEmpty() ? null : chSur);
        m.setChildDob(etChildDob.getText().toString().trim());
        m.setCaregiverId(etCaregiverId.getText().toString().trim());
        m.setVcaId(etVcaId.getText().toString().trim());

        String sex = spinnerSex.getSelectedItem().toString();
        m.setChildSex(sex.startsWith("--") ? null : sex);
        String ovc = spinnerOvc.getSelectedItem().toString();
        m.setIsEnrolledOvc(ovc.startsWith("--") ? null : ovc);

        Threading.io(() -> {
            if (participantId == -1) {
                ParticipantDao.insertParticipant(m);
            } else {
                ParticipantDao.updateParticipant(m);
            }
            Threading.main(() -> {
                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }
}
