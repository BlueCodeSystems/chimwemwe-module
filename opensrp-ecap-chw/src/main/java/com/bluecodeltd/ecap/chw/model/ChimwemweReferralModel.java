package com.bluecodeltd.ecap.chw.model;

public class ChimwemweReferralModel {
    private long   id;
    private long   participantId;
    // Business identifier from ec_chimwemwe_group.group_id
    private String groupId;
    private String whoReferred;
    private String serviceReferredFor;
    private String referralDate;
    private String receivingOrg;
    private String jobTitle;
    private String serviceDate;
    private String createdAt;

    public long getId()               { return id; }
    public void setId(long id)        { this.id = id; }

    public long getParticipantId()                   { return participantId; }
    public void setParticipantId(long participantId) { this.participantId = participantId; }

    public String getGroupId()               { return groupId; }
    public void setGroupId(String groupId)   { this.groupId = groupId; }

    public String getWhoReferred()                  { return whoReferred; }
    public void   setWhoReferred(String v)           { this.whoReferred = v; }

    public String getServiceReferredFor()           { return serviceReferredFor; }
    public void   setServiceReferredFor(String v)    { this.serviceReferredFor = v; }

    public String getReferralDate()                 { return referralDate; }
    public void   setReferralDate(String v)          { this.referralDate = v; }

    public String getReceivingOrg()                 { return receivingOrg; }
    public void   setReceivingOrg(String v)          { this.receivingOrg = v; }

    public String getJobTitle()                     { return jobTitle; }
    public void   setJobTitle(String v)              { this.jobTitle = v; }

    public String getServiceDate()                  { return serviceDate; }
    public void   setServiceDate(String v)           { this.serviceDate = v; }

    public String getCreatedAt()                    { return createdAt; }
    public void   setCreatedAt(String v)             { this.createdAt = v; }
}
