package com.bluecodeltd.chimwemwe.chw.interactor;

import org.smartregister.chw.core.interactor.CoreAncUpcomingServicesInteractor;

public class AncUpcomingServicesInteractor extends CoreAncUpcomingServicesInteractor {
    public AncUpcomingServicesInteractor() {
        setFlavor(new AncUpcomingServicesInteractorFlv());
    }
}
