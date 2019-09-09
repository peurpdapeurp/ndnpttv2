package com.example.ndnpttv2.back_end;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import net.named_data.jndn.Name;

public class SCModule {

    private static final String TAG = "SCModule";

    private Context ctx_;
    private Handler uiHandler_;

    public SCModule(Context ctx, Handler uiHandler) {
        ctx_ = ctx;
        uiHandler_ = uiHandler;
    }

    public void notifyNewStreamAvailable(Name streamName) {
        Log.d(TAG, "notifyNewStreamAvailable " + "(" +
                "streamName: " + streamName.toString() +
                ")");

    }

}
