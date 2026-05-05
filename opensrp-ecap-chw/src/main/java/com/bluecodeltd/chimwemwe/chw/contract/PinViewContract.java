package com.bluecodeltd.chimwemwe.chw.contract;

import com.bluecodeltd.chimwemwe.chw.pinlogin.PinLogger;

public interface PinViewContract {

    interface Controller {

        void navigateToFragment(String destinationFragment);

        void startPasswordLogin();

        void startHomeActivity();

        PinLogger getPinLogger();
    }
}
