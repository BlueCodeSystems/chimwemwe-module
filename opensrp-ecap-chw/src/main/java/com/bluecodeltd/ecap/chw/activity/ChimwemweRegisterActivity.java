package com.bluecodeltd.ecap.chw.activity;

import static org.smartregister.util.JsonFormUtils.STEP1;
import static org.smartregister.family.util.JsonFormUtils.STEP2;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.bluecodeltd.ecap.chw.R;
import com.bluecodeltd.ecap.chw.contract.ChimwemweRegisterContract;
import com.bluecodeltd.ecap.chw.fragment.ChimwemweRegisterFragment;
import com.bluecodeltd.ecap.chw.listener.ChwBottomNavigationListener;
import com.bluecodeltd.ecap.chw.model.HotspotGroupModel;
import com.bluecodeltd.ecap.chw.presenter.ChimwemweGroupPresenter;
import com.bluecodeltd.ecap.chw.util.Constants;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vijay.jsonwizard.constants.JsonFormConstants;

import org.json.JSONArray;
import org.json.JSONObject;
import org.smartregister.chw.core.custom_views.NavigationMenu;
import org.smartregister.chw.core.utils.CoreJsonFormUtils;
import org.smartregister.domain.FetchStatus;
import org.smartregister.family.util.JsonFormUtils;
import org.smartregister.helper.BottomNavigationHelper;
import org.smartregister.opd.utils.OpdConstants;
import org.smartregister.view.activity.BaseRegisterActivity;
import org.smartregister.view.fragment.BaseRegisterFragment;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Random;

import timber.log.Timber;

