package com.example.ndnpttv2.back_end;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.ndnpttv2.helpers.InterModuleInfo;
import com.example.ndnpttv2.helpers.Logger;

public abstract class Streamer_Module {

    protected static Context ctx_;

    public Streamer_Module(Context ctx) {
        ctx_ = ctx;
        LocalBroadcastManager.getInstance(ctx_).registerReceiver(recorderListener_,
                Recorder_Module.getIntentFilter());
    }

    protected BroadcastReceiver recorderListener_ = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(InterModuleInfo.Recorder_RECORD_REQUEST_START)) {
                RECORD_REQUEST_START_LOGIC();
            } else if (intent.getAction().equals(InterModuleInfo.Recorder_RECORD_REQUEST_STOP)) {
                RECORD_REQUEST_STOP_LOGIC();
            } else {
                Logger.logMessage(System.currentTimeMillis(), Logger.LOG_MODULE_STREAMER,
                        "recorderListener got unexpected intent: " + intent.getAction());
            }
        }
    };

    protected void RECORD_REQUEST_START_LOGIC() {
        Logger.logMessage(System.currentTimeMillis(), Logger.LOG_MODULE_STREAMER,
                "RECORD_REQUEST_START_LOGIC was called.");

        LocalBroadcastManager.getInstance(ctx_).sendBroadcast(
                new Intent(InterModuleInfo.Streamer_RECORDING_STARTED)
        );
    };

    protected void RECORD_REQUEST_STOP_LOGIC() {
        Logger.logMessage(System.currentTimeMillis(), Logger.LOG_MODULE_STREAMER,
                "RECORD_REQUEST_STOP_LOGIC was called.");

        LocalBroadcastManager.getInstance(ctx_).sendBroadcast(
                new Intent(InterModuleInfo.Streamer_RECORDING_FINISHED)
        );
    };

    public static IntentFilter getIntentFilter() {
        IntentFilter ret = new IntentFilter();
        ret.addAction(InterModuleInfo.Streamer_RECORDING_STARTED);
        ret.addAction(InterModuleInfo.Streamer_RECORDING_FINISHED);

        return ret;
    }

}
