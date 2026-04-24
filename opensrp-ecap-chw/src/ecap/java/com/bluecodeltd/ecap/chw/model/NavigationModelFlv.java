package com.bluecodeltd.ecap.chw.model;

import com.bluecodeltd.ecap.chw.R;
import com.bluecodeltd.ecap.chw.dao.HotspotGroupDao;
import com.bluecodeltd.ecap.chw.util.Constants;

import org.smartregister.chw.core.model.NavigationModel;
import org.smartregister.chw.core.model.NavigationOption;
import org.smartregister.chw.core.utils.CoreConstants;

import java.util.ArrayList;
import java.util.List;

public class NavigationModelFlv implements NavigationModel.Flavor {

    private static final List<NavigationOption> navigationOptions = new ArrayList<>();

    @Override
    public List<NavigationOption> getNavigationItems() {

        if (navigationOptions.size() == 0) {
            NavigationOption homeOption = new NavigationOption(
                    R.mipmap.sidemenu_landd,
                    R.mipmap.sidemenu_landd_active,
                    R.string.nav_home,
                    CoreConstants.DrawerMenu.REPORTS,
                    -1);
            navigationOptions.add(homeOption);

            NavigationOption chimwemweOption = new NavigationOption(
                    R.mipmap.sidemenu_families,
                    R.mipmap.sidemenu_families_active,
                    R.string.chimwemwe_groups,
                    Constants.DrawerMenu.CHIMWEMWE,
                    HotspotGroupDao.countGroups());
            navigationOptions.add(chimwemweOption);
        }

        return navigationOptions;
    }
}
