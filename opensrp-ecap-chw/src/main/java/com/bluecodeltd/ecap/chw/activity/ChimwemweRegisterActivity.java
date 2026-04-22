package com.bluecodeltd.ecap.chw.activity;

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
import com.bluecodeltd.ecap.chw.presenter.ChimwemweGroupPresenter;
import com.bluecodeltd.ecap.chw.util.Constants;
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
    public void startFormActivity(String formName, String entityId, Map<String, String> map) {
        // Overridden
    }

    @Override
    public void startFormActivity(JSONObject jsonObject) {
        try {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
            CoreJsonFormUtils.populateJsonForm(jsonObject, oMapper.convertValue(sp.getAll(), Map.class));

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
            String locationId = com.bluecodeltd.ecap.chw.util.Utils.context()
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
            showProgressDialog(R.string.saving_dialog_title);
            groupPresenter().saveForm(jsonString, registerParams);
        } catch (Exception e) {
            Timber.e(e, "Error processing chimwemwe enrollment form");
            Toast.makeText(this, "Error saving enrollment. Please try again.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void toggleDialogVisibility(boolean showDialog) {
        if (showDialog) {
            showProgressDialog(R.string.saving_index);
        } else {
            hideProgressDialog();
        }
    }

    @Override
    public void onGroupSaveComplete(String groupName) {
        Toast.makeText(this,
                "Group \"" + groupName + "\" enrolled. Add participants inside the group.",
                Toast.LENGTH_LONG).show();
    }

    @Override
    public void onGroupSaveError(String errorMessage) {
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
    }

    @Override
    public List<String> getViewIdentifiers() {
        return null;
    }

    @Override
    public void startRegistration() {
        startFormActivity("chimwemwe_enrollment", null, "");
    }

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
