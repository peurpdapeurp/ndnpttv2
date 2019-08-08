package com.example.ndnpttv2.helpers;

import android.util.Log;

public class Logger {

    public static final String
        LOG_MODULE_LOGIN_ACTIVITY = "LOGIN_ACTIVITY",
        LOG_MODULE_MAIN_ACTIVITY = "MAIN_ACTIVITY",
        LOG_MODULE_PTT_BUTTON_PRESS_RECEIVER = "PTT_BUTTON_PRESS_RECEIVER",
        LOG_MODULE_RECORDER = "RECORDER",
        LOG_MODULE_STREAMER = "STREAMER",
        LOG_MODULE_STREAMER_AUDIOMANAGER = "STREAMER_AUDIOMANAGER",
        LOG_MODULE_STREAMER_STREAMTHREAD = "STREAMER_STREAMTHREAD";

    public static void logMessage(long timestamp, String log_module, String message) {
        Log.d(log_module, timestamp + ": " + message);
    }

}
