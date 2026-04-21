package com.bluecodeltd.ecap.chw.dao;

import com.bluecodeltd.ecap.chw.model.ChimwemweReferralModel;

import net.sqlcipher.database.SQLiteDatabase;

import org.smartregister.dao.AbstractDao;

import java.time.LocalDate;
import java.util.List;

public class ChimwemweReferralDao extends AbstractDao {

    public static final String TABLE = "ec_chimwemwe_referral";

    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
            "  id                    INTEGER PRIMARY KEY AUTOINCREMENT," +
            "  participant_id        INTEGER NOT NULL," +
            "  group_id              INTEGER NOT NULL," +
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

    public static void insertReferral(ChimwemweReferralModel m) {
        String sql = "INSERT INTO " + TABLE +
                " (participant_id, group_id, who_referred, service_referred_for, referral_date, receiving_org, job_title, service_date, created_at)" +
                " VALUES (" +
                m.getParticipantId() + "," +
                m.getGroupId() + "," +
                q(m.getWhoReferred()) + "," +
                q(m.getServiceReferredFor()) + "," +
                q(m.getReferralDate()) + "," +
                q(m.getReceivingOrg()) + "," +
                q(m.getJobTitle()) + "," +
                q(m.getServiceDate()) + "," +
                q(LocalDate.now().toString()) + ")";
        AbstractDao.updateDB(sql);
    }

    public static List<ChimwemweReferralModel> getParticipantReferrals(long participantId) {
        String sql = "SELECT id, participant_id, group_id, who_referred, service_referred_for," +
                " referral_date, receiving_org, job_title, service_date, created_at FROM " + TABLE +
                " WHERE participant_id=" + participantId + " ORDER BY id DESC";
        return AbstractDao.readData(sql, cursor -> {
            ChimwemweReferralModel m = new ChimwemweReferralModel();
            m.setId(cursor.getLong(0));
            m.setParticipantId(cursor.getLong(1));
            m.setGroupId(cursor.getLong(2));
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

    private static String q(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "''") + "'";
    }
}
