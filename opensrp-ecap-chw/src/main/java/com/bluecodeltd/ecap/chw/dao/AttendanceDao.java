package com.bluecodeltd.ecap.chw.dao;

import com.bluecodeltd.ecap.chw.model.AttendanceModel;

import net.sqlcipher.database.SQLiteDatabase;

import org.smartregister.dao.AbstractDao;

import java.util.List;

public class AttendanceDao extends AbstractDao {

    public static final String TABLE = "ec_chimwemwe_attendance";

    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
            "  id                   INTEGER PRIMARY KEY AUTOINCREMENT," +
            "  group_id             INTEGER NOT NULL," +
            "  participant_id       INTEGER NOT NULL," +
            "  session_number       INTEGER NOT NULL," +
            "  session_date         TEXT," +
            "  caregiver_attendance TEXT DEFAULT ''," +
            "  child_attendance     TEXT DEFAULT ''" +
            ")";

    public static void createTable(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_SQL);
    }

    /** Add group_id column to existing installs that were created without it (DB v35). */
    public static void migrateToV35(SQLiteDatabase db) {
        try {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN group_id INTEGER DEFAULT 0");
        } catch (Exception ignored) {
            // Column already exists
        }
    }

    /**
     * Save (insert or replace) attendance for one participant in one session.
     * Uses INSERT OR REPLACE based on (group_id, participant_id, session_number).
     */
    public static void saveAttendance(AttendanceModel m) {
        // Delete existing then insert (simpler than REPLACE which requires UNIQUE constraint)
        AbstractDao.updateDB("DELETE FROM " + TABLE +
                " WHERE group_id=" + m.getGroupId() +
                " AND participant_id=" + m.getParticipantId() +
                " AND session_number=" + m.getSessionNumber());
        String sql = "INSERT INTO " + TABLE +
                " (group_id, participant_id, session_number, session_date," +
                "  caregiver_attendance, child_attendance) VALUES (" +
                m.getGroupId() + "," +
                m.getParticipantId() + "," +
                m.getSessionNumber() + "," +
                q(m.getSessionDate()) + "," +
                q(m.getCaregiverAttendance()) + "," +
                q(m.getChildAttendance()) + ")";
        AbstractDao.updateDB(sql);
    }

    /** Load all attendance records for a specific session of a group. */
    public static List<AttendanceModel> getSessionAttendance(long groupId, int sessionNumber) {
        String sql = "SELECT id, group_id, participant_id, session_number, session_date," +
                " caregiver_attendance, child_attendance FROM " + TABLE +
                " WHERE group_id=" + groupId + " AND session_number=" + sessionNumber;
        return AbstractDao.readData(sql, cursor -> {
            AttendanceModel a = new AttendanceModel();
            a.setId(cursor.getLong(0));
            a.setGroupId(cursor.getLong(1));
            a.setParticipantId(cursor.getLong(2));
            a.setSessionNumber(cursor.getInt(3));
            a.setSessionDate(cursor.getString(4));
            a.setCaregiverAttendance(cursor.getString(5));
            a.setChildAttendance(cursor.getString(6));
            return a;
        });
    }

    /** Get the date recorded for a given session of a group (null if not yet recorded). */
    public static String getSessionDate(long groupId, int sessionNumber) {
        List<String> res = AbstractDao.readData(
                "SELECT session_date FROM " + TABLE +
                " WHERE group_id=" + groupId + " AND session_number=" + sessionNumber +
                " LIMIT 1",
                cursor -> cursor.getString(0));
        return (res != null && !res.isEmpty()) ? res.get(0) : null;
    }

    /** Count how many distinct sessions have at least one participant attended (not all-absent). */
    public static int countRecordedSessions(long groupId) {
        List<Integer> res = AbstractDao.readData(
                "SELECT COUNT(DISTINCT session_number) FROM " + TABLE +
                " WHERE group_id=" + groupId +
                " AND (caregiver_attendance!='' OR child_attendance!='')",
                cursor -> cursor.getInt(0));
        return (res != null && !res.isEmpty()) ? res.get(0) : 0;
    }

    private static String q(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "''") + "'";
    }
}
