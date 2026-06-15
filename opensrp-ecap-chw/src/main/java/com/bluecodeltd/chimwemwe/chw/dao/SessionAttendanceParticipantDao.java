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
                    "  child_attendance     TEXT DEFAULT ''," +
                    "  caregiver_signature   TEXT DEFAULT ''" +
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
                                "  participant_id, session_date, caregiver_attendance, child_attendance, caregiver_signature) " +
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
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN caregiver_signature TEXT DEFAULT ''"); } catch (Exception ignored) {}
    }

    /**
     * Returns a map keyed by participant business code (ec_chimwemwe_participant.participant_id,
     * e.g. "CHIM-1234567890") for a given session (unlimited participants). Keyed by code rather
     * than the row PK because OpenSRP's CONFLICT_REPLACE corrupts the participant row's INTEGER
     * id column with a non-numeric base_entity_id, so the row PK can't identify a participant.
     */
    public static Map<String, AttendanceModel> getSessionAttendanceMap(String groupId, int sessionNumber) {
        Map<String, AttendanceModel> out = new HashMap<>();
        try {
            String sql = "SELECT participant_id, session_date, caregiver_attendance, child_attendance, caregiver_signature FROM " + TABLE +
                    " WHERE group_id=" + q(groupId) + " AND session_number=" + sessionNumber +
                    " AND (delete_status IS NULL OR delete_status <> '1')";
            List<Map<String, AttendanceModel>> rows = AbstractDao.readData(sql, cursor -> {
                Map<String, AttendanceModel> map = new HashMap<>();
                String pidRaw = cursor.getString(0);
                if (pidRaw == null || pidRaw.trim().isEmpty()) return map;
                String pid = pidRaw.trim();

                AttendanceModel a = new AttendanceModel();
                a.setGroupId(groupId);
                a.setParticipantId(pid);
                a.setSessionNumber(sessionNumber);
                a.setSessionDate(cursor.getString(1));
                a.setCaregiverAttendance(cursor.getString(2) != null ? cursor.getString(2) : "");
                a.setChildAttendance(cursor.getString(3) != null ? cursor.getString(3) : "");
                a.setCaregiverSignature(cursor.getString(4) != null ? cursor.getString(4) : "");
                map.put(pid, a);
                return map;
            });

            if (rows != null) {
                for (Map<String, AttendanceModel> r : rows) if (r != null) out.putAll(r);
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static void softDeleteForGroup(String groupId) {
        AbstractDao.updateDB("UPDATE " + TABLE + " SET delete_status='1' WHERE group_id=" + q(groupId));
    }

    /** participantCode is the business identifier (ec_chimwemwe_participant.participant_id). */
    public static void softDeleteForParticipant(String participantCode) {
        if (participantCode == null || participantCode.trim().isEmpty()) return;
        AbstractDao.updateDB("UPDATE " + TABLE + " SET delete_status='1' WHERE participant_id=" + q(participantCode.trim()));
    }

    /**
     * Direct write of one (group, session, participant) row. Bypasses the OpenSRP form processor
     * because its edit-mode JsonFormUtils.merge() preserves existing non-empty attribute values
     * when the new value is empty — so flipping a participant's attendance from "Group" or
     * "Home Visit" to "Absent" (which encodes as "") through saveRegistration alone does NOT
     * land. The OpenSRP Client/Event records used for sync are still created by the caller via
     * saveRegistration; this only patches the bind_type table columns to whatever the caller
     * actually intended (including empty strings).
     *
     * Idempotent: insert-or-ignore by deterministic base_entity_id, then update the snapshot
     * columns explicitly.
     */
    public static void upsertLine(String groupId, int sessionNumber, String sessionDate,
                                  String participantCode, String caregiverAttendance,
                                  String childAttendance) {
        if (groupId == null || groupId.trim().isEmpty()) return;
        if (participantCode == null || participantCode.trim().isEmpty()) return;
        String gid = groupId.trim();
        String pid = participantCode.trim();
        String date = sessionDate != null ? sessionDate : "";
        String cg = caregiverAttendance != null ? caregiverAttendance : "";
        String ch = childAttendance != null ? childAttendance : "";
        String baseEntityId = "chimwemwe-session-attendance-" + gid + "-" + sessionNumber + "-" + pid;
        long now = System.currentTimeMillis();

        String insert = "INSERT OR IGNORE INTO " + TABLE +
                " (base_entity_id, last_interacted_with, delete_status, is_closed, group_id, session_number," +
                "  participant_id, session_date, caregiver_attendance, child_attendance, caregiver_signature) " +
                "VALUES (" + q(baseEntityId) + "," + now + ",NULL,0," + q(gid) + "," + sessionNumber + "," +
                q(pid) + "," + q(date) + "," + q(cg) + "," + q(ch) + ",''" + ")";
        AbstractDao.updateDB(insert);

        String update = "UPDATE " + TABLE + " SET " +
                "last_interacted_with=" + now + "," +
                "delete_status=NULL," +
                "session_date=" + q(date) + "," +
                "caregiver_attendance=" + q(cg) + "," +
                "child_attendance=" + q(ch) +
                " WHERE base_entity_id=" + q(baseEntityId);
        AbstractDao.updateDB(update);
    }

    public static void updateSignature(String groupId, int sessionNumber, String participantCode, String signature) {
        if (groupId == null || participantCode == null || participantCode.trim().isEmpty()) return;
        String baseEntityId = "chimwemwe-session-attendance-" + groupId.trim() + "-" + sessionNumber + "-" + participantCode.trim();
        AbstractDao.updateDB("UPDATE " + TABLE + " SET caregiver_signature=" + q(signature) +
                " WHERE base_entity_id=" + q(baseEntityId));
    }

    private static String q(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "''") + "'";
    }
}
