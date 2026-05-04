package com.bluecodeltd.ecap.chw.dao;

import com.bluecodeltd.ecap.chw.model.ChimwemweFacilityModel;

import net.sqlcipher.database.SQLiteDatabase;

import org.smartregister.dao.AbstractDao;

import java.util.List;

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
                q(facilityName) + "," +
                q(district) + "," +
                q(province) +
                ")";
        AbstractDao.updateDB(sql);
    }

    public static List<ChimwemweFacilityModel> getFacilities(String search) {
        String where = " WHERE (delete_status IS NULL OR delete_status <> '1')";
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

    private static String q(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "''") + "'";
    }
}

