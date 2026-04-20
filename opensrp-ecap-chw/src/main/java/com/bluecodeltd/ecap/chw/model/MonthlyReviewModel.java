package com.bluecodeltd.ecap.chw.model;

public class MonthlyReviewModel {

    private long   id;
    private long   groupId;
    private String reviewQuarter;
    private String reviewDate;
    private String reviewerName;
    private String registerAccurate;
    private String reviewerNotes;
    private String createdAt;

    public long getId()                   { return id; }
    public void setId(long id)            { this.id = id; }

    public long getGroupId()              { return groupId; }
    public void setGroupId(long groupId)  { this.groupId = groupId; }

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
