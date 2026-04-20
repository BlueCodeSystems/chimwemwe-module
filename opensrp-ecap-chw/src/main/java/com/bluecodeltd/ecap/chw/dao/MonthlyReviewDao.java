package com.bluecodeltd.ecap.chw.dao;

import com.bluecodeltd.ecap.chw.model.MonthlyReviewModel;

import net.sqlcipher.database.SQLiteDatabase;

import org.smartregister.dao.AbstractDao;

import java.time.LocalDate;
import java.util.List;

public class MonthlyReviewDao extends AbstractDao {

    public static final String TABLE = "ec_chimwemwe_review";

    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
            "  id                INTEGER PRIMARY KEY AUTOINCREMENT," +
            "  group_id          INTEGER NOT NULL," +
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
        } catch (Exception ignored) {
            // Column may already exist
        }
    }

    public static void createTable(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_SQL);
    }

    public static void insertReview(MonthlyReviewModel m) {
        String sql = "INSERT INTO " + TABLE +
                " (group_id, review_quarter, review_date, reviewer_name, register_accurate, reviewer_notes, created_at)" +
                " VALUES (" +
                m.getGroupId() + "," +
                q(m.getReviewQuarter()) + "," +
                q(m.getReviewDate()) + "," +
                q(m.getReviewerName()) + "," +
                q(m.getRegisterAccurate()) + "," +
                q(m.getReviewerNotes()) + "," +
                q(LocalDate.now().toString()) + ")";
        AbstractDao.updateDB(sql);
    }

    /** Returns all reviews for a group, most recent first. */
    public static List<MonthlyReviewModel> getReviews(long groupId) {
        String sql = "SELECT id, group_id, review_quarter, review_date, reviewer_name, register_accurate," +
                " reviewer_notes, created_at FROM " + TABLE +
                " WHERE group_id=" + groupId + " ORDER BY id DESC";
        return AbstractDao.readData(sql, cursor -> {
            MonthlyReviewModel m = new MonthlyReviewModel();
            m.setId(cursor.getLong(0));
            m.setGroupId(cursor.getLong(1));
            m.setReviewQuarter(cursor.getString(2));
            m.setReviewDate(cursor.getString(3));
            m.setReviewerName(cursor.getString(4));
            m.setRegisterAccurate(cursor.getString(5));
            m.setReviewerNotes(cursor.getString(6));
            m.setCreatedAt(cursor.getString(7));
            return m;
        });
    }

    private static String q(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "''") + "'";
    }
}
