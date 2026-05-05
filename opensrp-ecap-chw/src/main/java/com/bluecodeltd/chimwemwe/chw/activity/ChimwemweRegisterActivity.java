package com.bluecodeltd.chimwemwe.chw.activity;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import static com.vijay.jsonwizard.utils.FormUtils.fields;
import static com.vijay.jsonwizard.utils.FormUtils.getFieldJSONObject;
import static org.smartregister.util.JsonFormUtils.STEP1;

import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.bluecodeltd.chimwemwe.chw.R;
import com.bluecodeltd.chimwemwe.chw.contract.ChimwemweRegisterContract;
import com.bluecodeltd.chimwemwe.chw.fragment.ChimwemweRegisterFragment;
import com.bluecodeltd.chimwemwe.chw.listener.ChwBottomNavigationListener;
import com.bluecodeltd.chimwemwe.chw.presenter.ChimwemweGroupPresenter;
import com.bluecodeltd.chimwemwe.chw.util.Constants;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vijay.jsonwizard.constants.JsonFormConstants;

import org.json.JSONObject;
import org.smartregister.AllConstants;
import org.smartregister.chw.core.custom_views.NavigationMenu;
import org.smartregister.chw.core.utils.CoreJsonFormUtils;
import org.smartregister.client.utils.domain.Form;
import org.smartregister.family.util.JsonFormUtils;
import org.smartregister.helper.BottomNavigationHelper;
import org.smartregister.opd.pojo.RegisterParams;
import org.smartregister.opd.utils.OpdConstants;
import org.smartregister.opd.utils.OpdJsonFormUtils;
import org.smartregister.opd.utils.OpdUtils;
import org.smartregister.view.activity.BaseRegisterActivity;
import org.smartregister.view.fragment.BaseRegisterFragment;

import java.util.List;
import java.util.Map;
import java.util.Random;

import timber.log.Timber;

