package com.bluecodeltd.chimwemwe.chw.activity;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.bluecodeltd.chimwemwe.chw.BuildConfig;
import com.bluecodeltd.chimwemwe.chw.R;
import com.bluecodeltd.chimwemwe.chw.actionhelper.CSVGeneratorHelper;
import com.bluecodeltd.chimwemwe.chw.application.ChwApplication;
import com.bluecodeltd.chimwemwe.chw.contract.GenerateCSVContract;
import com.bluecodeltd.chimwemwe.chw.presenter.GenerateCSVPresenter;
import com.bluecodeltd.chimwemwe.chw.util.CsvFormImportService;
import com.bluecodeltd.chimwemwe.chw.util.Threading;
import com.bluecodeltd.chimwemwe.chw.util.UpdateManager;
import com.bluecodeltd.chimwemwe.chw.viewmodel.DashboardViewModel;
import com.github.javiersantos.appupdater.AppUpdater;

import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.chw.core.custom_views.NavigationMenu;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

public class DashboardActivity extends AppCompatActivity implements GenerateCSVContract.View {

    private com.bluecodeltd.chimwemwe.chw.databinding.ActivityDashboardBinding binding;
    private GenerateCSVContract.Presenter presenter;
    private CSVGeneratorHelper csvGenerator;
    private Toolbar toolbar;
    private AppUpdater appUpdater;
    private DashboardViewModel dashboardViewModel;

