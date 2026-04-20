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
                "hotspot_name, group_name, created_date, " +
                "(SELECT COUNT(*) FROM ec_chimwemwe_participant WHERE group_id=ec_chimwemwe_group.id) AS p_count, " +
                "(SELECT COUNT(DISTINCT session_number) FROM ec_chimwemwe_attendance WHERE group_id=ec_chimwemwe_group.id AND (caregiver_attendance!='' OR child_attendance!='')) AS s_count, " +
                "'' AS relationalid, '' AS sync_status " +
                "FROM ec_chimwemwe_group";
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
