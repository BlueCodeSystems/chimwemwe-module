package com.bluecodeltd.chimwemwe.chw.model;

public class MonthlyReviewModel {

    private long   id;
    // Business identifier from ec_chimwemwe_group.group_id
    private String groupId;
    private long   participantId;
    private String reviewQuarter;
    private String reviewDate;
    private String reviewerName;
    private String registerAccurate;
    private String reviewerNotes;
    private String createdAt;

    public long getId()                   { return id; }
    public void setId(long id)            { this.id = id; }

    public String getGroupId()              { return groupId; }
    public void setGroupId(String groupId)  { this.groupId = groupId; }

    public long getParticipantId()                   { return participantId; }
    public void setParticipantId(long participantId) { this.participantId = participantId; }

    public String getReviewQuarter()                      { return reviewQuarter; }
    public void   setReviewQuarter(String reviewQuarter)  { this.reviewQuarter = reviewQuarter; }

    public String getReviewDate()                      { return reviewDate; }
    public void   setReviewDate(String reviewDate)     { this.reviewDate = reviewDate; }

    public String getReviewerName()                    { return reviewerName; }
    public void   setReviewerName(String reviewerName) { this.reviewerName = reviewerName; }

    public String getRegisterAccurate()                        { return registerAccurate; }
    public void   setRegisterAccurate(String registerAccurate) { this.registerAccurate = registerAccurate; }

    public String getReviewerNotes()                     { return reviewerNotes; }
    public void   setReviewerNotes(String reviewerNotes) { this.reviewerNotes = reviewerNotes; }

    public String getCreatedAt()                   { return createdAt; }
    public void   setCreatedAt(String createdAt)   { this.createdAt = createdAt; }
}