    private final Handler handler = new Handler();
    private Runnable runnable;
    private String phone = "";
    private static final int FORTY_FIVE_MINUTES = 2_700_000;
    private static final int REQUEST_CODE_IMPORT_CSV = 49011;

    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = com.bluecodeltd.chimwemwe.chw.databinding.ActivityDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        toolbar = binding.toolbarx;
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        toolbar.getOverflowIcon().setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP);

        binding.btnSync.setOnClickListener(v -> loadData());

        NavigationMenu.getInstance(this, null, toolbar);

        presenter = new GenerateCSVPresenter(this);
        csvGenerator = new CSVGeneratorHelper();
        appUpdater = new AppUpdater(this);
        UpdateManager.startOnce(this);

        // Chimwemwe Groups register row → navigate to register
        if (binding.registerRowGroups != null) {
            binding.registerRowGroups.setOnClickListener(v ->
                    startActivity(new Intent(this, ChimwemweRegisterActivity.class)));
        }

        // Shared prefs
        Bundle extras = getIntent().getExtras();
        String username = extras != null ? extras.getString("username") : null;
        String password = extras != null ? extras.getString("password") : null;

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        String facility = sp.getString("facility", "");
        phone = sp.getString("phone", "");

        if (binding.dashFacilityName != null) {
            binding.dashFacilityName.setText(facility);
        }

        // Token refresh on first launch
        if (username != null && password != null) {
            String code = sp.getString("code", "0000");
            if (!sp.contains("code") || code.equals("0000")) {
                getToken(username, password);
            }
        }

        // Observe Chimwemwe dashboard state
        dashboardViewModel = new ViewModelProvider(this).get(DashboardViewModel.class);
        dashboardViewModel.getState().observe(this, state -> {
            if (binding.dashProgressbar != null) {
                binding.dashProgressbar.setVisibility(android.view.View.GONE);
            }
            if (state == null) return;
            try {
                binding.statGroups.setText(String.valueOf(state.getGroupsCount()));
                binding.statParticipants.setText(String.valueOf(state.getParticipantsCount()));
                String sessionText = state.getSessionsRecorded() + " / " + state.getMaxSessions();
                binding.statSessions.setText(sessionText);
                binding.statCompleted.setText(String.valueOf(state.getCompletedCount()));
                if (state.getLastUpdated() != null) {
                    binding.lastUpdated.setText(dtf.format(state.getLastUpdated()));
                }
            } catch (Exception e) {
                Timber.e(e, "DashboardActivity: state update failed");
            }
        });

        loadData();
        refreshData();
    }

    private void loadData() {
        if (binding.dashProgressbar != null) {
            binding.dashProgressbar.setVisibility(android.view.View.VISIBLE);
        }
        dashboardViewModel.refresh();
    }

    private void refreshData() {
        handler.postDelayed(runnable = new Runnable() {
            public void run() {
                handler.postDelayed(runnable, FORTY_FIVE_MINUTES);
                loadData();
            }
        }, FORTY_FIVE_MINUTES);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(runnable);
    }

    // ── Options menu ──────────────────────────────────────────────

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.dash_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.generate_pdf) {
            csvGenerator.generateCSVWithProgress(this, presenter, () ->
                    showCustomDialog(this,
                            getString(R.string.csv_generated_location, getString(R.string.app_name))));
        } else if (id == R.id.import_csv) {
            openCsvPicker();
        } else if (id == R.id.add_facility) {
            seedAllFacilitiesToFirebase();
        }
        return super.onOptionsItemSelected(item);
    }

    // ── CSV contract ──────────────────────────────────────────────

    @Override
    public void showCSVGeneratedMessage(String filePath) {}

    @Override
    public void showError(String errorMessage) {}

    // ── Keycloak token / credential refresh ───────────────────────

    private void getToken(final String username, final String password) {
        String url = "https://keycloak.zeir.smartregister.org/auth/realms/ecap-stage/protocol/openid-connect/token";
        StringRequest req = new StringRequest(Request.Method.POST, url,
                response -> {
                    try {
                        JSONObject obj = new JSONObject(response.trim());
                        String token = obj.getString("access_token");
                        getCreds(token);
                        loadData();
                    } catch (JSONException e) {
                        Timber.e(e, "getToken parse error");
                    }
                },
                error -> Timber.e("getToken error: %s", error.getMessage())) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("grant_type", "password");
                params.put("username", username);
                params.put("password", password);
                params.put("scope", "openid");
                params.put("client_id", BuildConfig.OAUTH_CLIENT_ID);
                params.put("client_secret", BuildConfig.OAUTH_CLIENT_SECRET);
                return params;
            }
        };
        ChwApplication.getApplicationFlavor().chwAppInstance().addToRequestQueue(req, "req_login");
    }

    private void getCreds(String token) {
        String url = "https://keycloak.zeir.smartregister.org/auth/realms/ecap-stage/protocol/openid-connect/userinfo";
        StringRequest req = new StringRequest(Request.Method.GET, url,
                response -> {
                    try {
                        JSONObject obj = new JSONObject(response);
                        SharedPreferences.Editor edit = PreferenceManager
                                .getDefaultSharedPreferences(this).edit();
                        edit.putString("sub",            obj.optString("sub"));
                        edit.putString("code",           obj.optString("code"));
                        edit.putString("caseworker_name",obj.optString("name"));
                        edit.putString("given_name",     obj.optString("given_name"));
                        edit.putString("family_name",    obj.optString("family_name"));
                        edit.putString("province",       obj.optString("province"));
                        edit.putString("partner",        obj.optString("partner"));
                        edit.putString("phone",          obj.optString("phone"));
                        edit.putString("district",       obj.optString("district"));
                        edit.putString("facility",       obj.optString("facility"));
                        edit.putString("email",          obj.optString("email"));
                        edit.putString("nrc",            obj.optString("nrc"));
                        edit.apply();
                        recreate();
                    } catch (JSONException e) {
                        Timber.e(e, "getCreds parse error");
                    }
                },
                error -> Timber.e("getCreds error: %s", error.getMessage())) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };
        ChwApplication.getApplicationFlavor().chwAppInstance().addToRequestQueue(req, "req_creds");
    }

    // ── CSV import ────────────────────────────────────────────────

    private void openCsvPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"text/csv", "text/comma-separated-values", "application/vnd.ms-excel"});
        startActivityForResult(intent, REQUEST_CODE_IMPORT_CSV);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CODE_IMPORT_CSV || resultCode != RESULT_OK || data == null) return;

        List<Uri> csvUris = extractCsvUris(data);
        if (csvUris.isEmpty()) {
            showCustomDialog(this, "No CSV file selected.");
            return;
        }

        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setIndeterminate(false);
        progressDialog.setMax(100);
        progressDialog.setProgress(0);
        progressDialog.setMessage("Preparing import...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        Threading.io(() -> {
            final int[] lastPercent = {-1};
            Map<Uri, Integer> perFileRows = new LinkedHashMap<>();
            int totalRowsAllFiles = 0;
            for (Uri uri : csvUris) {
                int rows = CsvFormImportService.getDataRowCount(this, uri);
                int normalized = Math.max(1, rows);
                perFileRows.put(uri, normalized);
                totalRowsAllFiles += normalized;
            }
            final int overallTotal = totalRowsAllFiles;

            int importedAll = 0, skippedAll = 0, failedAll = 0, processedAll = 0;
            List<String> summary = new ArrayList<>();

            for (int fi = 0; fi < csvUris.size(); fi++) {
                Uri uri = csvUris.get(fi);
                int fileIdx = fi + 1;
                int totalFiles = csvUris.size();
                int rowsForFile = perFileRows.get(uri);
                int processedBefore = processedAll;

                CsvFormImportService.ImportSummary s = CsvFormImportService.importFromCsvUri(
                        this, uri,
                        (proc, tot, imp, skip, fail) -> {
                            int cur = tot > 0 ? tot : rowsForFile;
                            int globalProc = Math.min(overallTotal, processedBefore + Math.min(proc, cur));
                            int pct = overallTotal > 0 ? Math.min(95, (globalProc * 95) / overallTotal) : 95;
                            if (pct == lastPercent[0]) return;
                            lastPercent[0] = pct;
                            Threading.main(() -> {
                                progressDialog.setProgress(pct);
                                progressDialog.setMessage("Importing file " + fileIdx + "/" + totalFiles
                                        + ": " + pct + "%\nRows " + globalProc + "/" + overallTotal
                                        + "\nImported " + imp + ", Skipped " + skip + ", Failed " + fail);
                            });
                        });

                importedAll += s.importedRows;
                skippedAll  += s.skippedRows;
                failedAll   += s.failedRows;
                processedAll += rowsForFile;

                String label = s.fileName != null ? s.fileName : ("File " + fileIdx);
                String status = s.timedOutDuringProcessing ? "TIMEOUT" : (s.hasFileFailure() ? "FAILED" : "OK");
                summary.add(label + " [" + status + "]: Imported " + s.importedRows
                        + ", Skipped " + s.skippedRows + ", Failed " + s.failedRows);
            }

            StringBuilder msg = new StringBuilder("CSV import finished for ")
                    .append(csvUris.size()).append(" file(s).")
                    .append("\nImported: ").append(importedAll)
                    .append(", Skipped: ").append(skippedAll)
                    .append(", Failed: ").append(failedAll);
            if (!summary.isEmpty()) {
                msg.append("\n\nPer-file summary:");
                for (String line : summary) msg.append("\n- ").append(line);
            }
            String finalMsg = msg.toString();

            Threading.main(() -> {
                progressDialog.setProgress(100);
                progressDialog.setMessage("Finalizing: 100%");
                progressDialog.dismiss();
                showCustomDialog(this, finalMsg, this::loadData);
            });
        });
    }

    private List<Uri> extractCsvUris(Intent data) {
        List<Uri> uris = new ArrayList<>();
        if (data.getData() != null) uris.add(data.getData());
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                Uri u = clip.getItemAt(i).getUri();
                if (u != null && !uris.contains(u)) uris.add(u);
            }
        }
        return uris;
    }

    // ── Add Facility (seed all 655 Zambian facilities) ────────────

    private void seedAllFacilitiesToFirebase() {
        Threading.io(() -> {
            try {
                InputStream is = getResources().openRawResource(R.raw.zambia_facilities);
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                JSONObject payload = new JSONObject();
                java.util.Map<String, Integer> seen = new java.util.HashMap<>();
                String line;
                int count = 0;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split("\t", -1);
                    if (parts.length < 3) continue;
                    String name = parts[0].trim();
                    String province = parts[1].trim();
                    String district = parts[2].trim();
                    if (name.isEmpty()) continue;

                    String baseKey = facilitySlugify(name + "_" + district);
                    String key = baseKey;
                    if (seen.containsKey(baseKey)) {
                        int n = seen.get(baseKey);
                        key = baseKey + "_" + n;
                        seen.put(baseKey, n + 1);
                    } else {
                        seen.put(baseKey, 1);
                    }
                    JSONObject row = new JSONObject();
                    row.put("facility_name", name);
                    row.put("province", province);
                    row.put("district", district);
                    payload.put(key, row);
                    count++;
                }
                reader.close();

                int finalCount = count;
                String jsonBody = payload.toString();
                Threading.main(() -> pushAllFacilitiesToFirebase(jsonBody, finalCount));
            } catch (Exception e) {
                Timber.e(e, "Facility seed failed");
                Threading.main(() -> Toast.makeText(this, "Failed to load facilities: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void pushAllFacilitiesToFirebase(String jsonBody, int count) {
        String url = "https://chimwemwe-app-default-rtdb.firebaseio.com/facilities.json";
        try {
            com.android.volley.toolbox.StringRequest req = new com.android.volley.toolbox.StringRequest(
                    Request.Method.PATCH, url,
                    response -> Toast.makeText(this, count + " facilities saved to Firebase.", Toast.LENGTH_LONG).show(),
                    error -> {
                        Timber.e(error, "Facility seed push failed");
                        Toast.makeText(this, "Saved locally but Firebase push failed.", Toast.LENGTH_LONG).show();
                    }) {
                @Override
                public byte[] getBody() {
                    return jsonBody.getBytes(StandardCharsets.UTF_8);
                }
                @Override
                public String getBodyContentType() {
                    return "application/json; charset=utf-8";
                }
            };
            ((ChwApplication) ChwApplication.getInstance()).getRequestQueue().add(req);
        } catch (Exception e) {
            Timber.e(e, "Volley queue not available for facility seed push");
            Toast.makeText(this, "Firebase push failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static String facilitySlugify(String s) {
        String result = s.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
        return result.substring(0, Math.min(result.length(), 60));
    }

    // ── Dialog helper ─────────────────────────────────────────────

    public void showCustomDialog(Context context, String message) {
        showCustomDialog(context, message, null);
    }

    public void showCustomDialog(Context context, String message, Runnable onDismiss) {
        Dialog dialog = new Dialog(context);
        dialog.setContentView(R.layout.custom_dialog);
        dialog.setCancelable(false);
        ((TextView) dialog.findViewById(R.id.dialog_message)).setText(message);
        Button ok = dialog.findViewById(R.id.dialog_ok_button);
        ok.setOnClickListener(v -> {
            dialog.dismiss();
            if (onDismiss != null) onDismiss.run();
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            int w = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.95f);
            int h = (int) (context.getResources().getDisplayMetrics().heightPixels * 0.8f);
            dialog.getWindow().setLayout(w, h);
        }
    }
}
