package com.bluecodeltd.ecap.chw.dao;

import com.bluecodeltd.ecap.chw.model.HotspotGroupModel;

import net.sqlcipher.database.SQLiteDatabase;

import org.smartregister.dao.AbstractDao;

import java.util.List;

public class HotspotGroupDao extends AbstractDao {

    public static final String TABLE = "ec_chimwemwe_group";

    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
            "  id                      INTEGER PRIMARY KEY AUTOINCREMENT," +
            "  group_code              TEXT," +
            "  hotspot_name            TEXT," +
            "  group_name              TEXT," +
            "  created_date            TEXT," +
            "  province                TEXT," +
            "  district                TEXT," +
            "  location_of_session     TEXT," +
            "  location_gps            TEXT," +
            "  nearest_health_facility TEXT," +
            "  facilitator_1_first_name TEXT," +
            "  facilitator_1_surname    TEXT," +
            "  facilitator_2_first_name TEXT," +
            "  facilitator_2_surname    TEXT," +
            "  session_1_date          TEXT," +
            "  session_2_date          TEXT," +
            "  session_3_date          TEXT," +
            "  session_4_date          TEXT," +
            "  session_5_date          TEXT," +
            "  session_6_date          TEXT," +
            "  session_7_date          TEXT," +
            "  session_8_date          TEXT," +
            "  session_9_date          TEXT," +
            "  session_10_date         TEXT," +
            "  session_11_date         TEXT," +
            "  session_12_date         TEXT," +
            "  session_13_date         TEXT," +
            "  session_14_date         TEXT" +
            ")";

    /** Column added in DB version 32 (system-generated UUID for the group). */
    private static final String ALTER_V32 =
            "ALTER TABLE " + TABLE + " ADD COLUMN group_code TEXT";

    /** Columns added in DB version 31 (province + district). */
    private static final String[] ALTER_V31 = {
            "ALTER TABLE " + TABLE + " ADD COLUMN province  TEXT",
            "ALTER TABLE " + TABLE + " ADD COLUMN district  TEXT"
    };

    /** Columns added in DB version 30 (previously the table only had 4 columns). */
    private static final String[] ALTER_V30 = {
            "ALTER TABLE " + TABLE + " ADD COLUMN location_of_session     TEXT",
            "ALTER TABLE " + TABLE + " ADD COLUMN location_gps            TEXT",
            "ALTER TABLE " + TABLE + " ADD COLUMN nearest_health_facility TEXT",
            "ALTER TABLE " + TABLE + " ADD COLUMN facilitator_1_first_name TEXT",
            "ALTER TABLE " + TABLE + " ADD COLUMN facilitator_1_surname    TEXT",
            "ALTER TABLE " + TABLE + " ADD COLUMN facilitator_2_first_name TEXT",
            "ALTER TABLE " + TABLE + " ADD COLUMN facilitator_2_surname    TEXT",
            "ALTER TABLE " + TABLE + " ADD COLUMN session_1_date          TEXT",
            "ALTER TABLE " + TABLE + " ADD COLUMN session_2_date          TEXT",
            "ALTER TABLE " + TABLE + " ADD COLUMN session_3_date          TEXT",
            "ALTER TABLE " + TABLE + " ADD COLUMN session_4_date          TEXT",
            "ALTER TABLE " + TABLE + " ADD COLUMN session_5_date          TEXT",
            "ALTER TABLE " + TABLE + " ADD COLUMN session_6_date          TEXT",
            "ALTER TABLE " + TABLE + " ADD COLUMN session_7_date          TEXT",
            "ALTER TABLE " + TABLE + " ADD COLUMN session_8_date          TEXT",
            "ALTER TABLE " + TABLE + " ADD COLUMN session_9_date          TEXT",
            "ALTER TABLE " + TABLE + " ADD COLUMN session_10_date         TEXT",
            "ALTER TABLE " + TABLE + " ADD COLUMN session_11_date         TEXT",
            "ALTER TABLE " + TABLE + " ADD COLUMN session_12_date         TEXT",
            "ALTER TABLE " + TABLE + " ADD COLUMN session_13_date         TEXT",
            "ALTER TABLE " + TABLE + " ADD COLUMN session_14_date         TEXT"
    };

