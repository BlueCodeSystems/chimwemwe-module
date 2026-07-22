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
                    "  caregiver_signature   TEXT DEFAULT ''," +
                    "  caregiver_gps         TEXT DEFAULT ''" +
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

    /** Add per-participant GPS captured alongside the caregiver signature (DB v56). */
    public static void migrateToV56(SQLiteDatabase db) {
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN caregiver_gps TEXT DEFAULT ''"); } catch (Exception ignored) {}
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
            String sql = "SELECT participant_id, session_date, caregiver_attendance, child_attendance, caregiver_signature, caregiver_gps FROM " + TABLE +
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
                a.setCaregiverGps(cursor.getString(5) != null ? cursor.getString(5) : "");
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
     * when the new value is empty - so flipping a participant's attendance from "Group" or
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

    /**
     * Sequential eligibility check. Returns the earliest earlier session where this participant was
     * not recorded as a complete caregiver-child pair. Returns 0 when eligible.
     *
     * "Attended" = caregiver AND child present. If either member is absent, the entire pair is
     * ineligible for later sessions. Sessions with no row at all are ignored, so late-joiners are not
     * blocked.
     */
    public static int firstMissedSessionBefore(String groupId, int sessionNumber, String participantCode) {
        if (sessionNumber <= 1) return 0;
        if (groupId == null || groupId.trim().isEmpty()) return 0;
        if (participantCode == null || participantCode.trim().isEmpty()) return 0;

        String sql = "SELECT MIN(session_number) FROM " + TABLE +
                " WHERE group_id=" + q(groupId.trim()) +
                " AND participant_id=" + q(participantCode.trim()) +
                " AND session_number < " + sessionNumber +
                " AND (delete_status IS NULL OR delete_status <> '1')" +
                " AND NOT (" +
                "   LOWER(TRIM(IFNULL(caregiver_attendance,''))) IN ('group','home visit')" +
                "   AND LOWER(TRIM(IFNULL(child_attendance,''))) IN ('group','home visit')" +
                " )";

        List<Integer> res = AbstractDao.readData(sql, cursor -> cursor.getInt(0));
        return (res != null && !res.isEmpty() && res.get(0) != null) ? res.get(0) : 0;
    }

    public static String firstIneligibleReasonBefore(String groupId, int sessionNumber, String participantCode) {
        int missed = firstMissedSessionBefore(groupId, sessionNumber, participantCode);
        if (missed <= 0) return "";

        String sql = "SELECT caregiver_attendance, child_attendance FROM " + TABLE +
                " WHERE group_id=" + q(groupId != null ? groupId.trim() : "") +
                " AND participant_id=" + q(participantCode != null ? participantCode.trim() : "") +
                " AND session_number=" + missed +
                " AND (delete_status IS NULL OR delete_status <> '1')" +
                " LIMIT 1";

        List<String> res = AbstractDao.readData(sql, cursor -> {
            String cg = cursor.getString(0) != null ? cursor.getString(0).trim() : "";
            String ch = cursor.getString(1) != null ? cursor.getString(1).trim() : "";
            boolean caregiverPresent = "Group".equalsIgnoreCase(cg) || "Home Visit".equalsIgnoreCase(cg);
            boolean childPresent = "Group".equalsIgnoreCase(ch) || "Home Visit".equalsIgnoreCase(ch);
            if (caregiverPresent && !childPresent) {
                return "Caregiver attended Session " + missed + " but child did not attend; this pair is not eligible for this session.";
            }
            if (!caregiverPresent && childPresent) {
                return "Child attended Session " + missed + " but caregiver did not attend; this pair is not eligible for this session.";
            }
            return "Caregiver and child did not attend Session " + missed + "; this pair is not eligible for this session.";
        });
        return res != null && !res.isEmpty() && res.get(0) != null ? res.get(0) : "";
    }

    /**
     * Session progression gate. A session is complete once at least one participant pair has both
     * caregiver and child recorded as "Group" or "Home Visit".
     */
    public static boolean isSessionComplete(String groupId, int sessionNumber) {
        if (groupId == null || groupId.trim().isEmpty()) return false;

        String sql = "SELECT COUNT(*) FROM " + TABLE +
                " WHERE group_id=" + q(groupId.trim()) +
                " AND session_number=" + sessionNumber +
                " AND (delete_status IS NULL OR delete_status <> '1')" +
                " AND LOWER(TRIM(IFNULL(caregiver_attendance,''))) IN ('group','home visit')" +
                " AND LOWER(TRIM(IFNULL(child_attendance,''))) IN ('group','home visit')";

        List<Integer> res = AbstractDao.readData(sql, cursor -> cursor.getInt(0));
        return res != null && !res.isEmpty() && res.get(0) != null && res.get(0) > 0;
    }

    /**
     * Child attendance summary by session for the dashboard stacked bar chart.
     * Returns [sessionIndex][0 full pair, 1 partial pair, 2 absent pair].
     */
    public static int[][] getChildAttendanceBySession() {
        int[][] counts = new int[14][3];
        String sql = "SELECT session_number," +
                " SUM(CASE WHEN " +
                "   LOWER(TRIM(IFNULL(caregiver_attendance,''))) IN ('group','home visit')" +
                "   AND LOWER(TRIM(IFNULL(child_attendance,''))) IN ('group','home visit')" +
                " THEN 1 ELSE 0 END) AS full_pairs," +
                " SUM(CASE WHEN " +
                "   (LOWER(TRIM(IFNULL(caregiver_attendance,''))) IN ('group','home visit')" +
                "    OR LOWER(TRIM(IFNULL(child_attendance,''))) IN ('group','home visit'))" +
                "   AND NOT (LOWER(TRIM(IFNULL(caregiver_attendance,''))) IN ('group','home visit')" +
                "    AND LOWER(TRIM(IFNULL(child_attendance,''))) IN ('group','home visit'))" +
                " THEN 1 ELSE 0 END) AS partial_pairs," +
                " SUM(CASE WHEN " +
                "   LOWER(TRIM(IFNULL(caregiver_attendance,''))) NOT IN ('group','home visit')" +
                "   AND LOWER(TRIM(IFNULL(child_attendance,''))) NOT IN ('group','home visit')" +
                " THEN 1 ELSE 0 END) AS absent_pairs" +
                " FROM " + TABLE +
                " WHERE session_number BETWEEN 1 AND 14" +
                " AND (delete_status IS NULL OR delete_status <> '1')" +
                " GROUP BY session_number";

        List<int[]> rows = AbstractDao.readData(sql, cursor -> new int[]{
                cursor.getInt(0), cursor.getInt(1), cursor.getInt(2), cursor.getInt(3)
        });
        if (rows != null) {
            for (int[] row : rows) {
                if (row == null || row.length < 4) continue;
                int session = row[0];
                if (session < 1 || session > 14) continue;
                counts[session - 1][0] = row[1];
                counts[session - 1][1] = row[2];
                counts[session - 1][2] = row[3];
            }
        }
        return counts;
    }

    public static void updateSignature(String groupId, int sessionNumber, String participantCode, String signature) {
        if (groupId == null || participantCode == null || participantCode.trim().isEmpty()) return;
        String baseEntityId = "chimwemwe-session-attendance-" + groupId.trim() + "-" + sessionNumber + "-" + participantCode.trim();
        AbstractDao.updateDB("UPDATE " + TABLE + " SET caregiver_signature=" + q(signature) +
                " WHERE base_entity_id=" + q(baseEntityId));
    }

    /** Per-participant GPS ("lat,lng") captured alongside the caregiver signature. */
    public static void updateGps(String groupId, int sessionNumber, String participantCode, String gps) {
        if (groupId == null || participantCode == null || participantCode.trim().isEmpty()) return;
        String baseEntityId = "chimwemwe-session-attendance-" + groupId.trim() + "-" + sessionNumber + "-" + participantCode.trim();
        AbstractDao.updateDB("UPDATE " + TABLE + " SET caregiver_gps=" + q(gps != null ? gps : "") +
                " WHERE base_entity_id=" + q(baseEntityId));
    }

    private static String q(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "''") + "'";
    }
}
