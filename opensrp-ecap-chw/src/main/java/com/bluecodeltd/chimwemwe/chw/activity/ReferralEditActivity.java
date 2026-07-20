package com.bluecodeltd.chimwemwe.chw.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bluecodeltd.chimwemwe.chw.R;
import com.bluecodeltd.chimwemwe.chw.dao.ChimwemweReferralDao;
import com.bluecodeltd.chimwemwe.chw.dao.ParticipantDao;
import com.bluecodeltd.chimwemwe.chw.model.ChimwemweReferralModel;
import com.bluecodeltd.chimwemwe.chw.model.ParticipantModel;
import com.bluecodeltd.chimwemwe.chw.util.ChimwemweFormUtils;
import com.bluecodeltd.chimwemwe.chw.util.Threading;
import com.bluecodeltd.chimwemwe.chw.util.ReferralFormPrefill;

import org.json.JSONObject;
import org.smartregister.util.FormUtils;


import timber.log.Timber;

public class ReferralEditActivity extends AppCompatActivity {

    public static final String EXTRA_PARTICIPANT_CODE = "participant_code";
    public static final String EXTRA_REFERRAL_BASE_ENTITY_ID = "referral_base_entity_id";

    private static final int REQ_REFERRAL = 3003;

    private String participantCode;
    private String referralBaseEntityId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        participantCode = getIntent().getStringExtra(EXTRA_PARTICIPANT_CODE);
        referralBaseEntityId = getIntent().getStringExtra(EXTRA_REFERRAL_BASE_ENTITY_ID);
        launch();
    }

    private void launch() {
        Threading.io(() -> {
            try {
                FormUtils formUtils = new FormUtils(this);
                JSONObject form = formUtils.getFormJson("chimwemwe_referral");
                if (form == null) {
                    finish();
                    return;
                }

                ParticipantModel participant = ParticipantDao.getParticipantByCode(participantCode);
                if (participant != null) {
                    ChimwemweFormUtils.ensureFieldValue(form, "group_id", participant.getGroupId());
                    ChimwemweFormUtils.ensureFieldValue(form, "participant_id", participant.getParticipantId());
                }
                ChimwemweReferralModel existing = null;
                if (referralBaseEntityId != null && !referralBaseEntityId.trim().isEmpty() && participant != null) {
                    for (ChimwemweReferralModel item : ChimwemweReferralDao.getParticipantReferrals(participant.getParticipantId())) {
                        if (referralBaseEntityId.equals(item.getBase_entity_id())) {
                            existing = item;
                            break;
                        }
                    }
                }

                if (existing != null) {
                    ReferralFormPrefill.populate(form, existing);
                    String baseEntityId = existing.getBase_entity_id();
                    if (baseEntityId != null && !baseEntityId.trim().isEmpty()) {
                        form.put("entity_id", baseEntityId);
                    }
                }

                Threading.main(() -> launchForm(form));
            } catch (Exception e) {
                Timber.e(e, "ReferralEditActivity launch");
                finish();
            }
        });
    }

    private void launchForm(JSONObject form) {
        try {
            Intent intent = new Intent(this, org.smartregister.family.util.Utils.metadata().familyFormActivity);
            com.vijay.jsonwizard.domain.Form cfg = new com.vijay.jsonwizard.domain.Form();
            cfg.setWizard(true);
            cfg.setHideSaveLabel(true);
            cfg.setNextLabel(getString(R.string.next));
            cfg.setPreviousLabel(getString(R.string.previous));
            cfg.setSaveLabel(getString(R.string.submit));
            cfg.setNavigationBackground(R.color.chimwemwe_primary);
            intent.putExtra(com.vijay.jsonwizard.constants.JsonFormConstants.JSON_FORM_KEY.FORM, cfg);
            intent.putExtra(com.vijay.jsonwizard.constants.JsonFormConstants.JSON_FORM_KEY.JSON, form.toString());
            startActivityForResult(intent, REQ_REFERRAL);
        } catch (Exception e) {
            Timber.e(e, "ReferralEditActivity launchForm");
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_REFERRAL || resultCode != Activity.RESULT_OK || data == null) {
            finish();
            return;
        }

        String jsonString = data.getStringExtra(com.vijay.jsonwizard.constants.JsonFormConstants.JSON_FORM_KEY.JSON);
        if (jsonString == null) {
            finish();
            return;
        }

        Threading.io(() -> {
            try {
                JSONObject form = new JSONObject(jsonString);
                boolean isEdit = referralBaseEntityId != null && !referralBaseEntityId.trim().isEmpty();
                ParticipantModel participant = ParticipantDao.getParticipantByCode(participantCode);
                if (participant != null) {
                    ChimwemweFormUtils.ensureFieldValue(form, "group_id", participant.getGroupId());
                    ChimwemweFormUtils.ensureFieldValue(form, "participant_id", participant.getParticipantId());
                }
                String referralId = form.optString("entity_id", "").trim();
                if (referralId.isEmpty()) {
                    referralId = org.smartregister.util.JsonFormUtils.generateRandomUUIDString();
                    form.put("entity_id", referralId);
                }
                ChimwemweFormUtils.ensureFieldValue(form, "referral_id", referralId);
                String selectedServices = com.bluecodeltd.chimwemwe.chw.util.ReferralServiceAggregator.aggregate(form);
                if (!selectedServices.trim().isEmpty() || !isEdit) {
                    ChimwemweFormUtils.ensureFieldValue(form, "service_being_referred", selectedServices);
                }
                boolean saved = ChimwemweFormUtils.saveRegistration(
                        ChimwemweFormUtils.processRegistration(form, "ec_chimwemwe_referral", null),
                        isEdit
                );
                if (saved) {
                    setResult(RESULT_OK);
                }
                Threading.main(() -> {
                    Toast.makeText(
                            this,
                            saved ? "Referral saved successfully" : "Could not save referral. Please try again.",
                            saved ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG
                    ).show();
                    finish();
                });
            } catch (Exception e) {
                Timber.e(e, "ReferralEditActivity onActivityResult");
                Threading.main(() -> {
                    Toast.makeText(this, "Could not save referral. Please try again.", Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }
}
