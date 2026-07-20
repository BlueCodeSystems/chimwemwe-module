package com.bluecodeltd.chimwemwe.chw.util;

import com.bluecodeltd.chimwemwe.chw.model.ChimwemweReferralModel;

import org.json.JSONObject;

public final class ReferralFormPrefill {

    private ReferralFormPrefill() {
    }

    public static void populate(JSONObject form, ChimwemweReferralModel referral) {
        if (form == null || referral == null) {
            return;
        }

        ChimwemweFormUtils.ensureFieldValue(form, "referral_id", firstNonBlank(referral.getReferral_id(), referral.getBase_entity_id()));
        ChimwemweFormUtils.ensureFieldValue(form, "participant_id", referral.getParticipant_id());
        ChimwemweFormUtils.ensureFieldValue(form, "group_id", referral.getGroup_id());
        ChimwemweFormUtils.ensureFieldValue(form, "who_is_referred", referral.getWho_is_referred());
        ChimwemweFormUtils.ensureFieldValue(form, "provider", referral.getProvider());
        ChimwemweFormUtils.ensureFieldValue(form, "referral_date", DateFormValueUtils.normalizeForDatePicker(referral.getReferral_date()));
        ChimwemweFormUtils.ensureFieldValue(form, "service_being_referred", referral.getService_being_referred());
        ChimwemweFormUtils.ensureFieldValue(form, "recieving_organisation", referral.getRecieving_organisation());
        ChimwemweFormUtils.ensureFieldValue(form, "job_title", referral.getJob_title());
        ChimwemweFormUtils.ensureFieldValue(form, "full_name_providing_services", referral.getFull_name_providing_services());
        ChimwemweFormUtils.ensureFieldValue(form, "referral_status", referral.getReferral_status());
        ChimwemweFormUtils.ensureFieldValue(form, "service_date", DateFormValueUtils.normalizeForDatePicker(referral.getService_date()));
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first;
        }
        return second;
    }
}
