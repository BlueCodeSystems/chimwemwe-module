package com.bluecodeltd.chimwemwe.chw.util;

import com.bluecodeltd.chimwemwe.chw.model.MonthlyReviewModel;

import org.json.JSONObject;

public final class ReviewFormPrefill {

    private ReviewFormPrefill() {
    }

    public static void populate(JSONObject form, MonthlyReviewModel review) {
        if (form == null || review == null) {
            return;
        }

        ChimwemweFormUtils.ensureFieldValue(form, "group_id", review.getGroup_id());
        ChimwemweFormUtils.ensureFieldValue(form, "participant_id", review.getParticipant_id());
        ChimwemweFormUtils.ensureFieldValue(form, "review_quarter", review.getReview_quarter());
        ChimwemweFormUtils.ensureFieldValue(form, "review_date", DateFormValueUtils.normalizeForDatePicker(review.getReview_date()));
        ChimwemweFormUtils.ensureFieldValue(form, "reviewer_name", review.getReviewer_name());
        ChimwemweFormUtils.ensureFieldValue(form, "register_accurate", review.getRegister_accurate());
        ChimwemweFormUtils.ensureFieldValue(form, "reviewer_notes", review.getReviewer_notes());
        // A fresh mandatory sign-off replaces these values on save. Prefilling the Base64
        // signature also forces JsonWizard to lay out a very large hidden text value.
    }
}
