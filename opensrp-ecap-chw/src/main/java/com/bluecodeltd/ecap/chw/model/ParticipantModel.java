package com.bluecodeltd.ecap.chw.model;

public class ParticipantModel {
    private long id;
    private String participantCode;  // system-generated UUID
    private long groupId;
    private int sn;
    private String caregiverFirstName;
    private String caregiverSurname;
    private String childFirstName;
    private String childSurname;
    private String childDob;
    private String childSex;
    private String isEnrolledOvc;
    private String caregiverId;
    private String vcaId;

    // Referral fields
    private String whoReferred;
    private String serviceReferredFor;
    private String referralDate;
    private String receivingOrg;
    private String jobTitle;
    private String serviceDate;

    // Computed
    private int sessionsCompleted;
    private boolean completedProgram;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getParticipantCode()                      { return participantCode; }
    public void   setParticipantCode(String participantCode) { this.participantCode = participantCode; }

    public long getGroupId() { return groupId; }
    public void setGroupId(long groupId) { this.groupId = groupId; }

    public int getSn() { return sn; }
    public void setSn(int sn) { this.sn = sn; }

    public String getCaregiverFirstName() { return caregiverFirstName; }
    public void setCaregiverFirstName(String v) { this.caregiverFirstName = v; }

    public String getCaregiverSurname() { return caregiverSurname; }
    public void setCaregiverSurname(String v) { this.caregiverSurname = v; }

    public String getChildFirstName() { return childFirstName; }
    public void setChildFirstName(String v) { this.childFirstName = v; }

    public String getChildSurname() { return childSurname; }
    public void setChildSurname(String v) { this.childSurname = v; }

    public String getChildDob() { return childDob; }
    public void setChildDob(String v) { this.childDob = v; }

    public String getChildSex() { return childSex; }
    public void setChildSex(String v) { this.childSex = v; }

    public String getIsEnrolledOvc() { return isEnrolledOvc; }
    public void setIsEnrolledOvc(String v) { this.isEnrolledOvc = v; }

    public String getCaregiverId() { return caregiverId; }
    public void setCaregiverId(String v) { this.caregiverId = v; }

    public String getVcaId() { return vcaId; }
    public void setVcaId(String v) { this.vcaId = v; }

    public String getWhoReferred()                    { return whoReferred; }
    public void   setWhoReferred(String v)             { this.whoReferred = v; }

    public String getServiceReferredFor()             { return serviceReferredFor; }
    public void   setServiceReferredFor(String v)      { this.serviceReferredFor = v; }

    public String getReferralDate()                   { return referralDate; }
    public void   setReferralDate(String v)            { this.referralDate = v; }

    public String getReceivingOrg()                   { return receivingOrg; }
    public void   setReceivingOrg(String v)            { this.receivingOrg = v; }

    public String getJobTitle()                       { return jobTitle; }
    public void   setJobTitle(String v)                { this.jobTitle = v; }

    public String getServiceDate()                    { return serviceDate; }
    public void   setServiceDate(String v)             { this.serviceDate = v; }

    public int getSessionsCompleted() { return sessionsCompleted; }
    public void setSessionsCompleted(int v) { this.sessionsCompleted = v; }

    public boolean isCompletedProgram() { return completedProgram; }
    public void setCompletedProgram(boolean v) { this.completedProgram = v; }

    public String getCaregiverFullName() {
        String fn = caregiverFirstName != null ? caregiverFirstName : "";
        String sn = caregiverSurname != null ? caregiverSurname : "";
        return (fn + " " + sn).trim();
    }

    public String getChildFullName() {
        String fn = childFirstName != null ? childFirstName : "";
        String sn = childSurname != null ? childSurname : "";
        return (fn + " " + sn).trim();
    }
}
