package com.bluecodeltd.ecap.chw.presenter;

import com.bluecodeltd.ecap.chw.contract.ChimwemweRegisterFragmentContract;

public class ChimwemweRegisterPresenter implements ChimwemweRegisterFragmentContract.Presenter {

    private ChimwemweRegisterFragmentContract.View view;

    @Override
    public void initView(ChimwemweRegisterFragmentContract.View view) {
        this.view = view;
    }

    @Override
    public ChimwemweRegisterFragmentContract.View getView() {
        return view;
    }

    @Override
    public void processViewConfigurations() {}

    @Override
    public void initializeQueries(String s) {
        String table = "ec_chimwemwe_group";
        String countSelect = "SELECT COUNT(*) FROM " + table;
        String mainSelect =
                "SELECT ec_chimwemwe_group.id AS _id, " +
                "ec_chimwemwe_group.id AS id, " +
                "ec_chimwemwe_group.group_id AS group_id, " +
                "ec_chimwemwe_group.hotspot_name AS hotspot_name, " +
                "ec_chimwemwe_group.group_name AS group_name, " +
                "ec_chimwemwe_group.group_code AS group_code, " +
                "ec_chimwemwe_group.created_date AS created_date, " +
                "COALESCE(participant_counts.p_count, 0) AS p_count, " +
                "COALESCE(attendance_counts.s_count, 0) AS s_count, " +
                "'' AS relationalid, '' AS sync_status " +
                "FROM ec_chimwemwe_group " +
                "LEFT JOIN (" +
                "SELECT group_id, COUNT(*) AS p_count " +
                "FROM ec_chimwemwe_participant GROUP BY group_id" +
                ") AS participant_counts " +
                "ON participant_counts.group_id = ec_chimwemwe_group.id " +
                "LEFT JOIN (" +
                "SELECT group_id, " +
                "COUNT(DISTINCT CASE " +
                "WHEN caregiver_attendance!='' OR child_attendance!='' THEN session_number " +
                "END) AS s_count " +
                "FROM ec_chimwemwe_attendance GROUP BY group_id" +
                ") AS attendance_counts " +
                "ON attendance_counts.group_id = ec_chimwemwe_group.id";
        getView().initializeQueryParams(table, countSelect, mainSelect);
        getView().initializeAdapter();
        getView().countExecute();
        getView().filterandSortInInitializeQueries();
    }

    @Override
    public void startSync() {}

    @Override
    public void searchGlobally(String s) {}
}
