package com.bluecodeltd.chimwemwe.chw.dao;

import com.bluecodeltd.chimwemwe.chw.model.ParticipantModel;

import net.sqlcipher.database.SQLiteDatabase;

import org.smartregister.dao.AbstractDao;
import org.smartregister.util.JsonFormUtils;

import java.util.List;

public class ParticipantDao extends AbstractDao {

    public static final String TABLE = "ec_chimwemwe_participant";

    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
            "  id                   INTEGER PRIMARY KEY AUTOINCREMENT," +
            "  base_entity_id       TEXT," +
            "  last_interacted_with INTEGER," +
            "  delete_status        TEXT," +
            "  participant_id     TEXT," +
            "  group_id             TEXT NOT NULL," +
            "  sn                   INTEGER," +
            "  caregiver_first_name TEXT," +
            "  caregiver_surname    TEXT," +
            "  child_first_name     TEXT," +
            "  child_surname        TEXT," +
            "  child_dob            TEXT," +
            "  child_sex            TEXT," +
            "  enrollment_date      TEXT," +
            "  is_enrolled_ovc      TEXT," +
            "  caregiver_id         TEXT," +
            "  vca_id               TEXT," +
            "  referral_id          TEXT," +
            "  who_is_referred      TEXT," +
            "  provider             TEXT," +
            "  service_being_referred TEXT," +
            "  referral_date        TEXT," +
            "  recieving_organisation TEXT," +
            "  job_title            TEXT," +
            "  full_name_providing_services TEXT," +
            "  referral_status      TEXT," +
            "  service_date         TEXT" +
            ")";

    /** Add group_id column to existing installs that were created without it (DB v35). */
    public static void migrateToV35(SQLiteDatabase db) {
        try {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN group_id TEXT DEFAULT ''");
        } catch (Exception ignored) {
            // Column already exists
        }
    }

    /** Column added in DB version 32 (system-generated UUID for the participant). */
    public static void migrateToV32(SQLiteDatabase db) {
        try {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN participant_id TEXT");
        } catch (Exception ignored) {
            // Column may already exist
        }
    }

    /** Columns added in DB version 31 (referral fields). */
    private static final String[] ALTER_V31 = {
            "ALTER TABLE " + TABLE + " ADD COLUMN referral_id          TEXT",
            "ALTER TABLE " + TABLE + " ADD COLUMN who_is_referred      TEXT",
            "ALTER TABLE " + TABLE + " ADD COLUMN provider             TEXT",
            "ALTER TABLE " + TABLE + " ADD COLUMN service_being_referred TEXT",
            "ALTER TABLE " + TABLE + " ADD COLUMN referral_date        TEXT",
            "ALTER TABLE " + TABLE + " ADD COLUMN recieving_organisation TEXT",
            "ALTER TABLE " + TABLE + " ADD COLUMN job_title            TEXT",
            "ALTER TABLE " + TABLE + " ADD COLUMN full_name_providing_services TEXT",
            "ALTER TABLE " + TABLE + " ADD COLUMN referral_status      TEXT",
            "ALTER TABLE " + TABLE + " ADD COLUMN service_date         TEXT"
    };

    /** Run ALTER TABLE statements to add referral columns on existing installs (DB v31). */
    public static void migrateToV31(SQLiteDatabase db) {
        for (String sql : ALTER_V31) {
            try {
                db.execSQL(sql);
            } catch (Exception ignored) {
                // Column may already exist
            }
        }
    }

