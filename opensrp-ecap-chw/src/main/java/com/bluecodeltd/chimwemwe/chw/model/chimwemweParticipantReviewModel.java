package com.bluecodeltd.chimwemwe.chw.model;

/**
 * Participant review model aligned to columns in {@code ec_chimwemwe_review}.
 * Uses snake_case field names to match DB column names (similar to HTSlinksModel).
 *
 * Note: Class name follows existing project naming request (lowercase leading char).
 */
public class chimwemweParticipantReviewModel {

    private String base_entity_id;
    private String last_interacted_with;
    private String delete_status;
    private String group_id;
    private String participant_id;
    private String review_quarter;
    private String review_date;
    private String reviewer_name;
    private String register_accurate;
    private String reviewer_notes;
    private String created_at;

    public String getBase_entity_id() {
        return base_entity_id;
    }

    public void setBase_entity_id(String base_entity_id) {
        this.base_entity_id = base_entity_id;
    }

    public String getLast_interacted_with() {
        return last_interacted_with;
    }

    public void setLast_interacted_with(String last_interacted_with) {
        this.last_interacted_with = last_interacted_with;
    }

    public String getDelete_status() {
        return delete_status;
    }

    public void setDelete_status(String delete_status) {
        this.delete_status = delete_status;
    }

    public String getGroup_id() {
        return group_id;
    }

    public void setGroup_id(String group_id) {
        this.group_id = group_id;
    }

    public String getParticipant_id() {
        return participant_id;
    }

    public void setParticipant_id(String participant_id) {
        this.participant_id = participant_id;
    }

    public String getReview_quarter() {
        return review_quarter;
    }

    public void setReview_quarter(String review_quarter) {
        this.review_quarter = review_quarter;
    }

    public String getReview_date() {
        return review_date;
    }

    public void setReview_date(String review_date) {
        this.review_date = review_date;
    }

    public String getReviewer_name() {
        return reviewer_name;
    }

    public void setReviewer_name(String reviewer_name) {
        this.reviewer_name = reviewer_name;
    }

    public String getRegister_accurate() {
        return register_accurate;
    }

    public void setRegister_accurate(String register_accurate) {
        this.register_accurate = register_accurate;
    }

    public String getReviewer_notes() {
        return reviewer_notes;
    }

    public void setReviewer_notes(String reviewer_notes) {
        this.reviewer_notes = reviewer_notes;
    }

    public String getCreated_at() {
        return created_at;
    }

    public void setCreated_at(String created_at) {
        this.created_at = created_at;
    }

    private final java.util.Map<String, String> additionalFields = new java.util.HashMap<>();

    public java.util.Map<String, String> getAdditionalFields() {
        return additionalFields;
    }

    public String getAdditionalField(String key) {
        if (key == null) return null;
        return additionalFields.get(key);
    }

    public void setAdditionalField(String key, String value) {
        if (key == null) return;
        additionalFields.put(key, value);
    }
}