public class ChimwemweRegisterActivity extends BaseRegisterActivity
        implements ChimwemweRegisterContract.View {

    private final ObjectMapper oMapper = new ObjectMapper();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Toolbar toolbar = findViewById(org.smartregister.R.id.register_toolbar);
        NavigationMenu menu;
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayShowTitleEnabled(false);
            }
            toolbar.setTitle("");
            TextView titleLabel = toolbar.findViewById(org.smartregister.R.id.txt_title_label);
            if (titleLabel != null) titleLabel.setVisibility(View.GONE);
            menu = NavigationMenu.getInstance(this, null, toolbar);
            try {
                if (menu != null) {
                    androidx.drawerlayout.widget.DrawerLayout drawer = menu.getDrawer();
                    androidx.appcompat.graphics.drawable.DrawerArrowDrawable arrow =
                            new androidx.appcompat.graphics.drawable.DrawerArrowDrawable(this);
                    arrow.setColor(android.graphics.Color.WHITE);
                    toolbar.setNavigationIcon(arrow);
                    toolbar.setNavigationOnClickListener(v -> {
                        if (drawer != null) drawer.openDrawer(androidx.core.view.GravityCompat.START);
                    });
                }
            } catch (Throwable ignored) {}
        } else {
            menu = NavigationMenu.getInstance(this, null, null);
        }

        if (menu != null && menu.getNavigationAdapter() != null) {
            menu.getNavigationAdapter().setSelectedView(Constants.DrawerMenu.CHIMWEMWE);
        }
    }

    @Override
    protected void initializePresenter() {
        this.presenter = new ChimwemweGroupPresenter(this);
    }

    private ChimwemweGroupPresenter groupPresenter() {
        return (ChimwemweGroupPresenter) this.presenter;
    }

    @Override
    protected BaseRegisterFragment getRegisterFragment() {
        return new ChimwemweRegisterFragment();
    }

    @Override
    protected Fragment[] getOtherFragments() {
        return new Fragment[0];
    }

    @Override
    public void startFormActivity(String formName, String entityId, Map<String, String> map) {}

    @Override
    public void startFormActivity(JSONObject jsonObject) {
        try {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
            CoreJsonFormUtils.populateJsonForm(jsonObject, oMapper.convertValue(sp.getAll(), Map.class));

            android.content.Intent intent = new android.content.Intent(
                    this, org.smartregister.family.util.Utils.metadata().familyFormActivity);
            com.vijay.jsonwizard.domain.Form form = new com.vijay.jsonwizard.domain.Form();
            form.setWizard(true);
            form.setHideSaveLabel(true);
            form.setNextLabel(getString(R.string.next));
            form.setPreviousLabel(getString(R.string.previous));
            form.setSaveLabel(getString(R.string.submit));
            form.setNavigationBackground(R.color.chimwemwe_primary);
            intent.putExtra(JsonFormConstants.JSON_FORM_KEY.FORM, form);
            intent.putExtra(JsonFormConstants.JSON_FORM_KEY.JSON, jsonObject.toString());
            startActivityForResult(intent, JsonFormUtils.REQUEST_CODE_GET_JSON);
        } catch (Exception e) {
            Timber.e(e, "Error launching chimwemwe enrollment form");
        }
    }

    @Override
    public void startFormActivity(String formName, String entityId, String metaData) {}

    @Override
    protected void onActivityResultExtended(int requestCode, int resultCode, android.content.Intent data) {
        if (requestCode != JsonFormUtils.REQUEST_CODE_GET_JSON
                || resultCode != Activity.RESULT_OK || data == null) return;

        String jsonString = data.getStringExtra(OpdConstants.JSON_FORM_EXTRA.JSON);
        if (jsonString == null) return;

        try {
            JSONObject form = new JSONObject(jsonString);
            HotspotGroupModel group = buildGroupModel(form);
            if (group == null) {
                Toast.makeText(this, "Group name and hotspot name are required.", Toast.LENGTH_SHORT).show();
                return;
            }
            groupPresenter().saveForm(form, group);
        } catch (Exception e) {
            Timber.e(e, "Error processing chimwemwe enrollment form");
            Toast.makeText(this, "Error saving enrollment. Please try again.", Toast.LENGTH_SHORT).show();
        }
    }

    // ── ChimwemweRegisterContract.View ───────────────────────

    @Override
    public void toggleDialogVisibility(boolean showDialog) {
        // Group saves are fast local operations; no blocking progress dialog needed
    }

    @Override
    public void onGroupSaveComplete(String groupName) {
        refreshList(FetchStatus.fetched);
        Toast.makeText(this,
                "Group \"" + groupName + "\" enrolled. Add participants inside the group.",
                Toast.LENGTH_LONG).show();
    }

    @Override
    public void onGroupSaveError(String errorMessage) {
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
    }

    // ── Form parsing ─────────────────────────────────────────

    private HotspotGroupModel buildGroupModel(JSONObject form) {
        JSONObject step1 = form.optJSONObject(STEP1);
        JSONObject step2 = form.optJSONObject(STEP2);
        JSONObject step3 = form.optJSONObject("step3");

        String hotspotName = fieldValue(step1, "hotspot_name");
        String groupName   = fieldValue(step1, "group_name");
        if (hotspotName.isEmpty() || groupName.isEmpty()) return null;

        HotspotGroupModel group = new HotspotGroupModel();

        group.setGroupCode(generateGroupCode());
        group.setHotspotName(hotspotName);
        group.setGroupName(groupName);
        group.setProvince(fieldValue(step1, "province"));
        group.setDistrict(fieldValue(step1, "district"));
        group.setLocationOfSession(fieldValue(step1, "location_of_session"));
        group.setLocationGps(fieldValue(step1, "location_gps"));
        group.setNearestHealthFacility(fieldValue(step1, "nearest_health_facility"));
        group.setCreatedDate(LocalDate.now().toString());

        group.setFacilitator1FirstName(fieldValue(step2, "facilitator_1_first_name"));
        group.setFacilitator1Surname(fieldValue(step2,   "facilitator_1_surname"));
        group.setFacilitator2FirstName(fieldValue(step2, "facilitator_2_first_name"));
        group.setFacilitator2Surname(fieldValue(step2,   "facilitator_2_surname"));

        group.setSession1Date(fieldValue(step3,  "session_1_date"));
        group.setSession2Date(fieldValue(step3,  "session_2_date"));
        group.setSession3Date(fieldValue(step3,  "session_3_date"));
        group.setSession4Date(fieldValue(step3,  "session_4_date"));
        group.setSession5Date(fieldValue(step3,  "session_5_date"));
        group.setSession6Date(fieldValue(step3,  "session_6_date"));
        group.setSession7Date(fieldValue(step3,  "session_7_date"));
        group.setSession8Date(fieldValue(step3,  "session_8_date"));
        group.setSession9Date(fieldValue(step3,  "session_9_date"));
        group.setSession10Date(fieldValue(step3, "session_10_date"));
        group.setSession11Date(fieldValue(step3, "session_11_date"));
        group.setSession12Date(fieldValue(step3, "session_12_date"));
        group.setSession13Date(fieldValue(step3, "session_13_date"));
        group.setSession14Date(fieldValue(step3, "session_14_date"));

        return group;
    }

    private static String generateGroupCode() {
        return String.format("CHM%07d", new Random().nextInt(10_000_000));
    }

    private String fieldValue(JSONObject step, String key) {
        if (step == null) return "";
        try {
            JSONArray stepFields = step.optJSONArray("fields");
            if (stepFields == null) return "";
            for (int i = 0; i < stepFields.length(); i++) {
                JSONObject field = stepFields.getJSONObject(i);
                if (key.equals(field.optString("key"))) {
                    String v = field.optString("value", "").trim();
                    return v.equals("null") ? "" : v;
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    @Override
    public List<String> getViewIdentifiers() {
        return null;
    }

    @Override
    public void startRegistration() {}

    @Override
    protected void registerBottomNavigation() {
        bottomNavigationHelper = new BottomNavigationHelper();
        bottomNavigationView = findViewById(org.smartregister.R.id.bottom_navigation);
        if (bottomNavigationView != null) {
            com.bluecodeltd.ecap.chw.util.Utils.setupBottomNavigation(
                    bottomNavigationHelper, bottomNavigationView,
                    new ChwBottomNavigationListener(this));
            bottomNavigationView.getMenu().removeItem(R.id.action_register);
            bottomNavigationView.getMenu().removeItem(R.id.action_register_index);
            bottomNavigationView.getMenu().removeItem(R.id.action_fsw);
            bottomNavigationView.getMenu().removeItem(R.id.action_hts);

            MenuItem enrollItem = bottomNavigationView.getMenu().findItem(R.id.action_identifcation);
            if (enrollItem != null) enrollItem.setTitle("Add Group");
        }
    }
}
