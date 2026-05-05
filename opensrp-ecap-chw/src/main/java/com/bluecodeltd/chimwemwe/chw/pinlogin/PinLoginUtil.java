package com.bluecodeltd.chimwemwe.chw.pinlogin;

public class PinLoginUtil {
    private static PinLogger pinLogger;
    public static PinLogger getPinLogger() {
        if (pinLogger == null) {
            pinLogger = new SecurePinLogger();
        }
        return pinLogger;
    }
}
