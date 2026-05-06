package com.bluecodeltd.chimwemwe.chw.util;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.bluecodeltd.chimwemwe.chw.dao.ChimwemweIndexDao;
import com.bluecodeltd.chimwemwe.chw.model.ChimwemweIndexModel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import timber.log.Timber;

/**
 * Service for importing client records from a CSV file into the
 * ec_chimwemwe_index table.
 *
 * Expected CSV columns (first row is a header, case-insensitive):
 *   remote_id, first_name, last_name, gender, birthdate, unique_id,
 *   phone, sub_population, facility, province, district, case_status
 *
 * Any subset of these columns is accepted; missing columns are treated as empty.
 */
public class CsvFormImportService {

    private static final String TAG = "CsvFormImportService";

    /** Timeout applied to the entire import (3 minutes). */
    private static final long IMPORT_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(3);

    // ---- Public API ----

    public interface ProgressCallback {
        /**
         * Called periodically with progress updates.
         *
         * @param processedRows rows fully processed so far
         * @param totalRows     total data rows in file (0 if unknown)
         * @param importedRows  rows successfully inserted
         * @param skippedRows   rows skipped (duplicate)
         * @param failedRows    rows that could not be parsed / inserted
         */
        void onProgress(int processedRows, int totalRows,
                        int importedRows, int skippedRows, int failedRows);
    }

    /** Immutable result of a single-file import. */
    public static class ImportSummary {
        public final int importedRows;
        public final int skippedRows;
        public final int failedRows;
        public final boolean timedOutDuringProcessing;
        public final String fileName;

        ImportSummary(int importedRows, int skippedRows, int failedRows,
                      boolean timedOut, String fileName) {
            this.importedRows = importedRows;
            this.skippedRows = skippedRows;
            this.failedRows = failedRows;
            this.timedOutDuringProcessing = timedOut;
            this.fileName = fileName;
        }

        /** Returns true if the file-level process failed (all rows failed). */
        public boolean hasFileFailure() {
            return failedRows > 0 && importedRows == 0 && skippedRows == 0;
        }
    }

