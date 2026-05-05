package com.bluecodeltd.ecap.chw.dao;

import com.bluecodeltd.ecap.chw.model.AttendanceModel;

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
     * Returns a map keyed by participant code (ec_chimwemwe_participant.participant_id, e.g.
     * "CHIM-1234567890") for the session snapshot. Values contain caregiver/child attendance
     * for that participant.
     */
    public static Map<String, AttendanceModel> getSessionAttendanceMap(String groupId, int sessionNumber) {
        Map<String, AttendanceModel> out = new HashMap<>();
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

            List<Map<String, AttendanceModel>> rows = AbstractDao.readData(select.toString(), cursor -> {
                Map<String, AttendanceModel> map = new HashMap<>();
                String sessionDate = cursor.getString(0);
                int idx = 1;
                for (int i = 1; i <= 20; i++) {
                    String pidRaw = cursor.getString(idx++);
                    String cg = cursor.getString(idx++);
                    String ch = cursor.getString(idx++);
                    if (pidRaw == null || pidRaw.trim().isEmpty()) continue;
                    String pid = pidRaw.trim();
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
     * Direct write of the per-slot participant id and attendance columns for
     * (group_id, session_number). Bypasses the OpenSRP form processor so the slot
     * columns are guaranteed populated even when the form pipeline doesn't translate
     * spinner values into client attributes correctly. The OpenSRP Event/Client
     * records (used for sync) are still created by the caller via saveRegistration —
     * this only patches the DB columns.
     *
     * Slot match key is the participant business code (ec_chimwemwe_participant.participant_id),
     * NOT the row PK — OpenSRP overwrites the participant row's `id` column with the
     * non-numeric base_entity_id when processing the Client, which makes the row PK
     * unusable as a stable identifier from Java.
     *
     * Inserts a stub row keyed by the deterministic base_entity_id if one doesn't exist,
     * so the UPDATE always has a target — independent of whether the OpenSRP processor
     * has finished writing the row.
     */
    public static void upsertSlots(String groupId, int sessionNumber,
                                   List<AttendanceModel> attendances) {
        if (groupId == null || groupId.trim().isEmpty() || attendances == null) return;
        String gid = groupId.trim();

        String baseEntityId = "chimwemwe-attendance-" + gid + "-" + sessionNumber;
        String sessionDate = "";
        if (!attendances.isEmpty() && attendances.get(0).getSessionDate() != null) {
            sessionDate = attendances.get(0).getSessionDate();
        }

        // INSERT OR IGNORE relies on the unique base_entity_id index — no-op if a row
        // already exists for this (gid, sessionNumber) under that base_entity_id.
        String insertSql = "INSERT OR IGNORE INTO " + TABLE +
                " (base_entity_id, group_id, session_number, session_date, last_interacted_with) " +
                "VALUES (" + q(baseEntityId) + "," + q(gid) + "," + sessionNumber + "," +
                q(sessionDate) + "," + System.currentTimeMillis() + ")";
        AbstractDao.updateDB(insertSql);

        int n = Math.min(attendances.size(), 20);

        // Single combined UPDATE: filled slots get the snapshot data, unused slots are cleared.
        // Filtering by base_entity_id (unique) avoids any group_id/session_number whitespace
        // mismatch with whatever the OpenSRP processor wrote.
        StringBuilder set = new StringBuilder("UPDATE ").append(TABLE).append(" SET ");
        set.append("session_date=").append(q(sessionDate));
        for (int i = 0; i < 20; i++) {
            int slot = i + 1;
            if (i < n) {
                AttendanceModel a = attendances.get(i);
                String pid = a.getParticipantId() != null ? a.getParticipantId() : "";
                String cg  = a.getCaregiverAttendance() != null ? a.getCaregiverAttendance() : "";
                String ch  = a.getChildAttendance()    != null ? a.getChildAttendance()    : "";
                set.append(",p").append(slot).append("_participant_id=").append(q(pid))
                   .append(",p").append(slot).append("_cg_attendance=").append(q(cg))
                   .append(",p").append(slot).append("_child_attendance=").append(q(ch));
            } else {
                set.append(",p").append(slot).append("_participant_id=NULL")
                   .append(",p").append(slot).append("_cg_attendance=''")
                   .append(",p").append(slot).append("_child_attendance=''");
            }
        }
        set.append(" WHERE base_entity_id=").append(q(baseEntityId));
        AbstractDao.updateDB(set.toString());
    }

    /**
     * Clears a participant slot in all session snapshots for a group.
     * This avoids deleting entire session rows that also contain other participants' data.
     * participantCode is the business identifier (ec_chimwemwe_participant.participant_id).
     */
    public static void removeParticipantFromGroupSessions(String groupId, String participantCode) {
        if (groupId == null || groupId.trim().isEmpty() || participantCode == null || participantCode.trim().isEmpty()) return;
        String gid = groupId.trim();
        String pid = participantCode.trim();
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
