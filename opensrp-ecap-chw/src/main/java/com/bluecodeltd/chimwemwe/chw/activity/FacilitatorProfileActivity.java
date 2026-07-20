package com.bluecodeltd.chimwemwe.chw.activity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.preference.PreferenceManager;

import com.bluecodeltd.chimwemwe.chw.R;
import com.bluecodeltd.chimwemwe.chw.pinlogin.SecurePinLogger;
import com.bluecodeltd.chimwemwe.chw.util.DistrictNameUtils;

public class FacilitatorProfileActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        Toolbar toolbar = findViewById(R.id.collapsing_toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
                actionBar.setDisplayShowTitleEnabled(false);
            }
            toolbar.setNavigationOnClickListener(v -> onBackPressed());
            TextView tvTitle = findViewById(R.id.tvTitle);
            if (tvTitle != null) tvTitle.setText("Facilitator Profile");
        }

        View root = findViewById(android.R.id.content);
        if (root != null) {
            root.setBackgroundColor(getResources().getColor(R.color.chimwemwe_surface, getTheme()));
        }

        CardView headerCard = findViewById(R.id.profile_header_card);
        if (headerCard != null) {
            headerCard.setCardBackgroundColor(getResources().getColor(R.color.chimwemwe_primary, getTheme()));
        }

        TextView txtName = findViewById(R.id.name);
        TextView txtCode = findViewById(R.id.code);
        TextView txtProvince = findViewById(R.id.province);
        TextView txtDistrict = findViewById(R.id.district);
        TextView txtNrc = findViewById(R.id.nrc);
        TextView txtPhone = findViewById(R.id.phone);
        TextView txtEmail = findViewById(R.id.email);
        TextView txtPill = findViewById(R.id.profile_pill);

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        String username = null;
        String displayName = null;
        try {
            SecurePinLogger logger = new SecurePinLogger();
            username = logger.getLoggedInUserName();
            displayName = logger.loggedInUser();
        } catch (Exception ignored) {
        }
        if (username == null || username.trim().isEmpty()) {
            username = sp.getString("caseworker_name", "anonymous");
        }
        if (txtName != null) txtName.setText(displayName != null && !displayName.trim().isEmpty() ? displayName : username);
        if (txtPill != null) txtPill.setText("View Facilitator Profile");
        if (txtCode != null) txtCode.setText(sp.getString("code", "anonymous"));
        if (txtProvince != null) txtProvince.setText(sp.getString("province", "anonymous"));
        if (txtDistrict != null) txtDistrict.setText(DistrictNameUtils.display(sp.getString("district", "anonymous")));
        if (txtNrc != null) txtNrc.setText(sp.getString("nrc", "anonymous"));
        if (txtPhone != null) txtPhone.setText(sp.getString("phone", "anonymous"));
        if (txtEmail != null) txtEmail.setText(sp.getString("email", "anonymous"));
    }
}
