package com.example.ndnpttv2.back_end.sync_module;

import android.os.Message;

public class SyncModule {

    private static final String TAG = "SyncModule";

    // Public constants
    public static final int SYNC_MODULE_MSG_BASE = 1500;

    public SyncModule() {

    }

    public void handleMessage(Message msg) {
        switch(msg.what) {
            default:
                throw new IllegalStateException("unexpected msg.what " + msg.what);
        }
    }

}
