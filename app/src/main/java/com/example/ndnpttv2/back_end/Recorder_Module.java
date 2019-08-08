package com.example.ndnpttv2.back_end;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.ndnpttv2.front_end.MainActivity;
import com.example.ndnpttv2.helpers.InterModuleInfo;
import com.example.ndnpttv2.helpers.Logger;

public abstract class Recorder_Module {

    protected static Context ctx_;

    public Recorder_Module(Context ctx) {
        ctx_ = ctx;
        LocalBroadcastManager.getInstance(ctx_).registerReceiver(mainActivityListener_,
                MainActivity.getIntentFilter());
    }

    protected BroadcastReceiver mainActivityListener_ = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(InterModuleInfo.MainActivity_RECORD_REQUEST_START)) {
            RECORD_REQUEST_START_LOGIC();
        } else if (intent.getAction().equals(InterModuleInfo.MainActivity_RECORD_REQUEST_STOP)) {
            RECORD_REQUEST_STOP_LOGIC();
        } else {
            Logger.logMessage(System.currentTimeMillis(), Logger.LOG_MODULE_RECORDER,
                        "mainActivityListener got unexpected intent: " + intent.getAction());
        }
        }
    };

    protected void RECORD_REQUEST_START_LOGIC() {
        Logger.logMessage(System.currentTimeMillis(), Logger.LOG_MODULE_RECORDER,
                "RECORD_REQUEST_START_LOGIC was called.");

        LocalBroadcastManager.getInstance(ctx_).sendBroadcast(
                new Intent(InterModuleInfo.Recorder_RECORD_REQUEST_START)
        );
    };

    protected void RECORD_REQUEST_STOP_LOGIC() {
        Logger.logMessage(System.currentTimeMillis(), Logger.LOG_MODULE_RECORDER,
                "RECORD_REQUEST_STOP_LOGIC was called.");

        LocalBroadcastManager.getInstance(ctx_).sendBroadcast(
                new Intent(InterModuleInfo.Recorder_RECORD_REQUEST_STOP)
        );
    };

    public static IntentFilter getIntentFilter() {
        IntentFilter ret = new IntentFilter();
        ret.addAction(InterModuleInfo.Recorder_RECORD_REQUEST_START);
        ret.addAction(InterModuleInfo.Recorder_RECORD_REQUEST_STOP);

        return ret;
    }

}
