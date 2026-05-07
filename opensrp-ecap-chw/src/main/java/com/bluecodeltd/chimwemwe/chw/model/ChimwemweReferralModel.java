package com.bluecodeltd.chimwemwe.chw.model;

/**
 * Referral record model aligned to columns in {@code ec_chimwemwe_referral}.
 * Uses snake_case field names to match DB column names (similar to HTSlinksModel).
 */
public class ChimwemweReferralModel {


    private String base_entity_id;
    private String last_interacted_with;
    private String delete_status;

    // Business linkage (can be stored as TEXT or INTEGER in SQLite depending on legacy data)
    private String participant_id;
    private String group_id;

    // Referral fields
    private String who_referred;
    private String service_referred_for;
    private String referral_date;
    private String receiving_org;
    private String job_title;
    private String service_date;
    private String created_at;



    public String getBase_entity_id() { return base_entity_id; }
    public void setBase_entity_id(String base_entity_id) { this.base_entity_id = base_entity_id; }

    public String getLast_interacted_with() { return last_interacted_with; }
    public void setLast_interacted_with(String last_interacted_with) { this.last_interacted_with = last_interacted_with; }

    public String getDelete_status() { return delete_status; }
    public void setDelete_status(String delete_status) { this.delete_status = delete_status; }

    public String getParticipant_id() { return participant_id; }
    public void setParticipant_id(String participant_id) { this.participant_id = participant_id; }

    public String getGroup_id() { return group_id; }
    public void setGroup_id(String group_id) { this.group_id = group_id; }

    public String getWho_referred() { return who_referred; }
    public void setWho_referred(String who_referred) { this.who_referred = who_referred; }

    public String getService_referred_for() { return service_referred_for; }
    public void setService_referred_for(String service_referred_for) { this.service_referred_for = service_referred_for; }

    public String getReferral_date() { return referral_date; }
    public void setReferral_date(String referral_date) { this.referral_date = referral_date; }

    public String getReceiving_org() { return receiving_org; }
    public void setReceiving_org(String receiving_org) { this.receiving_org = receiving_org; }

    public String getJob_title() { return job_title; }
    public void setJob_title(String job_title) { this.job_title = job_title; }

    public String getService_date() { return service_date; }
    public void setService_date(String service_date) { this.service_date = service_date; }

    public String getCreated_at() { return created_at; }
    public void setCreated_at(String created_at) { this.created_at = created_at; }

    // --- Backward-compatible camelCase helpers (existing UI code uses these) ---

    public String getBaseEntityId() { return getBase_entity_id(); }
    public void setBaseEntityId(String v) { setBase_entity_id(v); }

    public String getDeleteStatus() { return getDelete_status(); }
    public void setDeleteStatus(String v) { setDelete_status(v); }

    public String getParticipantId() { return getParticipant_id(); }
    public void setParticipantId(String v) { setParticipant_id(v); }

    public String getGroupId() { return getGroup_id(); }
    public void setGroupId(String v) { setGroup_id(v); }

    public String getWhoReferred() { return getWho_referred(); }
    public void setWhoReferred(String v) { setWho_referred(v); }

    public String getServiceReferredFor() { return getService_referred_for(); }
    public void setServiceReferredFor(String v) { setService_referred_for(v); }

    public String getReferralDate() { return getReferral_date(); }
    public void setReferralDate(String v) { setReferral_date(v); }

    public String getReceivingOrg() { return getReceiving_org(); }
    public void setReceivingOrg(String v) { setReceiving_org(v); }

    public String getJobTitle() { return getJob_title(); }
    public void setJobTitle(String v) { setJob_title(v); }

    public String getServiceDate() { return getService_date(); }
    public void setServiceDate(String v) { setService_date(v); }

    public String getCreatedAt() { return getCreated_at(); }
    public void setCreatedAt(String v) { setCreated_at(v); }
}

