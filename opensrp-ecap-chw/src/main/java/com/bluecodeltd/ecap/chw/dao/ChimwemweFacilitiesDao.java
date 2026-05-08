package com.bluecodeltd.chimwemwe.chw.dao;

import com.bluecodeltd.chimwemwe.chw.model.ChimwemweFacilityModel;

import android.content.ContentValues;

import net.sqlcipher.database.SQLiteDatabase;

import org.smartregister.dao.AbstractDao;

import java.util.List;
import java.util.Map;

import timber.log.Timber;

public class ChimwemweFacilitiesDao extends AbstractDao {

    public static final String TABLE = "chimwemwe_facilities";

    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                    "  id                   INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  base_entity_id       TEXT," +
                    "  last_interacted_with INTEGER," +
                    "  delete_status        TEXT," +
                    "  is_closed            INTEGER DEFAULT 0," +
                    "  facility_name        TEXT NOT NULL," +
                    "  district             TEXT," +
                    "  province             TEXT" +
                    ")";

    public static void createTable(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_SQL);
        try {
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS " + TABLE + "_base_entity_id_idx ON " +
                    TABLE + "(base_entity_id)");
        } catch (Exception ignored) {}
        try {
            db.execSQL("CREATE INDEX IF NOT EXISTS " + TABLE + "_name_idx ON " +
                    TABLE + "(facility_name)");
        } catch (Exception ignored) {}
    }

    /** DB v49 */
    public static void migrateToV49(SQLiteDatabase db) {
        try { createTable(db); } catch (Exception e) { Timber.e(e, "ChimwemweFacilitiesDao.migrateToV49"); }
    }

    public static void upsertFacility(String baseEntityId, String facilityName, String district, String province) {
        String now = String.valueOf(System.currentTimeMillis());
        String sql = "INSERT OR REPLACE INTO " + TABLE +
                " (base_entity_id, last_interacted_with, delete_status, is_closed, facility_name, district, province) VALUES (" +
                q(baseEntityId) + "," +
                now + "," +
                q("0") + "," +
                "0," +
                q(toTitleCase(facilityName)) + "," +
                q(district) + "," +
                q(province) +
                ")";
        AbstractDao.updateDB(sql);
    }

    /**
     * Inserts or replaces all facilities in a single transaction.
     * @param facilities map of baseEntityId → [facilityName, district, province]
     * @return number of rows inserted/replaced
     */
    public static int batchUpsert(Map<String, String[]> facilities) {
        if (facilities == null || facilities.isEmpty()) return 0;
        SQLiteDatabase db = getRepository().getWritableDatabase();
        int count = 0;
        db.beginTransaction();
        try {
            long now = System.currentTimeMillis();
            for (Map.Entry<String, String[]> entry : facilities.entrySet()) {
                String id = entry.getKey();
                String[] vals = entry.getValue();
                if (id == null || vals == null || vals.length < 3) continue;
                ContentValues cv = new ContentValues();
                cv.put("base_entity_id", id);
                cv.put("last_interacted_with", now);
                cv.put("delete_status", "0");
                cv.put("is_closed", 0);
                cv.put("facility_name", toTitleCase(vals[0]));
                cv.put("district", vals[1]);
                cv.put("province", vals[2]);
                db.insertWithOnConflict(TABLE, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                count++;
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Timber.e(e, "ChimwemweFacilitiesDao.batchUpsert failed");
        } finally {
            db.endTransaction();
        }
        return count;
    }

    /**
     * Returns [facility_name, hotspot_count] for all active facilities, ordered by name.
     * hotspot_count is the number of distinct hotspots in ec_chimwemwe_group linked to that facility.
     */
    public static List<ChimwemweFacilityModel> getFacilities(String search) {
        return getFacilities(search, null);
    }

    public static List<ChimwemweFacilityModel> getFacilities(String search, String district) {
        String where = " WHERE (delete_status IS NULL OR delete_status <> '1')";
        if (district != null && !district.trim().isEmpty()) {
            // Normalize: lowercase + remove spaces so "Kapiri Mposhi" matches "Kapirimposhi"
            String d = district.trim().toLowerCase().replace(" ", "").replace("'", "''");
            where += " AND REPLACE(LOWER(district), ' ', '') LIKE '%" + d + "%'";
        }
        if (search != null && !search.trim().isEmpty()) {
            String s = search.trim().replace("'", "''");
            where += " AND (facility_name LIKE '%" + s + "%' OR district LIKE '%" + s + "%' OR province LIKE '%" + s + "%')";
        }
        String sql = "SELECT base_entity_id, facility_name, district, province FROM " + TABLE + where + " ORDER BY facility_name COLLATE NOCASE ASC";
        return AbstractDao.readData(sql, cursor -> {
            ChimwemweFacilityModel m = new ChimwemweFacilityModel();
            m.setBaseEntityId(cursor.getString(0));
            m.setFacilityName(cursor.getString(1));
            m.setDistrict(cursor.getString(2));
            m.setProvince(cursor.getString(3));
            return m;
        });
    }

    private static String toTitleCase(String s) {
        if (s == null || s.isEmpty()) return s;
        String[] words = s.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (sb.length() > 0) sb.append(' ');
            if (word.length() == 1) {
                sb.append(Character.toUpperCase(word.charAt(0)));
            } else {
                sb.append(Character.toUpperCase(word.charAt(0)));
                sb.append(word.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    private static String q(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "''") + "'";
    }
}

