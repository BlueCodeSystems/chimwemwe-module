package com.bluecodeltd.chimwemwe.chw.dao;

import com.bluecodeltd.chimwemwe.chw.model.MonthlyReviewModel;

import net.sqlcipher.database.SQLiteDatabase;

import org.smartregister.dao.AbstractDao;

import java.util.List;

public class MonthlyReviewDao extends AbstractDao {

    public static final String TABLE = "ec_chimwemwe_review";

    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
            "  base_entity_id        TEXT PRIMARY KEY," +
            "  last_interacted_with  INTEGER," +
            "  delete_status         TEXT," +
            "  group_id              TEXT," +
            "  participant_id        TEXT," +
            "  review_quarter        TEXT," +
            "  review_date           TEXT," +
            "  reviewer_name         TEXT," +
            "  register_accurate     TEXT," +
            "  reviewer_notes        TEXT," +
            "  created_at            TEXT" +
            ")";

    public static void createTable(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_SQL);
    }

    public static void migrateToV31(SQLiteDatabase db) {
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN review_quarter TEXT"); } catch (Exception ignored) {}
    }

    public static void migrateToV33(SQLiteDatabase db) {
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN participant_id TEXT"); } catch (Exception ignored) {}
    }

    public static void migrateToV43(SQLiteDatabase db) {
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN base_entity_id TEXT"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN last_interacted_with INTEGER"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN delete_status TEXT"); } catch (Exception ignored) {}
        try {
            db.execSQL("UPDATE " + TABLE + " SET base_entity_id='chimwemwe-review-' || rowid " +
                    "WHERE (base_entity_id IS NULL OR TRIM(base_entity_id)='')");
        } catch (Exception ignored) {}
    }

    public static List<MonthlyReviewModel> getParticipantReviews(String participantCode) {
        String sql = "SELECT * FROM " + TABLE +
                " WHERE participant_id = '" + participantCode.replace("'", "''") + "'" +
                " AND (delete_status IS NULL OR delete_status <> '1')" +
                " ORDER BY last_interacted_with DESC";
        return AbstractDao.readData(sql, getMap());
    }

    public static List<MonthlyReviewModel> getReviews(String groupId) {
        String sql = "SELECT * FROM " + TABLE +
                " WHERE group_id = '" + groupId.replace("'", "''") + "'" +
                " AND (delete_status IS NULL OR delete_status <> '1')" +
                " ORDER BY last_interacted_with DESC";
        return AbstractDao.readData(sql, getMap());
    }

    public static int countParticipantReviews(String participantCode) {
        String sql = "SELECT COUNT(*) FROM " + TABLE +
                " WHERE participant_id = '" + participantCode.replace("'", "''") + "'" +
                " AND (delete_status IS NULL OR delete_status <> '1')";
        List<Integer> result = AbstractDao.readData(sql, c -> c.getInt(0));
        return (result != null && !result.isEmpty()) ? result.get(0) : 0;
    }

    public static void deleteReview(String baseEntityId) {
        if (baseEntityId == null || baseEntityId.trim().isEmpty()) return;
        AbstractDao.updateDB(
                "UPDATE " + TABLE + " SET delete_status='1' WHERE base_entity_id='"
                        + baseEntityId.replace("'", "''") + "'");
    }

    public static DataMap<MonthlyReviewModel> getMap() {
        return c -> {
            MonthlyReviewModel record = new MonthlyReviewModel();
            record.setBase_entity_id(getCursorValue(c, "base_entity_id"));
            record.setLast_interacted_with(getCursorValue(c, "last_interacted_with"));
            record.setDelete_status(getCursorValue(c, "delete_status"));
            record.setGroup_id(getCursorValue(c, "group_id"));
            record.setParticipant_id(getCursorValue(c, "participant_id"));
            record.setReview_quarter(getCursorValue(c, "review_quarter"));
            record.setReview_date(getCursorValue(c, "review_date"));
            record.setReviewer_name(getCursorValue(c, "reviewer_name"));
            record.setRegister_accurate(getCursorValue(c, "register_accurate"));
            record.setReviewer_notes(getCursorValue(c, "reviewer_notes"));
            record.setCreated_at(getCursorValue(c, "created_at"));
            return record;
        };
    }
}
