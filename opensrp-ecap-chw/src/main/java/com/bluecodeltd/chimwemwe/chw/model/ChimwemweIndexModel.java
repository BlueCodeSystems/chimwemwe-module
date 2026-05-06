package com.bluecodeltd.chimwemwe.chw.model;

/**
 * Model representing a record in ec_chimwemwe_index —
 * populated either from the local DB or from a remote (OpenSRP) search result.
 */
public class ChimwemweIndexModel {

    private long id;
    private String remoteId;
    private String firstName;
    private String lastName;
    private String gender;
    private String birthdate;
    private String uniqueId;
    private String phone;
    private String subPopulation;
    private String facility;
    private String province;
    private String district;
    private String caseStatus;
    private String source;      // "remote" or "local"
    private String dateAdded;
    private String householdId;
    private String caregiverName;

    public ChimwemweIndexModel() {}

    // Full constructor
    public ChimwemweIndexModel(String remoteId, String firstName, String lastName,
                                String gender, String birthdate, String uniqueId,
                                String phone, String subPopulation, String facility,
                                String province, String district, String caseStatus,
                                String source, String dateAdded) {
        this.remoteId = remoteId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.gender = gender;
        this.birthdate = birthdate;
        this.uniqueId = uniqueId;
        this.phone = phone;
        this.subPopulation = subPopulation;
        this.facility = facility;
        this.province = province;
        this.district = district;
        this.caseStatus = caseStatus;
        this.source = source;
        this.dateAdded = dateAdded;
    }

    public String getFullName() {
        String f = firstName != null ? firstName : "";
        String l = lastName != null ? lastName : "";
        return (f + " " + l).trim();
    }

    public String getAvatarInitial() {
        if (firstName != null && !firstName.isEmpty()) {
            return String.valueOf(firstName.charAt(0)).toUpperCase();
        }
        return "?";
    }

    // ---- Getters & Setters ----

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getRemoteId() { return remoteId; }
    public void setRemoteId(String remoteId) { this.remoteId = remoteId; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getBirthdate() { return birthdate; }
    public void setBirthdate(String birthdate) { this.birthdate = birthdate; }

    public String getUniqueId() { return uniqueId; }
    public void setUniqueId(String uniqueId) { this.uniqueId = uniqueId; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getSubPopulation() { return subPopulation; }
    public void setSubPopulation(String subPopulation) { this.subPopulation = subPopulation; }

    public String getFacility() { return facility; }
    public void setFacility(String facility) { this.facility = facility; }

    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }

    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }

    public String getCaseStatus() { return caseStatus; }
    public void setCaseStatus(String caseStatus) { this.caseStatus = caseStatus; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getDateAdded() { return dateAdded; }
    public void setDateAdded(String dateAdded) { this.dateAdded = dateAdded; }

    public String getHouseholdId() { return householdId; }
    public void setHouseholdId(String householdId) { this.householdId = householdId; }

    public String getCaregiverName() { return caregiverName; }
    public void setCaregiverName(String caregiverName) { this.caregiverName = caregiverName; }
}
