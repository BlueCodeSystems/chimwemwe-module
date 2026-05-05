package com.bluecodeltd.chimwemwe.chw.dao;

import com.bluecodeltd.chimwemwe.chw.model.AttendanceModel;

import net.sqlcipher.database.SQLiteDatabase;

import org.smartregister.dao.AbstractDao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Normalized attendance lines: one row per participant per session.
 * This removes the 20-participant limitation of the session snapshot table.
 */
public class SessionAttendanceParticipantDao extends AbstractDao {

    public static final String TABLE = "ec_chimwemwe_session_attendance_participant";

    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                    "  id                   INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  base_entity_id       TEXT," +
                    "  last_interacted_with INTEGER," +
                    "  delete_status        TEXT," +
                    "  is_closed            INTEGER DEFAULT 0," +
                    "  group_id             TEXT NOT NULL," +
                    "  session_number       INTEGER NOT NULL," +
                    "  participant_id       TEXT NOT NULL," +
                    "  session_date         TEXT," +
                    "  caregiver_attendance TEXT DEFAULT ''," +
                    "  child_attendance     TEXT DEFAULT ''" +
                    ")";

    public static void createTable(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_SQL);
        try {
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS " + TABLE + "_base_entity_id_idx ON " +
                    TABLE + "(base_entity_id)");
        } catch (Exception ignored) {}
        try {
            db.execSQL("CREATE INDEX IF NOT EXISTS " + TABLE + "_group_session_idx ON " +
                    TABLE + "(group_id, session_number)");
        } catch (Exception ignored) {}
        try {
            db.execSQL("CREATE INDEX IF NOT EXISTS " + TABLE + "_participant_idx ON " +
                    TABLE + "(participant_id)");
        } catch (Exception ignored) {}
    }

    /** Create table and best-effort backfill from the older 20-slot session snapshot rows (DB v47). */
    public static void migrateToV47(SQLiteDatabase db) {
        try { createTable(db); } catch (Exception ignored) {}

        // Backfill: copy p1..p20 slot values into normalized rows.
        // Uses deterministic base_entity_id so repeated upgrades are safe via INSERT OR IGNORE.
        try {
            for (int i = 1; i <= 20; i++) {
                String pidCol = "p" + i + "_participant_id";
                String cgCol = "p" + i + "_cg_attendance";
                String chCol = "p" + i + "_child_attendance";
                String sql =
                        "INSERT OR IGNORE INTO " + TABLE +
                                " (base_entity_id, last_interacted_with, delete_status, group_id, session_number," +
                                "  participant_id, session_date, caregiver_attendance, child_attendance) " +
                                "SELECT " +
                                "  ('chimwemwe-session-attendance-' || IFNULL(group_id,'') || '-' || IFNULL(session_number,'') || '-' || IFNULL(" + pidCol + ",''))," +
                                "  last_interacted_with," +
                                "  delete_status," +
                                "  group_id," +
                                "  session_number," +
                                "  " + pidCol + "," +
                                "  session_date," +
                                "  " + cgCol + "," +
                                "  " + chCol + " " +
                                "FROM ec_chimwemwe_session_attendance " +
                                "WHERE " + pidCol + " IS NOT NULL AND TRIM(" + pidCol + ") <> ''";
                db.execSQL(sql);
            }
        } catch (Exception ignored) {
            // Older installs might not have the snapshot table or slot columns; ignore.
        }
    }

    /** Add standard OpenSRP is_closed column for compatibility (DB v48). */
    public static void migrateToV48(SQLiteDatabase db) {
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN is_closed INTEGER DEFAULT 0"); } catch (Exception ignored) {}
    }

    /**
     * Returns a map keyed by participant id for a given session (unlimited participants).
     */
    public static Map<Long, AttendanceModel> getSessionAttendanceMap(String groupId, int sessionNumber) {
        Map<Long, AttendanceModel> out = new HashMap<>();
        try {
            String sql = "SELECT participant_id, session_date, caregiver_attendance, child_attendance FROM " + TABLE +
                    " WHERE group_id=" + q(groupId) + " AND session_number=" + sessionNumber +
                    " AND (delete_status IS NULL OR delete_status <> '1')";
            List<Map<Long, AttendanceModel>> rows = AbstractDao.readData(sql, cursor -> {
                Map<Long, AttendanceModel> map = new HashMap<>();
                String pidRaw = cursor.getString(0);
                if (pidRaw == null || pidRaw.trim().isEmpty()) return map;
                long pid;
                try { pid = Long.parseLong(pidRaw.trim()); } catch (Exception ignored) { return map; }

                AttendanceModel a = new AttendanceModel();
                a.setGroupId(groupId);
                a.setParticipantId(pid);
                a.setSessionNumber(sessionNumber);
                a.setSessionDate(cursor.getString(1));
                a.setCaregiverAttendance(cursor.getString(2) != null ? cursor.getString(2) : "");
                a.setChildAttendance(cursor.getString(3) != null ? cursor.getString(3) : "");
                map.put(pid, a);
                return map;
            });

            if (rows != null) {
                for (Map<Long, AttendanceModel> r : rows) if (r != null) out.putAll(r);
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static void softDeleteForGroup(String groupId) {
        AbstractDao.updateDB("UPDATE " + TABLE + " SET delete_status='1' WHERE group_id=" + q(groupId));
    }

    public static void softDeleteForParticipant(long participantId) {
        AbstractDao.updateDB("UPDATE " + TABLE + " SET delete_status='1' WHERE participant_id=" + q(String.valueOf(participantId)));
    }

    private static String q(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "''") + "'";
    }
}
