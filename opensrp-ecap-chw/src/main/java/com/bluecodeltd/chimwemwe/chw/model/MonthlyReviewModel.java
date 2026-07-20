package com.bluecodeltd.chimwemwe.chw.model;

public class MonthlyReviewModel {

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
    private String supervisor_signature;
    private String supervisor_gps;
    private String created_at;

    public String getBase_entity_id() { return base_entity_id; }
    public void setBase_entity_id(String v) { this.base_entity_id = v; }

    public String getLast_interacted_with() { return last_interacted_with; }
    public void setLast_interacted_with(String v) { this.last_interacted_with = v; }

    public String getDelete_status() { return delete_status; }
    public void setDelete_status(String v) { this.delete_status = v; }

    public String getGroup_id() { return group_id; }
    public void setGroup_id(String v) { this.group_id = v; }

    public String getParticipant_id() { return participant_id; }
    public void setParticipant_id(String v) { this.participant_id = v; }

    public String getReview_quarter() { return review_quarter; }
    public void setReview_quarter(String v) { this.review_quarter = v; }

    public String getReview_date() { return review_date; }
    public void setReview_date(String v) { this.review_date = v; }

    public String getReviewer_name() { return reviewer_name; }
    public void setReviewer_name(String v) { this.reviewer_name = v; }

    public String getRegister_accurate() { return register_accurate; }
    public void setRegister_accurate(String v) { this.register_accurate = v; }

    public String getReviewer_notes() { return reviewer_notes; }
    public void setReviewer_notes(String v) { this.reviewer_notes = v; }

    public String getSupervisor_signature() { return supervisor_signature; }
    public void setSupervisor_signature(String v) { this.supervisor_signature = v; }

    public String getSupervisor_gps() { return supervisor_gps; }
    public void setSupervisor_gps(String v) { this.supervisor_gps = v; }

    public String getCreated_at() { return created_at; }
    public void setCreated_at(String v) { this.created_at = v; }

    // --- Backward-compatible camelCase helpers ---

    public String getBaseEntityId() { return getBase_entity_id(); }
    public void setBaseEntityId(String v) { setBase_entity_id(v); }

    public String getDeleteStatus() { return getDelete_status(); }
    public void setDeleteStatus(String v) { setDelete_status(v); }

    public String getGroupId() { return getGroup_id(); }
    public void setGroupId(String v) { setGroup_id(v); }

    public String getParticipantId() { return getParticipant_id(); }
    public void setParticipantId(String v) { setParticipant_id(v); }

    public String getReviewQuarter() { return getReview_quarter(); }
    public void setReviewQuarter(String v) { setReview_quarter(v); }

    public String getReviewDate() { return getReview_date(); }
    public void setReviewDate(String v) { setReview_date(v); }

    public String getReviewerName() { return getReviewer_name(); }
    public void setReviewerName(String v) { setReviewer_name(v); }

    public String getRegisterAccurate() { return getRegister_accurate(); }
    public void setRegisterAccurate(String v) { setRegister_accurate(v); }

    public String getReviewerNotes() { return getReviewer_notes(); }
    public void setReviewerNotes(String v) { setReviewer_notes(v); }

    public String getSupervisorSignature() { return getSupervisor_signature(); }
    public void setSupervisorSignature(String v) { setSupervisor_signature(v); }

    public String getSupervisorGps() { return getSupervisor_gps(); }
    public void setSupervisorGps(String v) { setSupervisor_gps(v); }

    public String getCreatedAt() { return getCreated_at(); }
    public void setCreatedAt(String v) { setCreated_at(v); }
}
