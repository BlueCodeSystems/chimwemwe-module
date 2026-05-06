package com.bluecodeltd.chimwemwe.chw.dao;

import com.bluecodeltd.chimwemwe.chw.model.AttendanceModel;

import net.sqlcipher.database.SQLiteDatabase;

import org.smartregister.dao.AbstractDao;

import java.util.List;

public class AttendanceDao extends AbstractDao {

    public static final String TABLE = "ec_chimwemwe_attendance";

    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
            "  id                   INTEGER PRIMARY KEY AUTOINCREMENT," +
            "  base_entity_id       TEXT," +
            "  last_interacted_with INTEGER," +
            "  delete_status        TEXT," +
            "  group_id             TEXT NOT NULL," +
            "  participant_id       TEXT NOT NULL," +
            "  session_number       INTEGER NOT NULL," +
            "  session_date         TEXT," +
            "  caregiver_attendance TEXT DEFAULT ''," +
            "  child_attendance     TEXT DEFAULT ''" +
            ")";

    public static void createTable(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_SQL);
        try {
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS " + TABLE + "_base_entity_id_idx ON " +
                    TABLE + "(base_entity_id)");
        } catch (Exception ignored) {
        }
    }

    /** Add group_id column to existing installs that were created without it (DB v35). */
    public static void migrateToV35(SQLiteDatabase db) {
        try {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN group_id TEXT DEFAULT ''");
        } catch (Exception ignored) {
            // Column already exists
        }
    }

    /** Add OpenSRP standard columns + index for client processor sync (DB v41). */
    public static void migrateToV41(SQLiteDatabase db) {
        try {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN base_entity_id TEXT");
        } catch (Exception ignored) {
        }
        try {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN last_interacted_with INTEGER");
        } catch (Exception ignored) {
        }
        try {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN delete_status TEXT");
        } catch (Exception ignored) {
        }
        try {
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS " + TABLE + "_base_entity_id_idx ON " +
                    TABLE + "(base_entity_id)");
        } catch (Exception ignored) {
        }
        try {
            db.execSQL("UPDATE " + TABLE + " SET base_entity_id=" +
                    "'chimwemwe-attendance-' || IFNULL(group_id,'') || '-' || IFNULL(session_number,'') || '-' || IFNULL(participant_id,'')" +
                    " WHERE base_entity_id IS NULL OR TRIM(base_entity_id)=''");
        } catch (Exception ignored) {
        }
    }

    /** OpenSRP standard delete_status column added in DB version 43 (for installs jumping from v42). */
    public static void migrateToV43(SQLiteDatabase db) {
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN delete_status TEXT"); } catch (Exception ignored) {}
    }

    /**
     * Save (insert or replace) attendance for one participant in one session.
     * Uses INSERT OR REPLACE based on (group_id, participant_id, session_number).
     */
    public static void saveAttendance(AttendanceModel m) {
        // Delete existing then insert (simpler than REPLACE which requires UNIQUE constraint)
        AbstractDao.updateDB("DELETE FROM " + TABLE +
                " WHERE group_id=" + q(m.getGroupId()) +
                " AND participant_id=" + q(m.getParticipantId()) +
                " AND session_number=" + m.getSessionNumber());
        String sql = "INSERT INTO " + TABLE +
                " (base_entity_id, group_id, participant_id, session_number, session_date," +
                "  caregiver_attendance, child_attendance) VALUES (" +
                q("chimwemwe-attendance-" + m.getGroupId() + "-" + m.getSessionNumber() + "-" + m.getParticipantId()) + "," +
                q(m.getGroupId()) + "," +
                q(m.getParticipantId()) + "," +
                m.getSessionNumber() + "," +
                q(m.getSessionDate()) + "," +
                q(m.getCaregiverAttendance()) + "," +
                q(m.getChildAttendance()) + ")";
        AbstractDao.updateDB(sql);
    }

    /** Load all attendance records for a specific session of a group. */
    public static List<AttendanceModel> getSessionAttendance(String groupId, int sessionNumber) {
        String sql = "SELECT id, group_id, participant_id, session_number, session_date," +
                " caregiver_attendance, child_attendance FROM " + TABLE +
                " WHERE group_id=" + q(groupId) + " AND session_number=" + sessionNumber +
                " AND (delete_status IS NULL OR delete_status <> '1')";
        return AbstractDao.readData(sql, cursor -> {
            AttendanceModel a = new AttendanceModel();
            a.setId(cursor.getLong(0));
            a.setGroupId(cursor.getString(1));
            String pid = cursor.getString(2);
            a.setParticipantId(pid != null ? pid.trim() : "");
            a.setSessionNumber(cursor.getInt(3));
            a.setSessionDate(cursor.getString(4));
            a.setCaregiverAttendance(cursor.getString(5));
            a.setChildAttendance(cursor.getString(6));
            return a;
        });
    }

    /** Get the date recorded for a given session of a group (null if not yet recorded). */
    public static String getSessionDate(String groupId, int sessionNumber) {
        List<String> res = AbstractDao.readData(
                "SELECT session_date FROM " + TABLE +
                " WHERE group_id=" + q(groupId) + " AND session_number=" + sessionNumber +
                " AND (delete_status IS NULL OR delete_status <> '1')" +
                " LIMIT 1",
                cursor -> cursor.getString(0));
        return (res != null && !res.isEmpty()) ? res.get(0) : null;
    }

    /** Count how many distinct sessions have at least one participant attended (not all-absent). */
    public static int countRecordedSessions(String groupId) {
        List<Integer> res = AbstractDao.readData(
                "SELECT COUNT(DISTINCT session_number) FROM " + TABLE +
                " WHERE group_id=" + q(groupId) +
                " AND (delete_status IS NULL OR delete_status <> '1')" +
                " AND (caregiver_attendance!='' OR child_attendance!='')",
                cursor -> cursor.getInt(0));
        return (res != null && !res.isEmpty()) ? res.get(0) : 0;
    }

    private static String q(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "''") + "'";
    }
}
