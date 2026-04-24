package com.bluecodeltd.ecap.chw.model;

/**
 * Records caregiver and child attendance for one participant in one session.
 * attendance values: "Group", "Home Visit", or "" (absent / not recorded).
 */
public class AttendanceModel {
    public static final String GROUP      = "Group";
    public static final String HOME_VISIT = "Home Visit";
    public static final String ABSENT     = "";

    private long id;
    // Business identifier from ec_chimwemwe_group.group_id
    private String groupId;
    private long participantId;
    private int  sessionNumber;   // 1–14
    private String sessionDate;   // DD/MM/YY
    private String caregiverAttendance;
    private String childAttendance;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public long getParticipantId() { return participantId; }
    public void setParticipantId(long participantId) { this.participantId = participantId; }

    public int getSessionNumber() { return sessionNumber; }
    public void setSessionNumber(int sessionNumber) { this.sessionNumber = sessionNumber; }

    public String getSessionDate() { return sessionDate; }
    public void setSessionDate(String sessionDate) { this.sessionDate = sessionDate; }

    public String getCaregiverAttendance() { return caregiverAttendance; }
    public void setCaregiverAttendance(String v) { this.caregiverAttendance = v; }

    public String getChildAttendance() { return childAttendance; }
    public void setChildAttendance(String v) { this.childAttendance = v; }
}