    public static void createTable(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_SQL);
    }

    /** OpenSRP standard delete_status column added in DB version 43. */
    public static void migrateToV43(SQLiteDatabase db) {
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN base_entity_id TEXT"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN last_interacted_with INTEGER"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN delete_status TEXT"); } catch (Exception ignored) {}
        try {
            db.execSQL("UPDATE " + TABLE + " SET base_entity_id=participant_id " +
                    "WHERE (base_entity_id IS NULL OR TRIM(base_entity_id)='') " +
                    "AND (participant_id IS NOT NULL AND TRIM(participant_id)!='')");
        } catch (Exception ignored) {}
    }

    /** Date of enrollment captured on the Add Participant form (DB v53). */
    public static void migrateToV53(SQLiteDatabase db) {
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN enrollment_date TEXT"); } catch (Exception ignored) {}
    }

    public static void migrateToV50(SQLiteDatabase db) {
        // Backfill participant_id for rows that were inserted without one (via AddParticipantActivity).
        // Use base_entity_id if already set, otherwise generate a stable id from the row id.
        try {
            db.execSQL("UPDATE " + TABLE +
                    " SET participant_id = COALESCE(NULLIF(TRIM(base_entity_id),''), 'chm-participant-' || id)" +
                    " WHERE (participant_id IS NULL OR TRIM(participant_id) = '')");
        } catch (Exception ignored) {}
    }

    public static long insertParticipant(ParticipantModel m) {
        if (m.getParticipantId() == null || m.getParticipantId().trim().isEmpty()) {
            m.setParticipantId("chm-" + JsonFormUtils.generateRandomUUIDString());
        }
        String sql = "INSERT INTO " + TABLE +
                " (participant_id, group_id, sn, caregiver_first_name, caregiver_surname," +
                "  child_first_name, child_surname, child_dob, child_sex, enrollment_date," +
                "  is_enrolled_ovc, caregiver_id, vca_id," +
                "  referral_id, who_is_referred, provider, service_being_referred, referral_date," +
                "  recieving_organisation, job_title, full_name_providing_services, referral_status, service_date) VALUES (" +
                q(m.getParticipantId()) + "," +
                q(m.getGroupId()) + "," +
                m.getSn() + "," +
                q(m.getCaregiverFirstName()) + "," +
                q(m.getCaregiverSurname()) + "," +
                q(m.getChildFirstName()) + "," +
                q(m.getChildSurname()) + "," +
                q(m.getChildDob()) + "," +
                q(m.getChildSex()) + "," +
                q(m.getEnrollmentDate()) + "," +
                q(m.getIsEnrolledOvc()) + "," +
                q(m.getCaregiverId()) + "," +
                q(m.getVcaId()) + "," +
                q(m.getReferralId()) + "," +
                q(m.getWhoReferred()) + "," +
                q(m.getProvider()) + "," +
                q(m.getServiceReferredFor()) + "," +
                q(m.getReferralDate()) + "," +
                q(m.getReceivingOrg()) + "," +
                q(m.getJobTitle()) + "," +
                q(m.getFullNameProvidingServices()) + "," +
                q(m.getReferralStatus()) + "," +
                q(m.getServiceDate()) + ")";
        AbstractDao.updateDB(sql);
        List<Long> ids = AbstractDao.readData(
                "SELECT id FROM " + TABLE + " WHERE group_id=" + q(m.getGroupId()) +
                " ORDER BY id DESC LIMIT 1",
                cursor -> cursor.getLong(0));
        return (ids != null && !ids.isEmpty()) ? ids.get(0) : -1L;
    }

    public static void updateParticipant(ParticipantModel m) {
        String sql = "UPDATE " + TABLE + " SET " +
                "participant_id="     + q(m.getParticipantId()) + "," +
                "group_id="           + q(m.getGroupId()) + "," +
                "sn="                 + m.getSn() + "," +
                "caregiver_first_name=" + q(m.getCaregiverFirstName()) + "," +
                "caregiver_surname="    + q(m.getCaregiverSurname()) + "," +
                "child_first_name="     + q(m.getChildFirstName()) + "," +
                "child_surname="        + q(m.getChildSurname()) + "," +
                "child_dob="            + q(m.getChildDob()) + "," +
                "child_sex="            + q(m.getChildSex()) + "," +
                "enrollment_date="      + q(m.getEnrollmentDate()) + "," +
                "is_enrolled_ovc="      + q(m.getIsEnrolledOvc()) + "," +
                "caregiver_id="         + q(m.getCaregiverId()) + "," +
                "vca_id="               + q(m.getVcaId()) + "," +
                "who_referred="         + q(m.getWhoReferred()) + "," +
                "service_referred_for=" + q(m.getServiceReferredFor()) + "," +
                "referral_date="        + q(m.getReferralDate()) + "," +
                "receiving_org="        + q(m.getReceivingOrg()) + "," +
                "job_title="            + q(m.getJobTitle()) + "," +
                "service_date="         + q(m.getServiceDate()) +
                " WHERE participant_id=" + q(m.getParticipantId());
        AbstractDao.updateDB(sql);
    }

    public static void updateParticipantById(ParticipantModel m) {
        String sql = "UPDATE " + TABLE + " SET " +
                "participant_id="     + q(m.getParticipantId()) + "," +
                "group_id="           + q(m.getGroupId()) + "," +
                "sn="                 + m.getSn() + "," +
                "caregiver_first_name=" + q(m.getCaregiverFirstName()) + "," +
                "caregiver_surname="    + q(m.getCaregiverSurname()) + "," +
                "child_first_name="     + q(m.getChildFirstName()) + "," +
                "child_surname="        + q(m.getChildSurname()) + "," +
                "child_dob="            + q(m.getChildDob()) + "," +
                "child_sex="            + q(m.getChildSex()) + "," +
                "enrollment_date="      + q(m.getEnrollmentDate()) + "," +
                "is_enrolled_ovc="      + q(m.getIsEnrolledOvc()) + "," +
                "caregiver_id="         + q(m.getCaregiverId()) + "," +
                "vca_id="               + q(m.getVcaId()) + "," +
                "who_referred="         + q(m.getWhoReferred()) + "," +
                "service_referred_for=" + q(m.getServiceReferredFor()) + "," +
                "referral_date="        + q(m.getReferralDate()) + "," +
                "receiving_org="        + q(m.getReceivingOrg()) + "," +
                "job_title="            + q(m.getJobTitle()) + "," +
                "service_date="         + q(m.getServiceDate()) +
                " WHERE id=" + m.getId();
        AbstractDao.updateDB(sql);
    }

    /** Load all participants for a group, ordered by sn. Includes sessions_completed count. */
    public static List<ParticipantModel> getParticipants(String groupId) {
        String sql = "SELECT p.id, p.participant_id, p.group_id, p.sn, p.caregiver_first_name, p.caregiver_surname," +
                "  p.child_first_name, p.child_surname, p.child_dob, p.child_sex," +
                "  p.is_enrolled_ovc, p.caregiver_id, p.vca_id," +
                "  p.who_referred, p.service_referred_for, p.referral_date," +
                "  p.receiving_org, p.job_title, p.service_date, p.enrollment_date," +
                sessionsDoneSelect() +
                " FROM " + TABLE + " p WHERE p.group_id=" + q(groupId) +
                " AND (p.delete_status IS NULL OR p.delete_status <> '1')" +
                " ORDER BY p.sn ASC";
        return AbstractDao.readData(sql, cursor -> {
            ParticipantModel m = mapParticipant(cursor);
            int done = cursor.getInt(20);
            m.setSessionsCompleted(done);
            m.setCompletedProgram(done >= 14);
            return m;
        });
    }

    public static ParticipantModel getParticipant(long id) {
        String sql = "SELECT p.id, p.participant_id, p.group_id, p.sn, p.caregiver_first_name, p.caregiver_surname," +
                " p.child_first_name, p.child_surname, p.child_dob, p.child_sex," +
                " p.is_enrolled_ovc, p.caregiver_id, p.vca_id," +
                " p.who_referred, p.service_referred_for, p.referral_date," +
                " p.receiving_org, p.job_title, p.service_date, p.enrollment_date," +
                sessionsDoneSelect() +
                " FROM " + TABLE + " p WHERE p.id=" + id +
                " AND (p.delete_status IS NULL OR p.delete_status <> '1')";
        List<ParticipantModel> list = AbstractDao.readData(sql, cursor -> {
            ParticipantModel m = mapParticipant(cursor);
            int done = cursor.getInt(20);
            m.setSessionsCompleted(done);
            m.setCompletedProgram(done >= 14);
            return m;
        });
        return (list != null && !list.isEmpty()) ? list.get(0) : null;
    }

    public static ParticipantModel getParticipantByCode(String participantIdCode) {
        if (participantIdCode == null || participantIdCode.trim().isEmpty()) return null;
        String code = participantIdCode.trim();
        String sql = "SELECT p.id, p.participant_id, p.group_id, p.sn, p.caregiver_first_name, p.caregiver_surname," +
                " p.child_first_name, p.child_surname, p.child_dob, p.child_sex," +
                " p.is_enrolled_ovc, p.caregiver_id, p.vca_id," +
                " p.who_referred, p.service_referred_for, p.referral_date," +
                " p.receiving_org, p.job_title, p.service_date, p.enrollment_date," +
                sessionsDoneSelect() +
                " FROM " + TABLE + " p WHERE p.participant_id=" + q(code) +
                " AND (p.delete_status IS NULL OR p.delete_status <> '1')" +
                " ORDER BY p.id DESC LIMIT 1";
        List<ParticipantModel> list = AbstractDao.readData(sql, cursor -> {
            ParticipantModel m = mapParticipant(cursor);
            int done = cursor.getInt(20);
            m.setSessionsCompleted(done);
            m.setCompletedProgram(done >= 14);
            return m;
        });
        return (list != null && !list.isEmpty()) ? list.get(0) : null;
    }

    public static ParticipantModel getParticipantByVcaId(String vcaId) {
        if (vcaId == null || vcaId.trim().isEmpty()) return null;
        String sql = "SELECT p.id, p.participant_id, p.group_id, p.sn, p.caregiver_first_name, p.caregiver_surname," +
                " p.child_first_name, p.child_surname, p.child_dob, p.child_sex," +
                " p.is_enrolled_ovc, p.caregiver_id, p.vca_id," +
                " p.who_referred, p.service_referred_for, p.referral_date," +
                " p.receiving_org, p.job_title, p.service_date, p.enrollment_date," +
                sessionsDoneSelect() +
                " FROM " + TABLE + " p WHERE p.vca_id=" + q(vcaId.trim()) +
                " AND (p.delete_status IS NULL OR p.delete_status <> '1')";
        List<ParticipantModel> list = AbstractDao.readData(sql, cursor -> {
            ParticipantModel m = mapParticipant(cursor);
            int done = cursor.getInt(20);
            m.setSessionsCompleted(done);
            m.setCompletedProgram(done >= 14);
            return m;
        });
        return (list != null && !list.isEmpty()) ? list.get(0) : null;
    }

    private static String sessionsDoneSelect() {
        // Count distinct sessions where the participant has any non-empty attendance recorded
        // in the normalized participant-lines table (no slot limit). Match on p.participant_id
        // (the stable "CHIM-..." business code), not the row PK — OpenSRP's CONFLICT_REPLACE
        // overwrites the participant row's INTEGER id column with a non-numeric base_entity_id,
        // so cursor.getLong(p.id) returns 0 for every participant and the PK is unusable.
        String pidExpr = "p.participant_id";
        StringBuilder sb = new StringBuilder();
        sb.append(" (SELECT COUNT(DISTINCT sap.session_number) FROM ec_chimwemwe_session_attendance_participant sap");
        sb.append("  WHERE sap.group_id=p.group_id AND sap.participant_id=").append(pidExpr);
        sb.append("  AND (sap.delete_status IS NULL OR sap.delete_status <> '1')");
        sb.append("  AND (IFNULL(sap.caregiver_attendance,'')!='' OR IFNULL(sap.child_attendance,'')!='')) AS sessions_done");
        return sb.toString();
    }

    private static ParticipantModel mapParticipant(android.database.Cursor cursor) {
        ParticipantModel m = new ParticipantModel();
        m.setId(cursor.getLong(0));
        m.setParticipantId(cursor.getString(1));
        m.setGroupId(cursor.getString(2));
        m.setSn(cursor.getInt(3));
        m.setCaregiverFirstName(cursor.getString(4));
        m.setCaregiverSurname(cursor.getString(5));
        m.setChildFirstName(cursor.getString(6));
        m.setChildSurname(cursor.getString(7));
        m.setChildDob(cursor.getString(8));
        m.setChildSex(cursor.getString(9));
        m.setIsEnrolledOvc(cursor.getString(10));
        // enrollment_date is appended after service_date (index 19); sessions_done follows at 20.
        m.setEnrollmentDate(cursor.getString(19));
        m.setCaregiverId(cursor.getString(11));
        m.setVcaId(cursor.getString(12));
        m.setWhoReferred(cursor.getString(13));
        m.setServiceReferredFor(cursor.getString(14));
        m.setReferralDate(cursor.getString(15));
        m.setReceivingOrg(cursor.getString(16));
        m.setJobTitle(cursor.getString(17));
        m.setServiceDate(cursor.getString(18));
        return m;
    }

    public static int countParticipants(String groupId) {
        List<Integer> res = AbstractDao.readData(
                "SELECT COUNT(*) FROM " + TABLE + " WHERE group_id=" + q(groupId) +
                        " AND (delete_status IS NULL OR delete_status <> '1')",
                cursor -> cursor.getInt(0));
        return (res != null && !res.isEmpty()) ? res.get(0) : 0;
    }

    /**
     * Child gender tally for the homepage dashboard.
     * Returns a 3-slot array: [0] = male, [1] = female, [2] = unspecified/other (blank or any
     * value that isn't male/female). Soft-deleted participants are excluded.
     */
    public static int[] getChildGenderCounts() {
        int[] counts = new int[3];
        String sql = "SELECT LOWER(TRIM(IFNULL(child_sex,''))) AS g, COUNT(*) AS c FROM " + TABLE +
                " WHERE (delete_status IS NULL OR delete_status <> '1') GROUP BY g";
        List<int[]> rows = AbstractDao.readData(sql, cursor -> {
            String g = cursor.getString(0);
            int c = cursor.getInt(1);
            int bucket = "male".equals(g) ? 0 : ("female".equals(g) ? 1 : 2);
            int[] r = new int[3];
            r[bucket] = c;
            return r;
        });
        if (rows != null) {
            for (int[] r : rows) {
                if (r == null) continue;
                counts[0] += r[0];
                counts[1] += r[1];
                counts[2] += r[2];
            }
        }
        return counts;
    }

    public static int countAllParticipants() {
        List<Integer> res = AbstractDao.readData(
                "SELECT COUNT(*) FROM " + TABLE + " WHERE (delete_status IS NULL OR delete_status <> '1')",
                cursor -> cursor.getInt(0));
        return (res != null && !res.isEmpty()) ? res.get(0) : 0;
    }

    /** All participants across all groups, with sessions_done. */
    public static List<ParticipantModel> getAllParticipantsWithSessions() {
        String sql = "SELECT p.id, p.participant_id, p.group_id, p.sn, p.caregiver_first_name, p.caregiver_surname," +
                "  p.child_first_name, p.child_surname, p.child_dob, p.child_sex," +
                "  p.is_enrolled_ovc, p.caregiver_id, p.vca_id," +
                "  p.who_referred, p.service_referred_for, p.referral_date," +
                "  p.receiving_org, p.job_title, p.service_date, p.enrollment_date," +
                sessionsDoneSelect() +
                " FROM " + TABLE + " p WHERE (p.delete_status IS NULL OR p.delete_status <> '1')" +
                " ORDER BY p.caregiver_surname ASC, p.caregiver_first_name ASC";
        return AbstractDao.readData(sql, cursor -> {
            ParticipantModel m = mapParticipant(cursor);
            int done = cursor.getInt(20);
            m.setSessionsCompleted(done);
            m.setCompletedProgram(done >= 14);
            return m;
        });
    }

    /** Participants who have attended all 14 sessions. */
    public static List<ParticipantModel> getGraduatesWithSessions() {
        String inner = "SELECT p.id, p.participant_id, p.group_id, p.sn, p.caregiver_first_name, p.caregiver_surname," +
                "  p.child_first_name, p.child_surname, p.child_dob, p.child_sex," +
                "  p.is_enrolled_ovc, p.caregiver_id, p.vca_id," +
                "  p.who_referred, p.service_referred_for, p.referral_date," +
                "  p.receiving_org, p.job_title, p.service_date, p.enrollment_date," +
                sessionsDoneSelect() +
                " FROM " + TABLE + " p WHERE (p.delete_status IS NULL OR p.delete_status <> '1')";
        String sql = "SELECT * FROM (" + inner + ") WHERE sessions_done >= 14" +
                " ORDER BY caregiver_surname ASC, caregiver_first_name ASC";
        try {
            return AbstractDao.readData(sql, cursor -> {
                ParticipantModel m = mapParticipant(cursor);
                int done = cursor.getInt(20);
                m.setSessionsCompleted(done);
                m.setCompletedProgram(true);
                return m;
            });
        } catch (Exception e) {
            return new java.util.ArrayList<>();
        }
    }

    public static int countCompletedParticipants() {
        try {
            String sql = "SELECT COUNT(*) FROM " + TABLE + " p" +
                    " WHERE (p.delete_status IS NULL OR p.delete_status <> '1')" +
                    " AND (" + sessionsDoneSelect().replace(" AS sessions_done", "") + ") >= 14";
            List<Integer> res = AbstractDao.readData(sql, cursor -> cursor.getInt(0));
            return (res != null && !res.isEmpty()) ? res.get(0) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /** Returns MAX(sn)+1 for the group, safe against deletions creating duplicate S/N values. */
    public static int nextSn(String groupId) {
        List<Integer> res = AbstractDao.readData(
                "SELECT COALESCE(MAX(sn), 0) + 1 FROM " + TABLE + " WHERE group_id=" + q(groupId) +
                        " AND (delete_status IS NULL OR delete_status <> '1')",
                cursor -> cursor.getInt(0));
        return (res != null && !res.isEmpty()) ? res.get(0) : 1;
    }

    public static void deleteParticipant(long id) {
        // The row PK `id` is unreliable: OpenSRP's CONFLICT_REPLACE overwrites it with the
        // non-numeric base_entity_id, so getId() reads back as 0. Resolve the stable business
        // code and delete by that; only fall back to the raw id when no code can be found.
        ParticipantModel p = null;
        try {
            p = getParticipant(id);
        } catch (Exception ignored) {}

        String code = p != null ? p.getParticipantId() : null;
        if (code != null && !code.trim().isEmpty()) {
            deleteParticipant(code.trim());
        } else {
            AbstractDao.updateDB("UPDATE " + TABLE + " SET delete_status='1' WHERE id=" + id);
        }
    }

    public static void deleteParticipant(String participantCode) {
        if (participantCode == null || participantCode.trim().isEmpty()) return;
        String code = participantCode.trim();

        // Resolve the group so session attendance can be cleared. Match everything on
        // participant_id (the stable business code) — never on the row PK `id`, which OpenSRP
        // corrupts to a non-numeric base_entity_id (getId() returns 0).
        ParticipantModel p = getParticipantByCode(code);
        String groupId = p != null ? p.getGroupId() : null;
        if (groupId != null && !groupId.trim().isEmpty()) {
            SessionAttendanceDao.removeParticipantFromGroupSessions(groupId.trim(), code);
        }

        // Cascade soft-delete to attendance / review / referral child tables.
        AbstractDao.updateDB("UPDATE ec_chimwemwe_session_attendance_participant SET delete_status='1' WHERE participant_id=" + q(code));
        AbstractDao.updateDB("UPDATE ec_chimwemwe_attendance SET delete_status='1' WHERE participant_id=" + q(code));
        AbstractDao.updateDB("UPDATE ec_chimwemwe_review   SET delete_status='1' WHERE participant_id=" + q(code));
        AbstractDao.updateDB("UPDATE ec_chimwemwe_referral SET delete_status='1' WHERE participant_id=" + q(code));

        AbstractDao.updateDB("UPDATE " + TABLE + " SET delete_status='1' WHERE participant_id=" + q(code));
    }

    /** Soft-delete every participant whose parent group name does not match keepGroupName. */
    public static int purgeParticipantsExceptByGroupName(String keepGroupName) {
        String keep = keepGroupName != null ? keepGroupName.trim() : "";
        String sql = "SELECT p.participant_id FROM " + TABLE + " p " +
                "LEFT JOIN ec_chimwemwe_group g ON g.group_id = p.group_id " +
                "WHERE (p.delete_status IS NULL OR p.delete_status <> '1')";
        if (!keep.isEmpty()) {
            sql += " AND (g.group_name IS NULL OR TRIM(g.group_name) <> " + q(keep) + ")";
        }
        List<String> codes = AbstractDao.readData(sql, cursor -> cursor.getString(0));
        int deleted = 0;
        if (codes == null) return 0;
        for (String code : codes) {
            if (code == null || code.trim().isEmpty()) continue;
            deleteParticipant(code.trim());
            deleted++;
        }
        return deleted;
    }

    /** Soft-delete all participants across all groups. */
    public static int purgeAllParticipants() {
        int updated = 0;
        try {
            AbstractDao.updateDB("UPDATE " + TABLE + " SET delete_status='1' WHERE (delete_status IS NULL OR delete_status <> '1')");
            AbstractDao.updateDB("UPDATE ec_chimwemwe_session_attendance_participant SET delete_status='1' WHERE (delete_status IS NULL OR delete_status <> '1')");
            AbstractDao.updateDB("UPDATE ec_chimwemwe_attendance SET delete_status='1' WHERE (delete_status IS NULL OR delete_status <> '1')");
            AbstractDao.updateDB("UPDATE ec_chimwemwe_review SET delete_status='1' WHERE (delete_status IS NULL OR delete_status <> '1')");
            AbstractDao.updateDB("UPDATE ec_chimwemwe_referral SET delete_status='1' WHERE (delete_status IS NULL OR delete_status <> '1')");
            updated = countAllParticipants();
        } catch (Exception ignored) {}
        return updated;
    }

    private static String q(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "''") + "'";
    }
}
