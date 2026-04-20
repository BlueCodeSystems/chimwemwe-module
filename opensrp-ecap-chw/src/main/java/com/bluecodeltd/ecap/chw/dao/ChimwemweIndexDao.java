package com.bluecodeltd.ecap.chw.dao;

import com.bluecodeltd.ecap.chw.model.ChimwemweIndexModel;

import net.sqlcipher.database.SQLiteDatabase;

import org.smartregister.dao.AbstractDao;

import java.util.List;

/**
 * Data access object for the ec_chimwemwe_index table.
 * This table stores client records retrieved from the remote OpenSRP database
 * via the Advanced Search feature.
 */
public class ChimwemweIndexDao extends AbstractDao {

    public static final String TABLE_NAME = "ec_chimwemwe_index";

    public static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "remote_id TEXT, " +
            "first_name TEXT, " +
            "last_name TEXT, " +
            "gender TEXT, " +
            "birthdate TEXT, " +
            "unique_id TEXT, " +
            "phone TEXT, " +
            "sub_population TEXT, " +
            "facility TEXT, " +
            "province TEXT, " +
            "district TEXT, " +
            "case_status TEXT, " +
            "source TEXT, " +
            "date_added TEXT" +
            ")";

    /**
     * Create the ec_chimwemwe_index table if it does not yet exist.
     * Safe to call on every app start.
     */
    public static void createTable(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_SQL);
    }

    /**
     * Insert a ChimwemweIndexModel record.
     * If a record with the same remote_id already exists it will be skipped.
     */
    public static boolean saveRecord(ChimwemweIndexModel model) {
        try {
            // Only insert if remote_id not already saved
            String checkSql = "SELECT id FROM " + TABLE_NAME +
                    " WHERE remote_id = '" + escape(model.getRemoteId()) + "' LIMIT 1";
            AbstractDao.DataMap<Long> idMap = c -> c.getLong(0);
            List<Long> existing = AbstractDao.readData(checkSql, idMap);
            if (existing != null && !existing.isEmpty() && existing.get(0) != null) {
                return false; // already saved
            }

            String insertSql = "INSERT INTO " + TABLE_NAME +
                    " (remote_id, first_name, last_name, gender, birthdate, unique_id, phone," +
                    " sub_population, facility, province, district, case_status, source, date_added)" +
                    " VALUES ('" + escape(model.getRemoteId()) + "','" +
                    escape(model.getFirstName()) + "','" +
                    escape(model.getLastName()) + "','" +
                    escape(model.getGender()) + "','" +
                    escape(model.getBirthdate()) + "','" +
                    escape(model.getUniqueId()) + "','" +
                    escape(model.getPhone()) + "','" +
                    escape(model.getSubPopulation()) + "','" +
                    escape(model.getFacility()) + "','" +
                    escape(model.getProvince()) + "','" +
                    escape(model.getDistrict()) + "','" +
                    escape(model.getCaseStatus()) + "','" +
                    escape(model.getSource()) + "','" +
                    escape(model.getDateAdded()) + "')";

            AbstractDao.updateDB(insertSql);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Escape single quotes to prevent SQL errors. */
    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("'", "''");
    }

    /**
     * Return all records saved in ec_chimwemwe_index ordered by date_added descending.
     */
    public static List<ChimwemweIndexModel> getAllRecords() {
        String sql = "SELECT * FROM " + TABLE_NAME + " ORDER BY date_added DESC";
        AbstractDao.DataMap<ChimwemweIndexModel> dataMap = c -> {
            ChimwemweIndexModel m = new ChimwemweIndexModel();
            m.setId(c.getLong(c.getColumnIndex("id")));
            m.setRemoteId(getCursorValue(c, "remote_id"));
            m.setFirstName(getCursorValue(c, "first_name"));
            m.setLastName(getCursorValue(c, "last_name"));
            m.setGender(getCursorValue(c, "gender"));
            m.setBirthdate(getCursorValue(c, "birthdate"));
            m.setUniqueId(getCursorValue(c, "unique_id"));
            m.setPhone(getCursorValue(c, "phone"));
            m.setSubPopulation(getCursorValue(c, "sub_population"));
            m.setFacility(getCursorValue(c, "facility"));
            m.setProvince(getCursorValue(c, "province"));
            m.setDistrict(getCursorValue(c, "district"));
            m.setCaseStatus(getCursorValue(c, "case_status"));
            m.setSource(getCursorValue(c, "source"));
            m.setDateAdded(getCursorValue(c, "date_added"));
            return m;
        };
        return AbstractDao.readData(sql, dataMap);
    }

    /**
     * Count total records in the chimwemwe index.
     */
    public static int countRecords() {
        String sql = "SELECT COUNT(*) FROM " + TABLE_NAME;
        AbstractDao.DataMap<Integer> dataMap = c -> c.getInt(0);
        List<Integer> result = AbstractDao.readData(sql, dataMap);
        return (result != null && !result.isEmpty() && result.get(0) != null) ? result.get(0) : 0;
    }

    /**
     * Delete a record by its local id.
     */
    public static void deleteRecord(long id) {
        String sql = "DELETE FROM " + TABLE_NAME + " WHERE id = " + id;
        AbstractDao.updateDB(sql);
    }
}
