package com.bluecodeltd.chimwemwe.chw.activity;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.bluecodeltd.chimwemwe.chw.R;
import com.bluecodeltd.chimwemwe.chw.dao.HotspotGroupDao;
import com.bluecodeltd.chimwemwe.chw.dao.ParticipantDao;
import com.bluecodeltd.chimwemwe.chw.model.HotspotGroupModel;
import com.bluecodeltd.chimwemwe.chw.util.Threading;
import com.github.mikephil.charting.charts.HorizontalBarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.util.ArrayList;
import java.util.List;

/**
 * Homepage dashboard with two overview charts (issue #49):
 *   1. Child gender proportion (pie) — from ec_chimwemwe_participant.child_sex.
 *   2. Sessions completed by group (horizontal bar, 0–14) — from getAllGroups().
 *
 * NOTE: The issue also asks for the caregiver/parent gender proportion, but parent gender is not
 * captured anywhere in the participant data yet, so that chart is intentionally omitted until a
 * caregiver-gender field is added. Data loads off the UI thread and renders on the main thread.
 */
public class ChimwemweDashboardActivity extends AppCompatActivity {

    // Gender palette: blue = male, pink = female, grey = unspecified.
    private static final int COLOR_MALE        = Color.parseColor("#2563EB");
    private static final int COLOR_FEMALE      = Color.parseColor("#DB2777");
    private static final int COLOR_UNSPECIFIED = Color.parseColor("#9CA3AF");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chimwemwe_dashboard);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        loadData();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void loadData() {
        Threading.io(() -> {
            int[] genderCounts = ParticipantDao.getChildGenderCounts();
            List<HotspotGroupModel> groups = HotspotGroupDao.getAllGroups();
            Threading.main(() -> {
                if (isFinishing() || isDestroyed()) return;
                renderChildGender(genderCounts);
                renderGroupSessions(groups);
            });
        });
    }

    // ── Child gender pie ──────────────────────────────────────

    private void renderChildGender(int[] counts) {
        PieChart chart = findViewById(R.id.chart_child_gender);
        TextView empty = findViewById(R.id.tv_gender_empty);

        int total = (counts == null) ? 0 : counts[0] + counts[1] + counts[2];
        if (total == 0) {
            chart.setVisibility(View.GONE);
            empty.setVisibility(View.VISIBLE);
            return;
        }
        chart.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);

        List<PieEntry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        if (counts[0] > 0) { entries.add(new PieEntry(counts[0], "Male"));        colors.add(COLOR_MALE); }
        if (counts[1] > 0) { entries.add(new PieEntry(counts[1], "Female"));      colors.add(COLOR_FEMALE); }
        if (counts[2] > 0) { entries.add(new PieEntry(counts[2], "Unspecified")); colors.add(COLOR_UNSPECIFIED); }

        PieDataSet set = new PieDataSet(entries, "");
        set.setColors(colors);
        set.setValueTextColor(Color.WHITE);
        set.setValueTextSize(13f);
        set.setValueFormatter(new ValueFormatter() {
            @Override public String getFormattedValue(float value) {
                return String.valueOf((int) value); // whole-count labels, not decimals
            }
        });

        PieData data = new PieData(set);
        chart.setData(data);
        chart.getDescription().setEnabled(false);
        chart.setEntryLabelColor(Color.WHITE);
        chart.setEntryLabelTextSize(12f);
        chart.setUsePercentValues(false);
        chart.setDrawHoleEnabled(true);
        chart.setHoleColor(Color.TRANSPARENT);
        chart.setCenterText("Children\n" + total);
        chart.setCenterTextSize(13f);
        chart.getLegend().setWordWrapEnabled(true);
        chart.animateY(600);
        chart.invalidate();
    }

    // ── Groups vs sessions completed (horizontal bar) ─────────

    private void renderGroupSessions(List<HotspotGroupModel> groups) {
        HorizontalBarChart chart = findViewById(R.id.chart_group_sessions);
        TextView empty = findViewById(R.id.tv_groups_empty);

        if (groups == null || groups.isEmpty()) {
            chart.setVisibility(View.GONE);
            empty.setVisibility(View.VISIBLE);
            return;
        }
        chart.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);

        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        // Bars render bottom-up on a HorizontalBarChart; iterate in reverse so the first group
        // appears at the top.
        for (int i = groups.size() - 1; i >= 0; i--) {
            HotspotGroupModel g = groups.get(i);
            String name = (g.getGroupName() != null && !g.getGroupName().trim().isEmpty())
                    ? g.getGroupName().trim() : "(unnamed)";
            int index = labels.size();
            labels.add(name);
            entries.add(new BarEntry(index, g.getSessionsRecorded()));
        }

        BarDataSet set = new BarDataSet(entries, "Sessions completed");
        set.setColor(androidx.core.content.ContextCompat.getColor(this, R.color.chimwemwe_primary));
        set.setValueTextSize(10f);
        set.setValueFormatter(new ValueFormatter() {
            @Override public String getFormattedValue(float value) {
                return String.valueOf((int) value);
            }
        });

        BarData data = new BarData(set);
        data.setBarWidth(0.6f);
        chart.setData(data);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setFitBars(true);
        chart.setDrawValueAboveBar(true);
        chart.setScaleEnabled(false);

        XAxis x = chart.getXAxis();
        x.setValueFormatter(new IndexAxisValueFormatter(labels));
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setGranularity(1f);
        x.setGranularityEnabled(true);
        x.setDrawGridLines(false);
        x.setLabelCount(labels.size());

        YAxis top = chart.getAxisLeft();
        top.setAxisMinimum(0f);
        top.setAxisMaximum(14f);
        top.setGranularity(1f);
        YAxis bottom = chart.getAxisRight();
        bottom.setAxisMinimum(0f);
        bottom.setAxisMaximum(14f);
        bottom.setDrawLabels(false);

        // Grow the chart so every group's bar is visible; the outer ScrollView handles overflow.
        float density = getResources().getDisplayMetrics().density;
        int perBar = Math.round(46 * density);
        int minH = Math.round(280 * density);
        ViewGroup.LayoutParams lp = chart.getLayoutParams();
        lp.height = Math.max(minH, groups.size() * perBar);
        chart.setLayoutParams(lp);

        chart.animateY(600);
        chart.invalidate();
    }
}
