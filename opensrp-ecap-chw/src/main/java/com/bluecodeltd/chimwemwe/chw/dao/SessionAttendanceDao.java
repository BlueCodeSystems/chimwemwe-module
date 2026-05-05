package com.bluecodeltd.chimwemwe.chw.dao;

import com.bluecodeltd.chimwemwe.chw.model.AttendanceModel;

import net.sqlcipher.database.SQLiteDatabase;

import org.smartregister.dao.AbstractDao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

public class SessionAttendanceDao extends AbstractDao {

    public static final String TABLE = "ec_chimwemwe_session_attendance";

    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                    "  id                     INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  base_entity_id         TEXT," +
                    "  last_interacted_with   INTEGER," +
                    "  delete_status          TEXT," +
                    "  group_id               TEXT NOT NULL," +
                    "  participant_id         TEXT," +
                    "  session_number         INTEGER NOT NULL," +
                    "  session_date           TEXT," +
                    "  session_type           TEXT," +
                    slotColumnsSql() +
                    ")";

    private static String slotColumnsSql() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 20; i++) {
            sb.append("  p").append(i).append("_participant_id  TEXT,");
            sb.append("  p").append(i).append("_cg_attendance   TEXT DEFAULT '',");
            sb.append("  p").append(i).append("_child_attendance TEXT DEFAULT ''");
            if (i < 20) sb.append(",");
        }
        return sb.toString();
    }

    public static void createTable(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_SQL);
        try {
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS " + TABLE + "_base_entity_id_idx ON " +
                    TABLE + "(base_entity_id)");
        } catch (Exception ignored) {
        }
        try {
            db.execSQL("CREATE INDEX IF NOT EXISTS " + TABLE + "_group_session_idx ON " +
                    TABLE + "(group_id, session_number)");
        } catch (Exception ignored) {
        }
    }

    /** Best-effort migration for existing installs (DB v42). */
    public static void migrateToV42(SQLiteDatabase db) {
        try {
            db.execSQL(CREATE_TABLE_SQL);
        } catch (Exception ignored) {
        }

        // Base columns
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN base_entity_id TEXT"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN last_interacted_with INTEGER"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN delete_status TEXT"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN group_id TEXT DEFAULT ''"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN session_number INTEGER DEFAULT 0"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN session_date TEXT"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN session_type TEXT"); } catch (Exception ignored) {}

        // Slot columns
        for (int i = 1; i <= 20; i++) {
            try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN p" + i + "_participant_id TEXT"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN p" + i + "_cg_attendance TEXT DEFAULT ''"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN p" + i + "_child_attendance TEXT DEFAULT ''"); } catch (Exception ignored) {}
        }

        try {
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS " + TABLE + "_base_entity_id_idx ON " +
                    TABLE + "(base_entity_id)");
        } catch (Exception ignored) {
        }
        try {
            db.execSQL("CREATE INDEX IF NOT EXISTS " + TABLE + "_group_session_idx ON " +
                    TABLE + "(group_id, session_number)");
        } catch (Exception ignored) {
        }
    }

    /** OpenSRP standard delete_status column added in DB version 43 (for installs jumping from v42). */
    public static void migrateToV43(SQLiteDatabase db) {
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN delete_status TEXT"); } catch (Exception ignored) {}
    }

    /** GPS coordinates captured at the time the session is recorded (DB v44). */
    public static void migrateToV44(SQLiteDatabase db) {
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN session_gps TEXT"); } catch (Exception ignored) {}
    }

    /** participant_id column added (DB v45). */
    public static void migrateToV45(SQLiteDatabase db) {
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN participant_id TEXT"); } catch (Exception ignored) {}
    }

    public static boolean hasSession(String groupId, int sessionNumber) {
        List<Integer> res = AbstractDao.readData(
                "SELECT COUNT(*) FROM " + TABLE +
                        " WHERE group_id=" + q(groupId) + " AND session_number=" + sessionNumber +
                        " AND (delete_status IS NULL OR delete_status <> '1')",
                cursor -> cursor.getInt(0));
        return res != null && !res.isEmpty() && res.get(0) > 0;
    }

    /**
     * Returns the existing base_entity_id for a saved session row.
     * Used to ensure edits do not generate/override base_entity_id (which creates duplicate Clients).
     */
    public static String getSessionBaseEntityId(String groupId, int sessionNumber) {
        List<String> res = AbstractDao.readData(
                "SELECT base_entity_id FROM " + TABLE +
                        " WHERE group_id=" + q(groupId) + " AND session_number=" + sessionNumber +
                        " AND (delete_status IS NULL OR delete_status <> '1')" +
                        " ORDER BY last_interacted_with DESC LIMIT 1",
                cursor -> cursor.getString(0));
        return (res != null && !res.isEmpty()) ? res.get(0) : null;
    }

    public static String getSessionGps(String groupId, int sessionNumber) {
        List<String> res = AbstractDao.readData(
                "SELECT session_gps FROM " + TABLE +
                        " WHERE group_id=" + q(groupId) + " AND session_number=" + sessionNumber +
                        " ORDER BY last_interacted_with DESC LIMIT 1",
                cursor -> cursor.getString(0));
        return (res != null && !res.isEmpty()) ? res.get(0) : null;
    }

    public static String getSessionDate(String groupId, int sessionNumber) {
        List<String> res = AbstractDao.readData(
                "SELECT session_date FROM " + TABLE +
                        " WHERE group_id=" + q(groupId) + " AND session_number=" + sessionNumber +
                        " AND (delete_status IS NULL OR delete_status <> '1')" +
                        " ORDER BY last_interacted_with DESC LIMIT 1",
                cursor -> cursor.getString(0));
        return (res != null && !res.isEmpty()) ? res.get(0) : null;
    }

    /**
     * Returns a map keyed by participant id for the session snapshot.
     * Values contain caregiver/child attendance for that participant.
     */
    public static Map<Long, AttendanceModel> getSessionAttendanceMap(String groupId, int sessionNumber) {
        Map<Long, AttendanceModel> out = new HashMap<>();
        try {
            StringBuilder select = new StringBuilder();
            select.append("SELECT session_date");
            for (int i = 1; i <= 20; i++) {
                select.append(", p").append(i).append("_participant_id");
                select.append(", p").append(i).append("_cg_attendance");
                select.append(", p").append(i).append("_child_attendance");
            }
            select.append(" FROM ").append(TABLE)
                    .append(" WHERE group_id=").append(q(groupId))
                    .append(" AND session_number=").append(sessionNumber)
                    .append(" AND (delete_status IS NULL OR delete_status <> '1')")
                    .append(" ORDER BY last_interacted_with DESC LIMIT 1");

            List<Map<Long, AttendanceModel>> rows = AbstractDao.readData(select.toString(), cursor -> {
                Map<Long, AttendanceModel> map = new HashMap<>();
                String sessionDate = cursor.getString(0);
                int idx = 1;
                for (int i = 1; i <= 20; i++) {
                    String pidRaw = cursor.getString(idx++);
                    String cg = cursor.getString(idx++);
                    String ch = cursor.getString(idx++);
                    if (pidRaw == null || pidRaw.trim().isEmpty()) continue;
                    long pid;
                    try {
                        pid = Long.parseLong(pidRaw.trim());
                    } catch (Exception ignored) {
                        continue;
                    }
                    AttendanceModel a = new AttendanceModel();
                    a.setGroupId(groupId);
                    a.setParticipantId(pid);
                    a.setSessionNumber(sessionNumber);
                    a.setSessionDate(sessionDate);
                    a.setCaregiverAttendance(cg != null ? cg : "");
                    a.setChildAttendance(ch != null ? ch : "");
                    map.put(pid, a);
                }
                return map;
            });

            if (rows != null && !rows.isEmpty() && rows.get(0) != null) {
                out.putAll(rows.get(0));
            }
        } catch (Exception e) {
            Timber.e(e, "SessionAttendanceDao.getSessionAttendanceMap failed");
        }
        return out;
    }

    /**
     * Clears a participant slot in all session snapshots for a group.
     * This avoids deleting entire session rows that also contain other participants' data.
     */
    public static void removeParticipantFromGroupSessions(String groupId, long participantRowId) {
        if (groupId == null || groupId.trim().isEmpty() || participantRowId <= 0) return;
        String gid = groupId.trim();
        String pid = String.valueOf(participantRowId);
        for (int i = 1; i <= 20; i++) {
            String sql = "UPDATE " + TABLE + " SET " +
                    "p" + i + "_participant_id=NULL," +
                    "p" + i + "_cg_attendance=''," +
                    "p" + i + "_child_attendance='' " +
                    "WHERE group_id=" + q(gid) + " AND p" + i + "_participant_id=" + q(pid);
            AbstractDao.updateDB(sql);
        }
    }

    private static String q(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "''") + "'";
    }
}