public class ChimwemweRegisterActivity extends BaseRegisterActivity
        implements ChimwemweRegisterContract.View {

    private static final int REQUEST_CODE_SELECT_FACILITY = 49013;

    private final ObjectMapper oMapper = new ObjectMapper();
    private String pendingGroupId;
    private String pendingFacilityName;

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
    public void startFormActivity(String formName, String entityId, Map<String, String> map) {
        // Overridden
    }

    @Override
    public void startFormActivity(JSONObject jsonObject) {
        try {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
            CoreJsonFormUtils.populateJsonForm(jsonObject, oMapper.convertValue(sp.getAll(), Map.class));

            int groupId = new Random().nextInt(900000000);
            jsonObject.getJSONObject("step1").getJSONArray("fields").getJSONObject(0)
                    .put("value", Integer.toString(groupId));

            org.smartregister.repository.AllSharedPreferences prefs =
                    com.bluecodeltd.chimwemwe.chw.util.Utils.context().allSharedPreferences();
            String anmUsername = prefs.fetchRegisteredANM();
            String caseworkerName = prefs.getANMPreferredName(anmUsername);
            if (caseworkerName == null || caseworkerName.isEmpty()) caseworkerName = anmUsername;
            org.json.JSONArray step1Fields = jsonObject.getJSONObject("step1").getJSONArray("fields");
            for (int i = 0; i < step1Fields.length(); i++) {
                org.json.JSONObject f = step1Fields.getJSONObject(i);
                String key = f.optString("key");
                if ("facilitator_name_1".equals(key)) {
                    f.put("value", caseworkerName);
                }
                if ("nearest_health_facility".equals(key) && pendingFacilityName != null) {
                    f.put("value", pendingFacilityName);
                }
            }
            pendingFacilityName = null;

            android.content.Intent intent = new android.content.Intent(
                    this, org.smartregister.family.util.Utils.metadata().familyFormActivity);
            Form form = new Form();
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
    public void startFormActivity(String formName, String entityId, String metaData) {
        try {
            String locationId = com.bluecodeltd.chimwemwe.chw.util.Utils.context()
                    .allSharedPreferences()
                    .getPreference(AllConstants.CURRENT_LOCATION_ID);
            groupPresenter().startForm(formName, entityId, metaData, locationId);
        } catch (Exception e) {
            Timber.e(e);
            displayToast(R.string.error_unable_to_start_form);
        }
    }

    @Override
    protected void onActivityResultExtended(int requestCode, int resultCode, android.content.Intent data) {
        if (requestCode == REQUEST_CODE_SELECT_FACILITY) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                pendingFacilityName = data.getStringExtra(ChimwemweFacilitiesActivity.RESULT_FACILITY_NAME);
                startFormActivity("chimwemwe_group_register", null, "");
            }
            return;
        }

        if (requestCode != JsonFormUtils.REQUEST_CODE_GET_JSON
                || resultCode != Activity.RESULT_OK || data == null) return;

        String jsonString = data.getStringExtra(OpdConstants.JSON_FORM_EXTRA.JSON);
        if (jsonString == null) return;

        try {
            JSONObject jsonFormObject = new JSONObject(jsonString);
            if (jsonFormObject.optString(JsonFormConstants.ENCOUNTER_TYPE, "").isEmpty()) return;

            RegisterParams registerParams = new RegisterParams();
            registerParams.setEditMode(false);
            registerParams.setFormTag(OpdJsonFormUtils.formTag(OpdUtils.context().allSharedPreferences()));
            pendingGroupId = getFieldJSONObject(fields(jsonFormObject, STEP1), "group_id")
                    .optString(org.smartregister.family.util.JsonFormUtils.VALUE, "");
            groupPresenter().saveForm(jsonString, registerParams);
        } catch (Exception e) {
            Timber.e(e, "Error processing chimwemwe enrollment form");
            pendingGroupId = null;
            hideProgressDialog();
            Toast.makeText(this, "Error saving enrollment. Please try again.", Toast.LENGTH_SHORT).show();
        }
    }

    private void goToGroupDetail(String groupIdStr) {
        if (groupIdStr == null || groupIdStr.trim().isEmpty()) {
            Timber.w("Missing group_id after enrollment");
            return;
        }
        Toast.makeText(this, "Group enrolled successfully.", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, HotspotGroupDetailActivity.class);
        intent.putExtra(HotspotGroupDetailActivity.EXTRA_GROUP_ID, groupIdStr);
        startActivity(intent);
    }

    @Override
    public void toggleDialogVisibility(boolean showDialog) {
        if (showDialog) {
            showProgressDialog(R.string.saving_dialog_title);
        } else {
            hideProgressDialog();
        }
    }

    @Override
    public void onGroupSaveComplete(String groupName) {
        refreshList(org.smartregister.domain.FetchStatus.fetched);
        String groupIdToOpen = pendingGroupId;
        pendingGroupId = null;
        if (groupIdToOpen != null && !groupIdToOpen.isEmpty()) {
            goToGroupDetail(groupIdToOpen);
        }
    }

    @Override
    public void onGroupSaveError(String errorMessage) {
        pendingGroupId = null;
        hideProgressDialog();
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
    }

    @Override
    public List<String> getViewIdentifiers() {
        return null;
    }

    @Override
    public void startRegistration() {
        Intent intent = new Intent(this, ChimwemweFacilitiesActivity.class);
        intent.putExtra(ChimwemweFacilitiesActivity.EXTRA_SELECT_MODE, true);
        startActivityForResult(intent, REQUEST_CODE_SELECT_FACILITY);
    }

    @Override
    protected void registerBottomNavigation() {
        bottomNavigationHelper = new BottomNavigationHelper();
        bottomNavigationView = findViewById(org.smartregister.R.id.bottom_navigation);
        if (bottomNavigationView != null) {
            com.bluecodeltd.chimwemwe.chw.util.Utils.setupBottomNavigation(
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
