package com.example.ndnpttv2.front_end;

public class IntentInfo {

    // LoginActivity broadcast intent info
    public static int
        CHANNEL = 0,
        USER_NAME = 1,
        SEGMENT_INTEREST_MAX_REATTEMPTS = 2,
        SEGMENT_INTEREST_LIFETIME = 3,
        AP_IP_ADDRESS = 4;
    public static String
        LoginActivity_CONFIG = "LoginActivity_CONFIG";

    // PTTButtonPressReceiver broadcast intent info
    public static String PTTButtonPressReceiver_PTT_BUTTON_DOWN =
            "PTTButtonPressReceiver_PTT_BUTTON_DOWN";
    public static String PTTButtonPressReceiver_PTT_BUTTON_UP =
            "PTTButtonPressReceiver_PTT_BUTTON_UP";

}
