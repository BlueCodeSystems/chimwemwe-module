package com.bluecodeltd.chimwemwe.chw.dao;

import com.bluecodeltd.chimwemwe.chw.model.ChimwemweReferralModel;

import net.sqlcipher.database.SQLiteDatabase;

import org.smartregister.dao.AbstractDao;

import java.time.LocalDate;
import java.util.List;

public class ChimwemweReferralDao extends AbstractDao {

    public static final String TABLE = "ec_chimwemwe_referral";

    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
            "  id                    INTEGER PRIMARY KEY AUTOINCREMENT," +
            "  base_entity_id        TEXT," +
            "  last_interacted_with  INTEGER," +
            "  delete_status         TEXT," +
            "  participant_id        INTEGER NOT NULL," +
            "  group_id              TEXT NOT NULL," +
            "  who_referred          TEXT," +
            "  service_referred_for  TEXT," +
            "  referral_date         TEXT," +
            "  receiving_org         TEXT," +
            "  job_title             TEXT," +
            "  service_date          TEXT," +
            "  created_at            TEXT" +
            ")";

    public static void createTable(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_SQL);
    }

    /** OpenSRP standard delete_status column added in DB version 43. */
    public static void migrateToV43(SQLiteDatabase db) {
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN base_entity_id TEXT"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN last_interacted_with INTEGER"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN delete_status TEXT"); } catch (Exception ignored) {}
        try {
            db.execSQL("UPDATE " + TABLE + " SET base_entity_id='chimwemwe-referral-' || id " +
                    "WHERE (base_entity_id IS NULL OR TRIM(base_entity_id)='')");
        } catch (Exception ignored) {}
    }

    public static long insertReferral(ChimwemweReferralModel m) {
        String sql = "INSERT INTO " + TABLE +
                " (participant_id, group_id, who_referred, service_referred_for, referral_date, receiving_org, job_title, service_date, created_at)" +
                " VALUES (" +
                m.getParticipantId() + "," +
                q(m.getGroupId()) + "," +
                q(m.getWhoReferred()) + "," +
                q(m.getServiceReferredFor()) + "," +
                q(m.getReferralDate()) + "," +
                q(m.getReceivingOrg()) + "," +
                q(m.getJobTitle()) + "," +
                q(m.getServiceDate()) + "," +
                q(LocalDate.now().toString()) + ")";
        AbstractDao.updateDB(sql);
        List<Long> ids = AbstractDao.readData(
                "SELECT id FROM " + TABLE + " ORDER BY id DESC LIMIT 1",
                cursor -> cursor.getLong(0));
        long id = (ids != null && !ids.isEmpty()) ? ids.get(0) : -1L;
        if (id > 0) {
            AbstractDao.updateDB("UPDATE " + TABLE + " SET base_entity_id=" + q("chimwemwe-referral-" + id) +
                    " WHERE id=" + id);
        }
        return id;
    }

    public static List<ChimwemweReferralModel> getParticipantReferrals(long participantId) {
        String sql = "SELECT id, participant_id, group_id, who_referred, service_referred_for," +
                " referral_date, receiving_org, job_title, service_date, created_at FROM " + TABLE +
                " WHERE participant_id=" + participantId +
                " AND (delete_status IS NULL OR delete_status <> '1')" +
                " ORDER BY id DESC";
        return AbstractDao.readData(sql, cursor -> {
            ChimwemweReferralModel m = new ChimwemweReferralModel();
            m.setId(cursor.getLong(0));
            m.setParticipantId(cursor.getLong(1));
            m.setGroupId(cursor.getString(2));
            m.setWhoReferred(cursor.getString(3));
            m.setServiceReferredFor(cursor.getString(4));
            m.setReferralDate(cursor.getString(5));
            m.setReceivingOrg(cursor.getString(6));
            m.setJobTitle(cursor.getString(7));
            m.setServiceDate(cursor.getString(8));
            m.setCreatedAt(cursor.getString(9));
            return m;
        });
    }

    public static void updateReferral(ChimwemweReferralModel m) {
        String sql = "UPDATE " + TABLE + " SET " +
                "who_referred="         + q(m.getWhoReferred()) + "," +
                "service_referred_for=" + q(m.getServiceReferredFor()) + "," +
                "referral_date="        + q(m.getReferralDate()) + "," +
                "receiving_org="        + q(m.getReceivingOrg()) + "," +
                "job_title="            + q(m.getJobTitle()) + "," +
                "service_date="         + q(m.getServiceDate()) +
                " WHERE id=" + m.getId();
        AbstractDao.updateDB(sql);
    }

    public static void deleteReferral(long id) {
        if (id <= 0) return;
        AbstractDao.updateDB("UPDATE " + TABLE + " SET delete_status='1' WHERE id=" + id);
    }

    private static String q(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "''") + "'";
    }
}
