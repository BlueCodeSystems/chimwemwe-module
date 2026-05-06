package com.bluecodeltd.chimwemwe.chw.dao;

import com.bluecodeltd.chimwemwe.chw.model.MonthlyReviewModel;

import net.sqlcipher.database.SQLiteDatabase;

import org.smartregister.dao.AbstractDao;

import java.time.LocalDate;
import java.util.List;

public class MonthlyReviewDao extends AbstractDao {

    public static final String TABLE = "ec_chimwemwe_review";

    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
            "  id                INTEGER PRIMARY KEY AUTOINCREMENT," +
            "  base_entity_id    TEXT," +
            "  last_interacted_with INTEGER," +
            "  delete_status     TEXT," +
            "  group_id          TEXT NOT NULL," +
            "  participant_id    INTEGER," +
            "  review_quarter    TEXT," +
            "  review_date       TEXT," +
            "  reviewer_name     TEXT," +
            "  register_accurate TEXT," +
            "  reviewer_notes    TEXT," +
            "  created_at        TEXT" +
            ")";

    /** Column added in DB version 31 (review_quarter). */
    public static void migrateToV31(SQLiteDatabase db) {
        try {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN review_quarter TEXT");
        } catch (Exception ignored) {}
    }

    /** Column added in DB version 33 (participant_id). */
    public static void migrateToV33(SQLiteDatabase db) {
        try {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN participant_id INTEGER");
        } catch (Exception ignored) {}
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
            db.execSQL("UPDATE " + TABLE + " SET base_entity_id='chimwemwe-review-' || id " +
                    "WHERE (base_entity_id IS NULL OR TRIM(base_entity_id)='')");
        } catch (Exception ignored) {}
    }

    public static long insertReview(MonthlyReviewModel m) {
        String sql = "INSERT INTO " + TABLE +
                " (group_id, participant_id, review_quarter, review_date, reviewer_name, register_accurate, reviewer_notes, created_at)" +
                " VALUES (" +
                q(m.getGroupId()) + "," +
                m.getParticipantId() + "," +
                q(m.getReviewQuarter()) + "," +
                q(m.getReviewDate()) + "," +
                q(m.getReviewerName()) + "," +
                q(m.getRegisterAccurate()) + "," +
                q(m.getReviewerNotes()) + "," +
                q(LocalDate.now().toString()) + ")";
        AbstractDao.updateDB(sql);
        List<Long> ids = AbstractDao.readData(
                "SELECT id FROM " + TABLE + " ORDER BY id DESC LIMIT 1",
                cursor -> cursor.getLong(0));
        long id = (ids != null && !ids.isEmpty()) ? ids.get(0) : -1L;
        if (id > 0) {
            AbstractDao.updateDB("UPDATE " + TABLE + " SET base_entity_id=" + q("chimwemwe-review-" + id) +
                    " WHERE id=" + id);
        }
        return id;
    }

    /** Returns all reviews for a group, most recent first. */
    public static List<MonthlyReviewModel> getReviews(String groupId) {
        String sql = "SELECT id, group_id, participant_id, review_quarter, review_date, reviewer_name, register_accurate," +
                " reviewer_notes, created_at FROM " + TABLE +
                " WHERE group_id=" + q(groupId) +
                " AND (delete_status IS NULL OR delete_status <> '1')" +
                " ORDER BY id DESC";
        return AbstractDao.readData(sql, MonthlyReviewDao::mapRow);
    }

    /** Returns all reviews for a specific participant, most recent first. */
    public static List<MonthlyReviewModel> getParticipantReviews(long participantId) {
        String sql = "SELECT id, group_id, participant_id, review_quarter, review_date, reviewer_name, register_accurate," +
                " reviewer_notes, created_at FROM " + TABLE +
                " WHERE participant_id=" + participantId +
                " AND (delete_status IS NULL OR delete_status <> '1')" +
                " ORDER BY id DESC";
        return AbstractDao.readData(sql, MonthlyReviewDao::mapRow);
    }

    public static void updateReview(MonthlyReviewModel m) {
        String sql = "UPDATE " + TABLE + " SET " +
                "review_quarter="    + q(m.getReviewQuarter()) + "," +
                "review_date="       + q(m.getReviewDate()) + "," +
                "reviewer_name="     + q(m.getReviewerName()) + "," +
                "register_accurate=" + q(m.getRegisterAccurate()) + "," +
                "reviewer_notes="    + q(m.getReviewerNotes()) +
                " WHERE id=" + m.getId();
        AbstractDao.updateDB(sql);
    }

    public static void deleteReview(long id) {
        if (id <= 0) return;
        AbstractDao.updateDB("UPDATE " + TABLE + " SET delete_status='1' WHERE id=" + id);
    }

    private static MonthlyReviewModel mapRow(android.database.Cursor cursor) {
        MonthlyReviewModel m = new MonthlyReviewModel();
        m.setId(cursor.getLong(0));
        m.setGroupId(cursor.getString(1));
        m.setParticipantId(cursor.getLong(2));
        m.setReviewQuarter(cursor.getString(3));
        m.setReviewDate(cursor.getString(4));
        m.setReviewerName(cursor.getString(5));
        m.setRegisterAccurate(cursor.getString(6));
        m.setReviewerNotes(cursor.getString(7));
        m.setCreatedAt(cursor.getString(8));
        return m;
    }

    private static String q(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "''") + "'";
    }
}
