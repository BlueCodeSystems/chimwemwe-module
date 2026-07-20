package com.bluecodeltd.chimwemwe.chw.dao;

import com.bluecodeltd.chimwemwe.chw.model.ChimwemweReferralModel;

import net.sqlcipher.database.SQLiteDatabase;

import org.smartregister.dao.AbstractDao;

import java.util.List;

public class ChimwemweReferralDao extends AbstractDao {

    public static final String TABLE = "ec_chimwemwe_referral";

    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
            "  base_entity_id        TEXT PRIMARY KEY," +
            "  last_interacted_with  INTEGER," +
            "  delete_status         TEXT," +
            "  participant_id        TEXT," +
            "  group_id              TEXT," +
            "  referral_id           TEXT," +
            "  who_is_referred       TEXT," +
            "  provider              TEXT," +
            "  service_being_referred TEXT," +
            "  referral_date         TEXT," +
            "  recieving_organisation TEXT," +
            "  job_title             TEXT," +
            "  full_name_providing_services TEXT," +
            "  referral_status       TEXT," +
            "  service_date          TEXT," +
            "  created_at            TEXT" +
            ")";

    public static void createTable(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_SQL);
    }

    public static void migrateToV44(SQLiteDatabase db) {
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN base_entity_id TEXT"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN last_interacted_with INTEGER"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN delete_status TEXT"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN referral_id TEXT"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN who_is_referred TEXT"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN provider TEXT"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN service_being_referred TEXT"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN recieving_organisation TEXT"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN full_name_providing_services TEXT"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN referral_status TEXT"); } catch (Exception ignored) {}
        try {
            db.execSQL("UPDATE " + TABLE + " SET base_entity_id='chimwemwe-referral-' || rowid " +
                    "WHERE (base_entity_id IS NULL OR TRIM(base_entity_id)='')");
        } catch (Exception ignored) {}
    }


    public static void migrateToV54(SQLiteDatabase db) {
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN base_entity_id TEXT"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN last_interacted_with INTEGER"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN delete_status TEXT"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN participant_id TEXT"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN group_id TEXT"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN referral_id TEXT"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN who_is_referred TEXT"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN provider TEXT"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN service_being_referred TEXT"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN referral_date TEXT"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN recieving_organisation TEXT"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN job_title TEXT"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN full_name_providing_services TEXT"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN referral_status TEXT"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN service_date TEXT"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN created_at TEXT"); } catch (Exception ignored) {}
        try {
            db.execSQL("UPDATE " + TABLE + " SET base_entity_id='chimwemwe-referral-' || rowid " +
                    "WHERE (base_entity_id IS NULL OR TRIM(base_entity_id)='')");
        } catch (Exception ignored) {}
    }

    public static List<ChimwemweReferralModel> getParticipantReferrals(String participantId) {
        String sql = "SELECT * FROM ec_chimwemwe_referral" +
                " WHERE participant_id = '" + participantId + "'" +
                " AND (delete_status IS NULL OR delete_status <> '1')" +
                " ORDER BY last_interacted_with DESC";
        return AbstractDao.readData(sql, getMap());
    }

    public static List<ChimwemweReferralModel> getGroupReferrals(String groupId) {
        if (groupId == null || groupId.trim().isEmpty()) return new java.util.ArrayList<>();
        String sql = "SELECT * FROM ec_chimwemwe_referral" +
                " WHERE group_id = '" + groupId.replace("'", "''") + "'" +
                " AND (delete_status IS NULL OR delete_status <> '1')" +
                " ORDER BY last_interacted_with DESC";
        List<ChimwemweReferralModel> values = AbstractDao.readData(sql, getMap());
        return values != null ? values : new java.util.ArrayList<>();
    }

    public static int countParticipantReferrals(String participantId) {
        String sql = "SELECT COUNT(*) FROM " + TABLE +
                " WHERE participant_id = '" + participantId.replace("'", "''") + "'" +
                " AND (delete_status IS NULL OR delete_status <> '1')";
        List<Integer> result = AbstractDao.readData(sql, c -> c.getInt(0));
        return (result != null && !result.isEmpty()) ? result.get(0) : 0;
    }

    public static void deleteReferral(String baseEntityId) {
        if (baseEntityId == null || baseEntityId.trim().isEmpty()) return;
        AbstractDao.updateDB(
                "UPDATE ec_chimwemwe_referral SET delete_status='1' WHERE base_entity_id='"
                        + baseEntityId.replace("'", "''") + "'");
    }

    public static DataMap<ChimwemweReferralModel> getMap() {
        return c -> {
            ChimwemweReferralModel record = new ChimwemweReferralModel();
            record.setBase_entity_id(getCursorValue(c, "base_entity_id"));
            record.setLast_interacted_with(getCursorValue(c, "last_interacted_with"));
            record.setDelete_status(getCursorValue(c, "delete_status"));
            record.setParticipant_id(getCursorValue(c, "participant_id"));
            record.setGroup_id(getCursorValue(c, "group_id"));
            record.setReferral_id(getCursorValue(c, "referral_id"));
            record.setWho_is_referred(getCursorValue(c, "who_is_referred"));
            record.setProvider(getCursorValue(c, "provider"));
            record.setService_being_referred(getCursorValue(c, "service_being_referred"));
            record.setReferral_date(getCursorValue(c, "referral_date"));
            record.setRecieving_organisation(getCursorValue(c, "recieving_organisation"));
            record.setJob_title(getCursorValue(c, "job_title"));
            record.setFull_name_providing_services(getCursorValue(c, "full_name_providing_services"));
            record.setReferral_status(getCursorValue(c, "referral_status"));
            record.setService_date(getCursorValue(c, "service_date"));
            record.setCreated_at(getCursorValue(c, "created_at"));
            return record;
        };
    }
}
