package com.bluecodeltd.ecap.chw.model;

public class HotspotGroupModel {

    private long   id;
    private String groupCode;   // system-generated UUID
    private String hotspotName;
    private String groupName;
    private String createdDate;

    // Enrollment form — Step 1
    private String province;
    private String district;
    private String locationOfSession;
    private String locationGps;
    private String nearestHealthFacility;

    // Enrollment form — Step 2 (facilitators)
    private String facilitator1FirstName;
    private String facilitator1Surname;
    private String facilitator2FirstName;
    private String facilitator2Surname;

    // Enrollment form — Step 3 (planned session dates 1–14)
    private String session1Date;
    private String session2Date;
    private String session3Date;
    private String session4Date;
    private String session5Date;
    private String session6Date;
    private String session7Date;
    private String session8Date;
    private String session9Date;
    private String session10Date;
    private String session11Date;
    private String session12Date;
    private String session13Date;
    private String session14Date;

    // Computed fields (not stored in DB)
    private int participantCount;
    private int sessionsRecorded;

    // ── Core ─────────────────────────────────────────────────

    public long getId()                  { return id; }
    public void setId(long id)           { this.id = id; }

    public String getGroupCode()                   { return groupCode; }
    public void   setGroupCode(String groupCode)   { this.groupCode = groupCode; }

    public String getHotspotName()                     { return hotspotName; }
    public void   setHotspotName(String hotspotName)   { this.hotspotName = hotspotName; }

    public String getGroupName()                   { return groupName; }
    public void   setGroupName(String groupName)   { this.groupName = groupName; }

    public String getCreatedDate()                   { return createdDate; }
    public void   setCreatedDate(String createdDate) { this.createdDate = createdDate; }

    // ── Step 1 ───────────────────────────────────────────────

    public String getProvince()                   { return province; }
    public void   setProvince(String province)     { this.province = province; }

    public String getDistrict()                   { return district; }
    public void   setDistrict(String district)     { this.district = district; }

    public String getLocationOfSession()                         { return locationOfSession; }
    public void   setLocationOfSession(String locationOfSession) { this.locationOfSession = locationOfSession; }

    public String getLocationGps()                   { return locationGps; }
    public void   setLocationGps(String locationGps) { this.locationGps = locationGps; }

    public String getNearestHealthFacility()                             { return nearestHealthFacility; }
    public void   setNearestHealthFacility(String nearestHealthFacility) { this.nearestHealthFacility = nearestHealthFacility; }

    // ── Step 2 ───────────────────────────────────────────────

    public String getFacilitator1FirstName()                               { return facilitator1FirstName; }
    public void   setFacilitator1FirstName(String facilitator1FirstName)   { this.facilitator1FirstName = facilitator1FirstName; }

    public String getFacilitator1Surname()                             { return facilitator1Surname; }
    public void   setFacilitator1Surname(String facilitator1Surname)   { this.facilitator1Surname = facilitator1Surname; }

    public String getFacilitator2FirstName()                               { return facilitator2FirstName; }
    public void   setFacilitator2FirstName(String facilitator2FirstName)   { this.facilitator2FirstName = facilitator2FirstName; }

    public String getFacilitator2Surname()                             { return facilitator2Surname; }
    public void   setFacilitator2Surname(String facilitator2Surname)   { this.facilitator2Surname = facilitator2Surname; }

    // ── Step 3 ───────────────────────────────────────────────

    public String getSession1Date()                      { return session1Date; }
    public void   setSession1Date(String session1Date)   { this.session1Date = session1Date; }

    public String getSession2Date()                      { return session2Date; }
    public void   setSession2Date(String session2Date)   { this.session2Date = session2Date; }

    public String getSession3Date()                      { return session3Date; }
    public void   setSession3Date(String session3Date)   { this.session3Date = session3Date; }

    public String getSession4Date()                      { return session4Date; }
    public void   setSession4Date(String session4Date)   { this.session4Date = session4Date; }

    public String getSession5Date()                      { return session5Date; }
    public void   setSession5Date(String session5Date)   { this.session5Date = session5Date; }

    public String getSession6Date()                      { return session6Date; }
    public void   setSession6Date(String session6Date)   { this.session6Date = session6Date; }

    public String getSession7Date()                      { return session7Date; }
    public void   setSession7Date(String session7Date)   { this.session7Date = session7Date; }

    public String getSession8Date()                      { return session8Date; }
    public void   setSession8Date(String session8Date)   { this.session8Date = session8Date; }

    public String getSession9Date()                      { return session9Date; }
    public void   setSession9Date(String session9Date)   { this.session9Date = session9Date; }

    public String getSession10Date()                       { return session10Date; }
    public void   setSession10Date(String session10Date)   { this.session10Date = session10Date; }

    public String getSession11Date()                       { return session11Date; }
    public void   setSession11Date(String session11Date)   { this.session11Date = session11Date; }

    public String getSession12Date()                       { return session12Date; }
    public void   setSession12Date(String session12Date)   { this.session12Date = session12Date; }

    public String getSession13Date()                       { return session13Date; }
    public void   setSession13Date(String session13Date)   { this.session13Date = session13Date; }

    public String getSession14Date()                       { return session14Date; }
    public void   setSession14Date(String session14Date)   { this.session14Date = session14Date; }

    // ── Computed ─────────────────────────────────────────────

    public int getParticipantCount()                       { return participantCount; }
    public void setParticipantCount(int participantCount)  { this.participantCount = participantCount; }

    public int getSessionsRecorded()                       { return sessionsRecorded; }
    public void setSessionsRecorded(int sessionsRecorded)  { this.sessionsRecorded = sessionsRecorded; }
}
