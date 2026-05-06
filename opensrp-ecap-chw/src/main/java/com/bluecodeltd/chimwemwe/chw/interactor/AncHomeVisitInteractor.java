package com.bluecodeltd.chimwemwe.chw.interactor;

import org.smartregister.chw.core.interactor.CoreAncHomeVisitInteractor;

public class AncHomeVisitInteractor extends CoreAncHomeVisitInteractor {
    public AncHomeVisitInteractor() {
        setFlavor(new AncHomeVisitInteractorFlv());
    }
}
