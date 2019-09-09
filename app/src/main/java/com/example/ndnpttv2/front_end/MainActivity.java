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
import com.example.ndnpttv2.back_end.AppLogicModule;
import com.example.ndnpttv2.back_end.SCModule;
import com.example.ndnpttv2.back_end.SPModule;
import com.example.ndnpttv2.back_end.SyncModule;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    // Messages
    public static final int MSG_LOGIN_ACTIVITY_FINISHED = 0;
    public static final int MSG_SYNC_NEW_STREAM = 1;
    public static final int MSG_BUTTON_RECORDING_STARTED = 2;
    public static final int MSG_BUTTON_RECORDING_ENDED = 3;
    public static final int MSG_APPLOGIC_RECORDING_STARTED = 4;
    public static final int MSG_APPLOGIC_RECORDING_ENDED = 5;
    public static final int MSG_STREAMPRODUCER_RECORDING_ENDED = 6;
    public static final int MSG_APPLOGIC_STREAM_AVAILABLE = 7;
    public static final int MSG_STREAMCONSUMER_STREAM_PLAYING_FINISHED = 8;

    // Modules
    private AppLogicModule appLogicModule_;
    private SCModule streamConsumerModule_;
    private SPModule streamProducerModule_;
    private SyncModule syncModule_;

    private BroadcastReceiver pttButtonPressReceiverListener_;
    private Handler handler_;

    @SuppressLint("HandlerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        appLogicModule_ = new AppLogicModule();
        streamConsumerModule_ = new SCModule();

        pttButtonPressReceiverListener_ = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(IntentInfo.PTTButtonPressReceiver_PTT_BUTTON_DOWN)) {
                    handler_.obtainMessage(MSG_BUTTON_RECORDING_STARTED).sendToTarget();
                } else if (intent.getAction().equals(IntentInfo.PTTButtonPressReceiver_PTT_BUTTON_UP)) {
                    handler_.obtainMessage(MSG_BUTTON_RECORDING_ENDED).sendToTarget();
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
                switch (msg.what) {
                    case MSG_LOGIN_ACTIVITY_FINISHED: {

                        break;
                    }
                    case MSG_SYNC_NEW_STREAM: {

                        break;
                    }
                    case MSG_BUTTON_RECORDING_STARTED: {

                        break;
                    }
                    case MSG_BUTTON_RECORDING_ENDED: {

                        break;
                    }
                    case MSG_APPLOGIC_RECORDING_STARTED: {

                        break;
                    }
                    case MSG_APPLOGIC_RECORDING_ENDED: {

                        break;
                    }
                    case MSG_STREAMPRODUCER_RECORDING_ENDED: {

                        break;
                    }
                    case MSG_APPLOGIC_STREAM_AVAILABLE: {

                        break;
                    }
                    case MSG_STREAMCONSUMER_STREAM_PLAYING_FINISHED: {

                        break;
                    }
                    default:
                        throw new IllegalStateException("unexpected msg.what: " + msg.what);
                }
            }
        };

    }

    @Override
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(pttButtonPressReceiverListener_);
        super.onDestroy();
    }

}
