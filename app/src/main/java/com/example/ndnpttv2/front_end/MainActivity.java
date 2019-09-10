package com.example.ndnpttv2.front_end;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.example.ndnpttv2.R;
import com.example.ndnpttv2.back_end.app_logic_module.AppLogicModule;
import com.example.ndnpttv2.back_end.sc_module.SCModule;
import com.example.ndnpttv2.back_end.sp_module.SPModule;
import com.example.ndnpttv2.back_end.sync_module.SyncModule;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    // Public constants
    public static final int GENERAL_MODULE_MSG_BASE = 0;

    // Back-end modules
    private AppLogicModule appLogicModule_;
    private SCModule streamConsumerModule_;
    private SPModule streamProducerModule_;
    private SyncModule syncModule_;

    private UiManager uiManager_;
    private BroadcastReceiver pttButtonPressReceiverListener_;
    private Handler handler_;

    @SuppressLint("HandlerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        pttButtonPressReceiverListener_ = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(IntentInfo.PTTButtonPressReceiver_PTT_BUTTON_DOWN)) {
                    handler_.obtainMessage(AppLogicModule.MSG_BUTTON_RECORD_START_REQUEST).sendToTarget();
                } else if (intent.getAction().equals(IntentInfo.PTTButtonPressReceiver_PTT_BUTTON_UP)) {
                    handler_.obtainMessage(AppLogicModule.MSG_BUTTON_RECORD_STOP_REQUEST).sendToTarget();
                } else {
                    Log.e(TAG, "pttButtonPressReceiverListener_ unexpected intent: " + intent.getAction());
                }
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(pttButtonPressReceiverListener_,
                PTTButtonPressReceiver.getIntentFilter());

        handler_ = new Handler() {
            @Override
            public void handleMessage(@NonNull Message msg) {
                Log.d(TAG, "Got message " + msg.what);

                if (msg.what >= GENERAL_MODULE_MSG_BASE && msg.what < SCModule.SC_MODULE_MSG_BASE) {
                    this.handleMessage(msg);
                }
                else if (msg.what >= SCModule.SC_MODULE_MSG_BASE && msg.what < SPModule.SP_MODULE_MSG_BASE) {
                    streamConsumerModule_.handleMessage(msg);
                }
                else if (msg.what >= SPModule.SP_MODULE_MSG_BASE && msg.what < SyncModule.SYNC_MODULE_MSG_BASE) {
                    streamProducerModule_.handleMessage(msg);
                }
                else if (msg.what >= SyncModule.SYNC_MODULE_MSG_BASE && msg.what < AppLogicModule.APPLOGIC_MODULE_MSG_BASE) {
                    syncModule_.handleMessage(msg);
                }
                else if (msg.what >= AppLogicModule.APPLOGIC_MODULE_MSG_BASE) {
                    appLogicModule_.handleMessage(msg);
                }
            }
        };

        uiManager_ = new UiManager(this);

        appLogicModule_ = new AppLogicModule(handler_);
        streamConsumerModule_ = new SCModule(this, handler_, uiManager_);
        streamProducerModule_ = new SPModule();
        syncModule_ = new SyncModule();



    }

    private void handleMessage(Message msg) {
        switch (msg.what) {
            default:
                throw new IllegalStateException("unexpected msg.what " + msg.what);
        }
    }

    @Override
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(pttButtonPressReceiverListener_);
        super.onDestroy();
    }

    public Handler getHandler() {
        return handler_;
    }

}
