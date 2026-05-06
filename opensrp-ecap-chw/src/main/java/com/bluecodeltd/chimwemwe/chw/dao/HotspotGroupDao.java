package com.bluecodeltd.chimwemwe.chw.dao;

import com.bluecodeltd.chimwemwe.chw.model.HotspotGroupModel;

import net.sqlcipher.database.SQLiteDatabase;

import org.smartregister.dao.AbstractDao;

import java.util.List;

public class HotspotGroupDao extends AbstractDao {

    public static final String TABLE = "ec_chimwemwe_group";

    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
            "  id                      INTEGER PRIMARY KEY AUTOINCREMENT," +
            "  base_entity_id          TEXT," +
            "  last_interacted_with    INTEGER," +
            "  delete_status           TEXT," +
            "  group_id                TEXT," +
            "  hotspot_name            TEXT," +
            "  group_name              TEXT," +
            "  created_date            TEXT," +
            "  province                TEXT," +
            "  district                TEXT," +
            "  location_of_session     TEXT," +
            "  location_gps            TEXT," +
            "  nearest_health_facility TEXT," +
            "  facilitator_name_1      TEXT," +
            "  facilitator_name_2      TEXT," +
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

    /** Column added in DB version 37 (business group identifier from the form). */
    private static final String ALTER_V37 =
            "ALTER TABLE " + TABLE + " ADD COLUMN group_id TEXT";

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
            "ALTER TABLE " + TABLE + " ADD COLUMN facilitator_name_1      TEXT",
            "ALTER TABLE " + TABLE + " ADD COLUMN facilitator_name_2      TEXT",
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