    /**
     * Count the number of data rows (excluding header) in the CSV at {@code uri}.
     * Returns 0 if the count cannot be determined.
     */
    public static int getDataRowCount(Context context, Uri uri) {
        int count = 0;
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) { firstLine = false; continue; } // skip header
                if (!line.trim().isEmpty()) count++;
            }
        } catch (Exception e) {
            Timber.tag(TAG).w(e, "getDataRowCount failed for %s", uri);
        }
        return count;
    }

    /**
     * Import records from the CSV at {@code uri} into ec_chimwemwe_index.
     *
     * @param context          application context
     * @param uri              content URI of the CSV file
     * @param progressCallback optional progress callback (may be null)
     * @return {@link ImportSummary} describing what happened
     */
    public static ImportSummary importFromCsvUri(Context context, Uri uri,
                                                  ProgressCallback progressCallback) {
        String fileName = resolveFileName(context, uri);
        int importedRows = 0;
        int skippedRows = 0;
        int failedRows = 0;
        boolean timedOut = false;

        final long startTime = System.currentTimeMillis();

        try (InputStream is = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {

            // Parse header row
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return new ImportSummary(0, 0, 0, false, fileName);
            }
            String[] headers = parseCsvLine(headerLine);
            Map<String, Integer> colIndex = buildColumnIndex(headers);

            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) lines.add(line);
            }

            int total = lines.size();
            for (int i = 0; i < total; i++) {

                // Timeout guard
                if (System.currentTimeMillis() - startTime > IMPORT_TIMEOUT_MS) {
                    timedOut = true;
                    break;
                }

                try {
                    String[] fields = parseCsvLine(lines.get(i));
                    ChimwemweIndexModel model = buildModel(fields, colIndex);
                    if (model == null) {
                        failedRows++;
                    } else {
                        boolean saved = ChimwemweIndexDao.saveRecord(model);
                        if (saved) importedRows++;
                        else skippedRows++;
                    }
                } catch (Exception e) {
                    failedRows++;
                    Timber.tag(TAG).w(e, "Error parsing row %d", i + 1);
                }

                // Report progress every 10 rows or on last row
                if (progressCallback != null && (i % 10 == 0 || i == total - 1)) {
                    int processed = i + 1;
                    int imp = importedRows, skip = skippedRows, fail = failedRows;
                    progressCallback.onProgress(processed, total, imp, skip, fail);
                }
            }

        } catch (IOException e) {
            Timber.tag(TAG).e(e, "importFromCsvUri IO error for %s", uri);
            return new ImportSummary(importedRows, skippedRows, failedRows + 1, false, fileName);
        }

        return new ImportSummary(importedRows, skippedRows, failedRows, timedOut, fileName);
    }

    // ---- Private helpers ----

    private static String resolveFileName(Context context, Uri uri) {
        String name = null;
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver()
                    .query(uri, new String[]{OpenableColumns.DISPLAY_NAME},
                            null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    name = cursor.getString(0);
                }
            } catch (Exception ignored) {}
        }
        if (name == null) name = uri.getLastPathSegment();
        return name;
    }

    /**
     * Build a column-name → index map from the header row.
     * Names are lowercased and trimmed.
     */
    private static Map<String, Integer> buildColumnIndex(String[] headers) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < headers.length; i++) {
            map.put(headers[i].trim().toLowerCase(), i);
        }
        return map;
    }

    /**
     * Attempt to build a {@link ChimwemweIndexModel} from a parsed CSV row.
     * Returns null if the row cannot produce a usable record.
     */
    private static ChimwemweIndexModel buildModel(String[] fields,
                                                   Map<String, Integer> colIndex) {
        ChimwemweIndexModel m = new ChimwemweIndexModel();
        m.setRemoteId(get(fields, colIndex, "remote_id"));
        m.setFirstName(get(fields, colIndex, "first_name"));
        m.setLastName(get(fields, colIndex, "last_name"));
        m.setGender(get(fields, colIndex, "gender"));
        m.setBirthdate(get(fields, colIndex, "birthdate"));
        m.setUniqueId(get(fields, colIndex, "unique_id"));
        m.setPhone(get(fields, colIndex, "phone"));
        m.setSubPopulation(get(fields, colIndex, "sub_population"));
        m.setFacility(get(fields, colIndex, "facility"));
        m.setProvince(get(fields, colIndex, "province"));
        m.setDistrict(get(fields, colIndex, "district"));
        m.setCaseStatus(get(fields, colIndex, "case_status"));
        m.setSource("csv");
        m.setDateAdded(LocalDate.now().toString());

        // Require at least a name or remote_id
        boolean hasName = (m.getFirstName() != null && !m.getFirstName().isEmpty())
                || (m.getLastName() != null && !m.getLastName().isEmpty());
        boolean hasId = m.getRemoteId() != null && !m.getRemoteId().isEmpty();
        if (!hasName && !hasId) return null;

        // Generate a remote_id if missing
        if (!hasId) {
            m.setRemoteId("csv_" + System.currentTimeMillis() + "_"
                    + (m.getFirstName() != null ? m.getFirstName() : "")
                    + "_" + (m.getLastName() != null ? m.getLastName() : ""));
        }

        return m;
    }

    private static String get(String[] fields, Map<String, Integer> colIndex, String col) {
        Integer idx = colIndex.get(col);
        if (idx == null || idx >= fields.length) return null;
        String val = fields[idx].trim();
        // Strip surrounding quotes
        if (val.length() >= 2 && val.charAt(0) == '"' && val.charAt(val.length() - 1) == '"') {
            val = val.substring(1, val.length() - 1).replace("\"\"", "\"");
        }
        return val.isEmpty() ? null : val;
    }

    /**
     * Minimal RFC-4180 CSV line parser.
     * Handles quoted fields (including embedded commas and escaped quotes).
     */
    private static String[] parseCsvLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        sb.append('"');
                        i++; // skip escaped quote
                    } else {
                        inQuotes = false;
                    }
                } else {
                    sb.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    tokens.add(sb.toString());
                    sb.setLength(0);
                } else {
                    sb.append(c);
                }
            }
        }
        tokens.add(sb.toString());
        return tokens.toArray(new String[0]);
    }
}
