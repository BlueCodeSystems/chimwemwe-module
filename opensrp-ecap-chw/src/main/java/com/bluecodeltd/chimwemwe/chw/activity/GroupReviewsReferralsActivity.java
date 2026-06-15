package com.bluecodeltd.chimwemwe.chw.activity;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.bluecodeltd.chimwemwe.chw.R;
import com.bluecodeltd.chimwemwe.chw.adapter.ViewPager2Adapter;
import com.bluecodeltd.chimwemwe.chw.fragment.GroupReferralsFragment;
import com.bluecodeltd.chimwemwe.chw.fragment.GroupReviewsFragment;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.Arrays;
import java.util.List;

public class GroupReviewsReferralsActivity extends AppCompatActivity {

    public static final String EXTRA_GROUP_ID = "group_id";

    private String groupId;
    private TabLayoutMediator tabMediator;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_reviews_referrals);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Reviews and Referrals");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);

        TabLayout tabs = findViewById(R.id.tabs);
        ViewPager2 pager = findViewById(R.id.viewpager);
        List<Fragment> fragments = Arrays.asList(
                GroupReviewsFragment.newInstance(groupId),
                GroupReferralsFragment.newInstance(groupId)
        );
        pager.setAdapter(new ViewPager2Adapter(this, fragments));
        tabMediator = new TabLayoutMediator(tabs, pager, (tab, position) -> {
            tab.setText(position == 0 ? "Reviews" : "Referrals");
        });
        tabMediator.attach();
    }

    @Override
    protected void onDestroy() {
        if (tabMediator != null) tabMediator.detach();
        super.onDestroy();
    }
}