    /** OpenSRP standard column added in DB version 43. */
    public static void migrateToV43(SQLiteDatabase db) {
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN base_entity_id TEXT"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN last_interacted_with INTEGER"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN delete_status TEXT"); } catch (Exception ignored) {}
        try {
            db.execSQL("UPDATE " + TABLE + " SET base_entity_id=group_id " +
                    "WHERE (base_entity_id IS NULL OR TRIM(base_entity_id)='') " +
                    "AND (group_id IS NOT NULL AND TRIM(group_id)!='')");
        } catch (Exception ignored) {}
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

    /** Replace split facilitator columns with combined facilitator_name_1/2 (DB v36). */
    public static void migrateToV36(SQLiteDatabase db) {
        String[] alters = {
                "ALTER TABLE " + TABLE + " ADD COLUMN facilitator_name_1 TEXT",
                "ALTER TABLE " + TABLE + " ADD COLUMN facilitator_name_2 TEXT"
        };
        for (String sql : alters) {
            try { db.execSQL(sql); } catch (Exception ignored) {}
        }
    }

    public static void migrateToV32(SQLiteDatabase db) {
        // no-op; retained for historical DB upgrade compatibility
    }

    public static void migrateToV37(SQLiteDatabase db) {
        try {
            db.execSQL(ALTER_V37);
        } catch (Exception ignored) {
            // Column may already exist
        }
        try {
            db.execSQL("UPDATE " + TABLE + " SET group_id = " +
                    "COALESCE(NULLIF(group_id, ''), CAST(id AS TEXT))");
        } catch (Exception ignored) {
            // best effort backfill
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
        String idPart = m.getId() > 0 ? m.getId() + "," : "NULL,";
        String sql = "INSERT INTO " + TABLE + " (" +
                "id, group_id, hotspot_name, group_name, created_date," +
                "province, district," +
                "location_of_session, location_gps, nearest_health_facility," +
                "facilitator_name_1, facilitator_name_2," +
                "session_1_date, session_2_date, session_3_date, session_4_date," +
                "session_5_date, session_6_date, session_7_date, session_8_date," +
                "session_9_date, session_10_date, session_11_date, session_12_date," +
                "session_13_date, session_14_date" +
                ") VALUES (" +
                idPart +
                q(m.getGroupId()) + "," +
                q(m.getHotspotName()) + "," +
                q(m.getGroupName()) + "," +
                q(m.getCreatedDate()) + "," +
                q(m.getProvince()) + "," +
                q(m.getDistrict()) + "," +
                q(m.getLocationOfSession()) + "," +
                q(m.getLocationGps()) + "," +
                q(m.getNearestHealthFacility()) + "," +
                q(m.getFacilitatorName1()) + "," +
                q(m.getFacilitatorName2()) + "," +
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
                "group_id="                 + q(m.getGroupId()) + "," +
                "hotspot_name="             + q(m.getHotspotName()) + "," +
                "group_name="               + q(m.getGroupName()) + "," +
                "province="                 + q(m.getProvince()) + "," +
                "district="                 + q(m.getDistrict()) + "," +
                "location_of_session="      + q(m.getLocationOfSession()) + "," +
                "location_gps="             + q(m.getLocationGps()) + "," +
                "nearest_health_facility="  + q(m.getNearestHealthFacility()) + "," +
                "facilitator_name_1="       + q(m.getFacilitatorName1()) + "," +
                "facilitator_name_2="       + q(m.getFacilitatorName2()) + "," +
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
        String sql = "SELECT id, group_id, hotspot_name, group_name, created_date," +
                " province, district," +
                " location_of_session, location_gps, nearest_health_facility," +
                " facilitator_name_1, facilitator_name_2," +
                " session_1_date,  session_2_date,  session_3_date,  session_4_date," +
                " session_5_date,  session_6_date,  session_7_date,  session_8_date," +
                " session_9_date,  session_10_date, session_11_date, session_12_date," +
                " session_13_date, session_14_date" +
                " FROM " + TABLE + " WHERE id=" + id +
                " AND (delete_status IS NULL OR delete_status <> '1')";
        List<HotspotGroupModel> list = AbstractDao.readData(sql, HotspotGroupDao::mapFull);
        return (list != null && !list.isEmpty()) ? list.get(0) : null;
    }

    public static List<HotspotGroupModel> getAllGroups() {
        String sqlWithSessions = "SELECT g.id, g.group_id, g.hotspot_name, g.group_name, g.created_date," +
                " g.province, g.district," +
                " g.location_of_session, g.location_gps, g.nearest_health_facility," +
                " g.facilitator_name_1, g.facilitator_name_2," +
                " g.session_1_date,  g.session_2_date,  g.session_3_date,  g.session_4_date," +
                " g.session_5_date,  g.session_6_date,  g.session_7_date,  g.session_8_date," +
                " g.session_9_date,  g.session_10_date, g.session_11_date, g.session_12_date," +
                " g.session_13_date, g.session_14_date," +
                " (SELECT COUNT(*) FROM ec_chimwemwe_participant p WHERE p.group_id=g.group_id AND (p.delete_status IS NULL OR p.delete_status <> '1')) AS p_count," +
                " (SELECT COUNT(DISTINCT session_number) FROM ec_chimwemwe_session_attendance sa WHERE sa.group_id=g.group_id) AS s_count" +
                " FROM " + TABLE + " g WHERE (g.delete_status IS NULL OR g.delete_status <> '1')" +
                " ORDER BY g.id DESC";
        try {
            return AbstractDao.readData(sqlWithSessions, cursor -> {
                HotspotGroupModel m = mapFull(cursor);
                m.setParticipantCount(cursor.getInt(26));
                m.setSessionsRecorded(cursor.getInt(27));
                return m;
            });
        } catch (Exception e) {
            // ec_chimwemwe_session_attendance may not exist on older installs — fall back
            String sqlFallback = "SELECT g.id, g.group_id, g.hotspot_name, g.group_name, g.created_date," +
                    " g.province, g.district," +
                    " g.location_of_session, g.location_gps, g.nearest_health_facility," +
                    " g.facilitator_name_1, g.facilitator_name_2," +
                    " g.session_1_date,  g.session_2_date,  g.session_3_date,  g.session_4_date," +
                    " g.session_5_date,  g.session_6_date,  g.session_7_date,  g.session_8_date," +
                    " g.session_9_date,  g.session_10_date, g.session_11_date, g.session_12_date," +
                    " g.session_13_date, g.session_14_date," +
                    " (SELECT COUNT(*) FROM ec_chimwemwe_participant p WHERE p.group_id=g.group_id AND (p.delete_status IS NULL OR p.delete_status <> '1')) AS p_count," +
                    " 0 AS s_count" +
                    " FROM " + TABLE + " g WHERE (g.delete_status IS NULL OR g.delete_status <> '1')" +
                    " ORDER BY g.id DESC";
            return AbstractDao.readData(sqlFallback, cursor -> {
                HotspotGroupModel m = mapFull(cursor);
                m.setParticipantCount(cursor.getInt(26));
                m.setSessionsRecorded(0);
                return m;
            });
        }
    }

    /** Groups filtered by nearest_health_facility (case-insensitive). */
    public static List<HotspotGroupModel> getGroupsByFacility(String facility) {
        String f = facility.trim().replace("'", "''");
        String sql = "SELECT g.id, g.group_id, g.hotspot_name, g.group_name, g.created_date," +
                " g.province, g.district," +
                " g.location_of_session, g.location_gps, g.nearest_health_facility," +
                " g.facilitator_name_1, g.facilitator_name_2," +
                " g.session_1_date,  g.session_2_date,  g.session_3_date,  g.session_4_date," +
                " g.session_5_date,  g.session_6_date,  g.session_7_date,  g.session_8_date," +
                " g.session_9_date,  g.session_10_date, g.session_11_date, g.session_12_date," +
                " g.session_13_date, g.session_14_date," +
                " (SELECT COUNT(*) FROM ec_chimwemwe_participant p WHERE p.group_id=g.group_id AND (p.delete_status IS NULL OR p.delete_status <> '1')) AS p_count," +
                " (SELECT COUNT(DISTINCT session_number) FROM ec_chimwemwe_session_attendance sa WHERE sa.group_id=g.group_id) AS s_count" +
                " FROM " + TABLE + " g WHERE (g.delete_status IS NULL OR g.delete_status <> '1')" +
                " AND TRIM(LOWER(g.nearest_health_facility)) = LOWER('" + f + "')" +
                " ORDER BY g.id DESC";
        try {
            return AbstractDao.readData(sql, cursor -> {
                HotspotGroupModel m = mapFull(cursor);
                m.setParticipantCount(cursor.getInt(26));
                m.setSessionsRecorded(cursor.getInt(27));
                return m;
            });
        } catch (Exception e) {
            return new java.util.ArrayList<>();
        }
    }

    /** Groups filtered by hotspot_name (case-insensitive). */
    public static List<HotspotGroupModel> getGroupsByHotspot(String hotspot) {
        String h = hotspot.trim().replace("'", "''");
        String sql = "SELECT g.id, g.group_id, g.hotspot_name, g.group_name, g.created_date," +
                " g.province, g.district," +
                " g.location_of_session, g.location_gps, g.nearest_health_facility," +
                " g.facilitator_name_1, g.facilitator_name_2," +
                " g.session_1_date,  g.session_2_date,  g.session_3_date,  g.session_4_date," +
                " g.session_5_date,  g.session_6_date,  g.session_7_date,  g.session_8_date," +
                " g.session_9_date,  g.session_10_date, g.session_11_date, g.session_12_date," +
                " g.session_13_date, g.session_14_date," +
                " (SELECT COUNT(*) FROM ec_chimwemwe_participant p WHERE p.group_id=g.group_id AND (p.delete_status IS NULL OR p.delete_status <> '1')) AS p_count," +
                " (SELECT COUNT(DISTINCT session_number) FROM ec_chimwemwe_session_attendance sa WHERE sa.group_id=g.group_id) AS s_count" +
                " FROM " + TABLE + " g WHERE (g.delete_status IS NULL OR g.delete_status <> '1')" +
                " AND TRIM(LOWER(g.hotspot_name)) = LOWER('" + h + "')" +
                " ORDER BY g.id DESC";
        try {
            return AbstractDao.readData(sql, cursor -> {
                HotspotGroupModel m = mapFull(cursor);
                m.setParticipantCount(cursor.getInt(26));
                m.setSessionsRecorded(cursor.getInt(27));
                return m;
            });
        } catch (Exception e) {
            return new java.util.ArrayList<>();
        }
    }

    public static int countGroups() {
        List<Integer> counts = AbstractDao.readData(
                "SELECT COUNT(*) FROM " + TABLE + " WHERE (delete_status IS NULL OR delete_status <> '1')",
                cursor -> cursor.getInt(0));
        return (counts != null && !counts.isEmpty()) ? counts.get(0) : 0;
    }

    /** Returns rows of [name, count] for distinct non-empty nearest_health_facility values. */
    public static List<String[]> getDistinctFacilitiesWithCount() {
        String sql = "SELECT nearest_health_facility, COUNT(*) AS cnt" +
                " FROM " + TABLE +
                " WHERE (delete_status IS NULL OR delete_status <> '1')" +
                " AND nearest_health_facility IS NOT NULL AND TRIM(nearest_health_facility) != ''" +
                " GROUP BY TRIM(LOWER(nearest_health_facility))" +
                " ORDER BY cnt DESC, nearest_health_facility ASC";
        return AbstractDao.readData(sql, cursor -> new String[]{cursor.getString(0), cursor.getString(1)});
    }

    /** Returns rows of [name, count] for distinct non-empty hotspot_name values. */
    public static List<String[]> getDistinctHotspotsWithCount() {
        String sql = "SELECT hotspot_name, COUNT(*) AS cnt" +
                " FROM " + TABLE +
                " WHERE (delete_status IS NULL OR delete_status <> '1')" +
                " AND hotspot_name IS NOT NULL AND TRIM(hotspot_name) != ''" +
                " GROUP BY TRIM(LOWER(hotspot_name))" +
                " ORDER BY cnt DESC, hotspot_name ASC";
        return AbstractDao.readData(sql, cursor -> new String[]{cursor.getString(0), cursor.getString(1)});
    }

    /** Returns rows of [hotspot_name, count] for distinct hotspots within a specific facility. */
    public static List<String[]> getDistinctHotspotsByFacility(String facility) {
        String f = facility.trim().replace("'", "''");
        String sql = "SELECT hotspot_name, COUNT(*) AS cnt" +
                " FROM " + TABLE +
                " WHERE (delete_status IS NULL OR delete_status <> '1')" +
                " AND TRIM(LOWER(nearest_health_facility)) = LOWER('" + f + "')" +
                " AND hotspot_name IS NOT NULL AND TRIM(hotspot_name) != ''" +
                " GROUP BY TRIM(LOWER(hotspot_name))" +
                " ORDER BY cnt DESC, hotspot_name ASC";
        return AbstractDao.readData(sql, cursor -> new String[]{cursor.getString(0), cursor.getString(1)});
    }

    /** Soft-delete group + related Chimwemwe records using delete_status='1'. */
    public static void deleteGroup(long id) {
        HotspotGroupModel g = getGroup(id);
        String groupId = (g != null && g.getGroupId() != null && !g.getGroupId().trim().isEmpty())
                ? g.getGroupId().trim()
                : String.valueOf(id);
        deleteGroupByBusinessId(groupId);
        AbstractDao.updateDB("UPDATE " + TABLE + " SET delete_status='1' WHERE id=" + id);
    }

    public static void deleteGroupByBusinessId(String groupId) {
        if (groupId == null || groupId.trim().isEmpty()) return;
        String gid = groupId.trim();
        AbstractDao.updateDB("UPDATE ec_chimwemwe_session_attendance SET delete_status='1' WHERE group_id=" + q(gid));
        AbstractDao.updateDB("UPDATE ec_chimwemwe_session_attendance_participant SET delete_status='1' WHERE group_id=" + q(gid));
        AbstractDao.updateDB("UPDATE ec_chimwemwe_review             SET delete_status='1' WHERE group_id=" + q(gid));
        AbstractDao.updateDB("UPDATE ec_chimwemwe_referral           SET delete_status='1' WHERE group_id=" + q(gid));
        AbstractDao.updateDB("UPDATE ec_chimwemwe_participant        SET delete_status='1' WHERE group_id=" + q(gid));
        AbstractDao.updateDB("UPDATE " + TABLE + "                   SET delete_status='1' WHERE group_id=" + q(gid));
    }

    public static HotspotGroupModel getGroupByBusinessId(String groupId) {
        String sql = "SELECT id, group_id, hotspot_name, group_name, created_date," +
                " province, district," +
                " location_of_session, location_gps, nearest_health_facility," +
                " facilitator_name_1, facilitator_name_2," +
                " session_1_date,  session_2_date,  session_3_date,  session_4_date," +
                " session_5_date,  session_6_date,  session_7_date,  session_8_date," +
                " session_9_date,  session_10_date, session_11_date, session_12_date," +
                " session_13_date, session_14_date" +
                " FROM " + TABLE + " WHERE group_id=" + q(groupId) +
                " AND (delete_status IS NULL OR delete_status <> '1')" +
                " LIMIT 1";
        List<HotspotGroupModel> list = AbstractDao.readData(sql, HotspotGroupDao::mapFull);
        return (list != null && !list.isEmpty()) ? list.get(0) : null;
    }

    // ── Mapper ────────────────────────────────────────────────

    private static HotspotGroupModel mapFull(android.database.Cursor cursor) {
        HotspotGroupModel m = new HotspotGroupModel();
        m.setId(cursor.getLong(0));
        m.setGroupId(cursor.getString(1));
        m.setHotspotName(cursor.getString(2));
        m.setGroupName(cursor.getString(3));
        m.setCreatedDate(cursor.getString(4));
        m.setProvince(cursor.getString(5));
        m.setDistrict(cursor.getString(6));
        m.setLocationOfSession(cursor.getString(7));
        m.setLocationGps(cursor.getString(8));
        m.setNearestHealthFacility(cursor.getString(9));
        m.setFacilitatorName1(cursor.getString(10));
        m.setFacilitatorName2(cursor.getString(11));
        m.setSession1Date(cursor.getString(12));
        m.setSession2Date(cursor.getString(13));
        m.setSession3Date(cursor.getString(14));
        m.setSession4Date(cursor.getString(15));
        m.setSession5Date(cursor.getString(16));
        m.setSession6Date(cursor.getString(17));
        m.setSession7Date(cursor.getString(18));
        m.setSession8Date(cursor.getString(19));
        m.setSession9Date(cursor.getString(20));
        m.setSession10Date(cursor.getString(21));
        m.setSession11Date(cursor.getString(22));
        m.setSession12Date(cursor.getString(23));
        m.setSession13Date(cursor.getString(24));
        m.setSession14Date(cursor.getString(25));
        return m;
    }

    private static String q(String s) {
        if (s == null) return "NULL";
        return "'" + s.replace("'", "''") + "'";
    }
}
