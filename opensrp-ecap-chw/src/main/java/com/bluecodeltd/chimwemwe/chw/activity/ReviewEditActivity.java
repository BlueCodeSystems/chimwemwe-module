package com.bluecodeltd.chimwemwe.chw.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bluecodeltd.chimwemwe.chw.R;
import com.bluecodeltd.chimwemwe.chw.dao.MonthlyReviewDao;
import com.bluecodeltd.chimwemwe.chw.model.MonthlyReviewModel;
import com.bluecodeltd.chimwemwe.chw.util.ChimwemweFormUtils;
import com.bluecodeltd.chimwemwe.chw.util.Threading;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.json.JSONObject;
import org.smartregister.chw.core.utils.CoreJsonFormUtils;
import org.smartregister.util.FormUtils;

import java.util.Map;

import timber.log.Timber;

public class ReviewEditActivity extends AppCompatActivity {

    public static final String EXTRA_GROUP_ID = "group_id";
    public static final String EXTRA_REVIEW_BASE_ENTITY_ID = "review_base_entity_id";

    private static final int REQ_REVIEW = 3004;

    private String groupId;
    private String reviewBaseEntityId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);
        reviewBaseEntityId = getIntent().getStringExtra(EXTRA_REVIEW_BASE_ENTITY_ID);
        launch();
    }

    private void launch() {
        Threading.io(() -> {
            try {
                FormUtils formUtils = new FormUtils(this);
                JSONObject form = formUtils.getFormJson("chimwemwe_participant_review");
                if (form == null) {
                    finish();
                    return;
                }

                ChimwemweFormUtils.ensureFieldValue(form, "group_id", groupId);

                MonthlyReviewModel existing = null;
                if (reviewBaseEntityId != null && !reviewBaseEntityId.trim().isEmpty() && groupId != null) {
                    for (MonthlyReviewModel item : MonthlyReviewDao.getReviews(groupId)) {
                        if (reviewBaseEntityId.equals(item.getBase_entity_id())) {
                            existing = item;
                            break;
                        }
                    }
                }

                if (existing != null) {
                    try {
                        CoreJsonFormUtils.populateJsonForm(form, new ObjectMapper().convertValue(existing, Map.class));
                    } catch (Exception e) {
                        Timber.w(e, "populateJsonForm review");
                    }
                    String baseEntityId = existing.getBase_entity_id();
                    if (baseEntityId != null && !baseEntityId.trim().isEmpty()) {
                        form.put("entity_id", baseEntityId);
                    }
                }

                Threading.main(() -> launchForm(form));
            } catch (Exception e) {
                Timber.e(e, "ReviewEditActivity launch");
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
            startActivityForResult(intent, REQ_REVIEW);
        } catch (Exception e) {
            Timber.e(e, "ReviewEditActivity launchForm");
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_REVIEW || resultCode != Activity.RESULT_OK || data == null) {
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
                boolean isEdit = !form.optString("entity_id", "").isEmpty();
                ChimwemweFormUtils.ensureFieldValue(form, "group_id", groupId);
                ChimwemweFormUtils.saveRegistration(
                        ChimwemweFormUtils.processRegistration(form, "ec_chimwemwe_review", null),
                        isEdit
                );
                setResult(RESULT_OK);
                Threading.main(() -> {
                    Toast.makeText(this, "Review saved successfully", Toast.LENGTH_SHORT).show();
                    finish();
                });
            } catch (Exception e) {
                Timber.e(e, "ReviewEditActivity onActivityResult");
                Threading.main(this::finish);
            }
        });
    }
}
