package com.example.ndnpttv2.back_end.back_end_impl;

import android.content.Context;

import com.example.ndnpttv2.back_end.Recorder_Module;
import com.example.ndnpttv2.helpers.Logger;

public class Recorder extends Recorder_Module {

    public Recorder(Context ctx) {
        super(ctx);
        Logger.logMessage(System.currentTimeMillis(), Logger.LOG_MODULE_RECORDER,
                "Successfully initialized.");
    };

    @Override
    protected void RECORD_REQUEST_START_LOGIC() {

        super.RECORD_REQUEST_START_LOGIC();
    }

    @Override
    protected void RECORD_REQUEST_STOP_LOGIC() {

        super.RECORD_REQUEST_STOP_LOGIC();
    }
}
