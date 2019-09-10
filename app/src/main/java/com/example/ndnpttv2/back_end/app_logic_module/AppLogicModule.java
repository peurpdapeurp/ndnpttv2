package com.example.ndnpttv2.back_end.app_logic_module;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.example.ndnpttv2.Util.Helpers;
import com.example.ndnpttv2.back_end.StreamInfo;
import com.example.ndnpttv2.back_end.sc_module.SCModule;

public class AppLogicModule {

    private static final String TAG = "AppLogicModuleImpl";

    // Public constants
    public static final int APPLOGIC_MODULE_MSG_BASE = 2000;

    // Messages
    public static final int MSG_SYNC_NEW_STREAM_AVAILABLE = APPLOGIC_MODULE_MSG_BASE;
    public static final int MSG_SC_STREAM_PLAYING_FINISHED = APPLOGIC_MODULE_MSG_BASE + 1;
    public static final int MSG_BUTTON_RECORD_START_REQUEST = APPLOGIC_MODULE_MSG_BASE + 2;
    public static final int MSG_BUTTON_RECORD_STOP_REQUEST = APPLOGIC_MODULE_MSG_BASE + 3;

    private Handler mainThreadHandler_;

    public AppLogicModule(Handler mainThreadHandler) {
        mainThreadHandler_ = mainThreadHandler;
    }

    public void handleMessage(Message msg) {

        switch (msg.what) {
            case MSG_SYNC_NEW_STREAM_AVAILABLE: {
                StreamInfo streamInfo = (StreamInfo) msg.obj;
                Log.d(TAG, "New stream available (" +
                                Helpers.getStreamInfoString(streamInfo) +
                                ")");
                mainThreadHandler_
                        .obtainMessage(SCModule.MSG_APPLOGIC_NEW_STREAM_AVAILABLE,
                                streamInfo)
                        .sendToTarget();
                break;
            }
            case MSG_SC_STREAM_PLAYING_FINISHED: {
                StreamInfo streamInfo = (StreamInfo) msg.obj;
                Log.d(TAG, "Stream playing finished (" +
                        Helpers.getStreamInfoString(streamInfo) +
                        ")");
                break;
            }
            case MSG_BUTTON_RECORD_START_REQUEST: {

                break;
            }
            case MSG_BUTTON_RECORD_STOP_REQUEST: {

                break;
            }
            default: {
                throw new IllegalStateException("unexpected msg " + msg.what);
            }
        }

    }

}
