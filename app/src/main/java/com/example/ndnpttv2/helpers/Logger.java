package com.example.ndnpttv2.helpers;

import android.util.Log;

public class Logger {

    public static final String
        LOG_MODULE_LOGIN_ACTIVITY = "LOGIN_ACTIVITY",
        LOG_MODULE_MAIN_ACTIVITY = "MAIN_ACTIVITY",
        LOG_MODULE_PTT_BUTTON_PRESS_RECEIVER = "PTT_BUTTON_PRESS_RECEIVER";

    public static void logMessage(long timestamp, String log_module, String message) {
        Log.d(log_module, Long.toString(timestamp) + ": " + message);
    }

}
