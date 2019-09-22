package com.example.ndnpttv2.front_end;

public class IntentInfo {

    // LoginActivity broadcast intent info
    public static int
        CHANNEL_NAME = 0,
        USER_NAME = 1,
        PRODUCER_SAMPLING_RATE = 2,
        PRODUCER_FRAMES_PER_SEGMENT = 3,
        CONSUMER_JITTER_BUFFER_SIZE = 4;
    public static String
            LOGIN_CONFIG = "LOGIN_CONFIG";

    // PTTButtonPressReceiver broadcast intent info
    public static String PTTButtonPressReceiver_PTT_BUTTON_DOWN =
            "PTTButtonPressReceiver_PTT_BUTTON_DOWN";
    public static String PTTButtonPressReceiver_PTT_BUTTON_UP =
            "PTTButtonPressReceiver_PTT_BUTTON_UP";

}
