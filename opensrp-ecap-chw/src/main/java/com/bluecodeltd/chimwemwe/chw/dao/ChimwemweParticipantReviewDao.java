package com.bluecodeltd.chimwemwe.chw.dao;

import com.bluecodeltd.chimwemwe.chw.model.chimwemweParticipantReviewModel;

import org.smartregister.dao.AbstractDao;

import java.util.ArrayList;
import java.util.List;

public class ChimwemweParticipantReviewDao extends AbstractDao {

    public static final String TABLE = "ec_chimwemwe_review";

    public static int countParticipantReviews(String participantCode) {
        if (participantCode == null) return 0;

        String sql = "SELECT COUNT(*) AS reviewCount FROM " + TABLE +
                " WHERE participant_id = '" + participantCode.replace("'", "''") + "'" +
                " AND (delete_status IS NULL OR delete_status <> '1')";

        List<Integer> result = AbstractDao.readData(sql, c -> c.getInt(0));
        return (result != null && !result.isEmpty()) ? result.get(0) : 0;
    }

    public static List<chimwemweParticipantReviewModel> getParticipantReviews(String participantCode) {
        if (participantCode == null) return new ArrayList<>();

        String sql = "SELECT * FROM " + TABLE +
                " WHERE participant_id = '" + participantCode.replace("'", "''") + "'" +
                " AND (delete_status IS NULL OR delete_status <> '1')" +
                " ORDER BY last_interacted_with DESC";

        List<chimwemweParticipantReviewModel> values = AbstractDao.readData(sql, getMap());
        if (values == null || values.isEmpty()) return new ArrayList<>();
        return values;
    }

    public static chimwemweParticipantReviewModel getLatestParticipantReview(String participantCode) {
        List<chimwemweParticipantReviewModel> values = getParticipantReviews(participantCode);
        return values.isEmpty() ? null : values.get(0);
    }

    public static void deleteReview(String baseEntityId) {
        if (baseEntityId == null || baseEntityId.trim().isEmpty()) return;
        AbstractDao.updateDB(
                "UPDATE " + TABLE + " SET delete_status='1' WHERE base_entity_id='"
                        + baseEntityId.replace("'", "''") + "'");
    }

    public static DataMap<chimwemweParticipantReviewModel> getMap() {
        return c -> {
            chimwemweParticipantReviewModel record = new chimwemweParticipantReviewModel();
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

            DaoModelFieldMapper.captureAdditionalFields(c, record);
            return record;
        };
    }
}
