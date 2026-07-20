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
    private String referral_id;
    private String who_is_referred;
    private String provider;
    private String service_being_referred;
    private String referral_date;
    private String recieving_organisation;
    private String job_title;
    private String full_name_providing_services;
    private String referral_status;
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

    public String getReferral_id() { return referral_id; }
    public void setReferral_id(String referral_id) { this.referral_id = referral_id; }
    public String getReferralId() { return referral_id; }
    public void setReferralId(String referralId) { this.referral_id = referralId; }

    public String getWho_is_referred() { return who_is_referred; }
    public void setWho_is_referred(String who_is_referred) { this.who_is_referred = who_is_referred; }
    public String getWho_referred() { return who_is_referred; }
    public void setWho_referred(String v) { this.who_is_referred = v; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getService_being_referred() { return service_being_referred; }
    public void setService_being_referred(String service_being_referred) { this.service_being_referred = service_being_referred; }
    public String getService_referred_for() { return service_being_referred; }
    public void setService_referred_for(String v) { this.service_being_referred = v; }

    public String getReferral_date() { return referral_date; }
    public void setReferral_date(String referral_date) { this.referral_date = referral_date; }

    public String getRecieving_organisation() { return recieving_organisation; }
    public void setRecieving_organisation(String recieving_organisation) { this.recieving_organisation = recieving_organisation; }
    public String getReceiving_org() { return recieving_organisation; }
    public void setReceiving_org(String v) { this.recieving_organisation = v; }

    public String getJob_title() { return job_title; }
    public void setJob_title(String job_title) { this.job_title = job_title; }

    public String getFull_name_providing_services() { return full_name_providing_services; }
    public void setFull_name_providing_services(String full_name_providing_services) { this.full_name_providing_services = full_name_providing_services; }

    public String getReferral_status() { return referral_status; }
    public void setReferral_status(String referral_status) { this.referral_status = referral_status; }

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

    public String getWhoReferred() { return getWho_is_referred(); }
    public void setWhoReferred(String v) { setWho_is_referred(v); }

    public String getServiceReferredFor() { return getService_being_referred(); }
    public void setServiceReferredFor(String v) { setService_being_referred(v); }

    public String getReferralDate() { return getReferral_date(); }
    public void setReferralDate(String v) { setReferral_date(v); }

    public String getReceivingOrg() { return getRecieving_organisation(); }
    public void setReceivingOrg(String v) { setRecieving_organisation(v); }

    public String getJobTitle() { return getJob_title(); }
    public void setJobTitle(String v) { setJob_title(v); }

    public String getServiceDate() { return getService_date(); }
    public void setServiceDate(String v) { setService_date(v); }

    public String getCreatedAt() { return getCreated_at(); }
    public void setCreatedAt(String v) { setCreated_at(v); }
}