    public static void createTable(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_SQL);
    }

    /** Run ALTER TABLE statements to add the new columns on existing installs (DB v30). */
    public static void migrateToV30(SQLiteDatabase db) {
        for (String sql : ALTER_V30) {
            try {
                db.execSQL(sql);
            } catch (Exception ignored) {
                // Column may already exist if migration runs twice
            }
        }
    }

    /** Add group_code column to existing installs (DB v32). */
    public static void migrateToV32(SQLiteDatabase db) {
        try {
            db.execSQL(ALTER_V32);
        } catch (Exception ignored) {
            // Column may already exist
        }
    }

    /** Run ALTER TABLE statements to add province + district columns (DB v31). */
    public static void migrateToV31(SQLiteDatabase db) {
        for (String sql : ALTER_V31) {
            try {
                db.execSQL(sql);
            } catch (Exception ignored) {
                // Column may already exist
            }
        }
    }

    // ── Insert ────────────────────────────────────────────────

    public static long insertGroup(HotspotGroupModel m) {
        String sql = "INSERT INTO " + TABLE + " (" +
                "group_code, hotspot_name, group_name, created_date," +
                "province, district," +
                "location_of_session, location_gps, nearest_health_facility," +
                "facilitator_1_first_name, facilitator_1_surname," +
                "facilitator_2_first_name, facilitator_2_surname," +
                "session_1_date, session_2_date, session_3_date, session_4_date," +
                "session_5_date, session_6_date, session_7_date, session_8_date," +
                "session_9_date, session_10_date, session_11_date, session_12_date," +
                "session_13_date, session_14_date" +
                ") VALUES (" +
                q(m.getGroupCode()) + "," +
                q(m.getHotspotName()) + "," +
                q(m.getGroupName()) + "," +
                q(m.getCreatedDate()) + "," +
                q(m.getProvince()) + "," +
                q(m.getDistrict()) + "," +
                q(m.getLocationOfSession()) + "," +
                q(m.getLocationGps()) + "," +
                q(m.getNearestHealthFacility()) + "," +
                q(m.getFacilitator1FirstName()) + "," +
                q(m.getFacilitator1Surname()) + "," +
                q(m.getFacilitator2FirstName()) + "," +
                q(m.getFacilitator2Surname()) + "," +
                q(m.getSession1Date()) + "," +
                q(m.getSession2Date()) + "," +
                q(m.getSession3Date()) + "," +
                q(m.getSession4Date()) + "," +
                q(m.getSession5Date()) + "," +
                q(m.getSession6Date()) + "," +
                q(m.getSession7Date()) + "," +
                q(m.getSession8Date()) + "," +
                q(m.getSession9Date()) + "," +
                q(m.getSession10Date()) + "," +
                q(m.getSession11Date()) + "," +
                q(m.getSession12Date()) + "," +
                q(m.getSession13Date()) + "," +
                q(m.getSession14Date()) + ")";
        AbstractDao.updateDB(sql);
        List<Long> ids = AbstractDao.readData(
                "SELECT id FROM " + TABLE + " ORDER BY id DESC LIMIT 1",
                cursor -> cursor.getLong(0));
        return (ids != null && !ids.isEmpty()) ? ids.get(0) : -1L;
    }

    // ── Update ────────────────────────────────────────────────

    public static void updateGroup(HotspotGroupModel m) {
        String sql = "UPDATE " + TABLE + " SET " +
                "hotspot_name="             + q(m.getHotspotName()) + "," +
                "group_name="               + q(m.getGroupName()) + "," +
                "province="                 + q(m.getProvince()) + "," +
                "district="                 + q(m.getDistrict()) + "," +
                "location_of_session="      + q(m.getLocationOfSession()) + "," +
                "location_gps="             + q(m.getLocationGps()) + "," +
                "nearest_health_facility="  + q(m.getNearestHealthFacility()) + "," +
                "facilitator_1_first_name=" + q(m.getFacilitator1FirstName()) + "," +
                "facilitator_1_surname="    + q(m.getFacilitator1Surname()) + "," +
                "facilitator_2_first_name=" + q(m.getFacilitator2FirstName()) + "," +
                "facilitator_2_surname="    + q(m.getFacilitator2Surname()) + "," +
                "session_1_date="           + q(m.getSession1Date()) + "," +
                "session_2_date="           + q(m.getSession2Date()) + "," +
                "session_3_date="           + q(m.getSession3Date()) + "," +
                "session_4_date="           + q(m.getSession4Date()) + "," +
                "session_5_date="           + q(m.getSession5Date()) + "," +
                "session_6_date="           + q(m.getSession6Date()) + "," +
                "session_7_date="           + q(m.getSession7Date()) + "," +
                "session_8_date="           + q(m.getSession8Date()) + "," +
                "session_9_date="           + q(m.getSession9Date()) + "," +
                "session_10_date="          + q(m.getSession10Date()) + "," +
                "session_11_date="          + q(m.getSession11Date()) + "," +
                "session_12_date="          + q(m.getSession12Date()) + "," +
                "session_13_date="          + q(m.getSession13Date()) + "," +
                "session_14_date="          + q(m.getSession14Date()) +
                " WHERE id=" + m.getId();
        AbstractDao.updateDB(sql);
    }

    // ── Read ──────────────────────────────────────────────────

    public static HotspotGroupModel getGroup(long id) {
        String sql = "SELECT id, group_code, hotspot_name, group_name, created_date," +
                " province, district," +
                " location_of_session, location_gps, nearest_health_facility," +
                " facilitator_1_first_name, facilitator_1_surname," +
                " facilitator_2_first_name, facilitator_2_surname," +
                " session_1_date,  session_2_date,  session_3_date,  session_4_date," +
                " session_5_date,  session_6_date,  session_7_date,  session_8_date," +
                " session_9_date,  session_10_date, session_11_date, session_12_date," +
                " session_13_date, session_14_date" +
                " FROM " + TABLE + " WHERE id=" + id;
        List<HotspotGroupModel> list = AbstractDao.readData(sql, HotspotGroupDao::mapFull);
        return (list != null && !list.isEmpty()) ? list.get(0) : null;
    }

    public static List<HotspotGroupModel> getAllGroups() {
        String sql = "SELECT g.id, g.group_code, g.hotspot_name, g.group_name, g.created_date," +
                " g.province, g.district," +
                " g.location_of_session, g.location_gps, g.nearest_health_facility," +
                " g.facilitator_1_first_name, g.facilitator_1_surname," +
                " g.facilitator_2_first_name, g.facilitator_2_surname," +
                " g.session_1_date,  g.session_2_date,  g.session_3_date,  g.session_4_date," +
                " g.session_5_date,  g.session_6_date,  g.session_7_date,  g.session_8_date," +
                " g.session_9_date,  g.session_10_date, g.session_11_date, g.session_12_date," +
                " g.session_13_date, g.session_14_date," +
                " (SELECT COUNT(*) FROM ec_chimwemwe_participant WHERE group_id=g.id) AS p_count," +
                " (SELECT COUNT(DISTINCT session_number) FROM ec_chimwemwe_attendance WHERE group_id=g.id AND (caregiver_attendance!='' OR child_attendance!='')) AS s_count" +
                " FROM " + TABLE + " g ORDER BY g.id DESC";
        return AbstractDao.readData(sql, cursor -> {
            HotspotGroupModel m = mapFull(cursor);
            m.setParticipantCount(cursor.getInt(28));
            m.setSessionsRecorded(cursor.getInt(29));
            return m;
        });
    }

    public static void deleteGroup(long id) {
        AbstractDao.updateDB("DELETE FROM ec_chimwemwe_attendance  WHERE group_id=" + id);
        AbstractDao.updateDB("DELETE FROM ec_chimwemwe_participant WHERE group_id=" + id);
        AbstractDao.updateDB("DELETE FROM " + TABLE + "             WHERE id=" + id);
    }

    // ── Mapper ────────────────────────────────────────────────

    private static HotspotGroupModel mapFull(android.database.Cursor cursor) {
        HotspotGroupModel m = new HotspotGroupModel();
        m.setId(cursor.getLong(0));
        m.setGroupCode(cursor.getString(1));
        m.setHotspotName(cursor.getString(2));
        m.setGroupName(cursor.getString(3));
        m.setCreatedDate(cursor.getString(4));
        m.setProvince(cursor.getString(5));
        m.setDistrict(cursor.getString(6));
        m.setLocationOfSession(cursor.getString(7));
        m.setLocationGps(cursor.getString(8));
        m.setNearestHealthFacility(cursor.getString(9));
        m.setFacilitator1FirstName(cursor.getString(10));
        m.setFacilitator1Surname(cursor.getString(11));
        m.setFacilitator2FirstName(cursor.getString(12));
        m.setFacilitator2Surname(cursor.getString(13));
        m.setSession1Date(cursor.getString(14));
        m.setSession2Date(cursor.getString(15));
        m.setSession3Date(cursor.getString(16));
        m.setSession4Date(cursor.getString(17));
        m.setSession5Date(cursor.getString(18));
        m.setSession6Date(cursor.getString(19));
        m.setSession7Date(cursor.getString(20));
        m.setSession8Date(cursor.getString(21));
        m.setSession9Date(cursor.getString(22));
        m.setSession10Date(cursor.getString(23));
        m.setSession11Date(cursor.getString(24));
        m.setSession12Date(cursor.getString(25));
        m.setSession13Date(cursor.getString(26));
        m.setSession14Date(cursor.getString(27));
        return m;
    }

    private static String q(String s) {
        if (s == null) return "NULL";
        return "'" + s.replace("'", "''") + "'";
    }
}
