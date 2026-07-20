package com.bluecodeltd.chimwemwe.chw.model;

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
    // Business identifier from ec_chimwemwe_participant.participant_id (e.g. "CHIM-1234567890").
    // Stored as a String because the OpenSRP form processor writes participants with a non-numeric
    // base_entity_id, which clobbers the row's `id` INTEGER PK with a string value — so the row
    // PK can't be used to identify a participant from Java.
    private String participantId;
    private int  sessionNumber;   // 1–14
    private String sessionDate;   // DD/MM/YY
    private String caregiverAttendance;
    private String childAttendance;
    private String caregiverSignature;
    private String caregiverGps;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public String getParticipantId() { return participantId; }
    public void setParticipantId(String participantId) { this.participantId = participantId; }

    public int getSessionNumber() { return sessionNumber; }
    public void setSessionNumber(int sessionNumber) { this.sessionNumber = sessionNumber; }

    public String getSessionDate() { return sessionDate; }
    public void setSessionDate(String sessionDate) { this.sessionDate = sessionDate; }

    public String getCaregiverAttendance() { return caregiverAttendance; }
    public void setCaregiverAttendance(String v) { this.caregiverAttendance = v; }

    public String getChildAttendance() { return childAttendance; }
    public void setChildAttendance(String v) { this.childAttendance = v; }

    public String getCaregiverSignature() { return caregiverSignature; }
    public void setCaregiverSignature(String caregiverSignature) { this.caregiverSignature = caregiverSignature; }

    public String getCaregiverGps() { return caregiverGps; }
    public void setCaregiverGps(String caregiverGps) { this.caregiverGps = caregiverGps; }
}
