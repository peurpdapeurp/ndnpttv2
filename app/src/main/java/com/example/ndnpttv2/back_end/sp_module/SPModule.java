package com.example.ndnpttv2.back_end.sp_module;

import android.os.Message;
import android.util.Log;

public class SPModule {

    private static final String TAG = "SPModule";

    public SPModule() {

    }

    public void handleMessage(Message msg) {
        switch(msg.what) {
            default:
                throw new IllegalStateException("unexpected msg.what " + msg.what);
        }
    }

}
