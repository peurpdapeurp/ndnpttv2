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
import com.example.ndnpttv2.back_end.MessageTypes;
import com.example.ndnpttv2.back_end.sc_module.SCModule;
import com.example.ndnpttv2.back_end.sp_module.SPModule;
import com.example.ndnpttv2.back_end.sync_module.SyncModule;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    // Back-end modules
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
                    handler_.obtainMessage().sendToTarget();
                } else if (intent.getAction().equals(IntentInfo.PTTButtonPressReceiver_PTT_BUTTON_UP)) {
                    handler_.obtainMessage().sendToTarget();
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

                switch (msg.what) {
                    case MessageTypes.MSG_PROGRESS_EVENT: {
                        streamConsumerModule_.handleMessage(msg);
                        uiManager_.handleMessage(msg);
                        break;
                    }
                    case MessageTypes.MSG_SC_MODULE: {
                        streamConsumerModule_.handleMessage(msg);
                        break;
                    }
                    case MessageTypes.MSG_SP_MODULE: {
                        streamProducerModule_.handleMessage(msg);
                        break;
                    }
                    case MessageTypes.MSG_SYNC_MODULE: {
                        syncModule_.handleMessage(msg);
                        break;
                    }
                    default:
                        throw new IllegalStateException("unexpected msg: " + msg.what);
                }
            }
        };

        uiManager_ = new UiManager(this);

        streamConsumerModule_ = new SCModule(this, handler_, uiManager_);
        streamProducerModule_ = new SPModule();
        syncModule_ = new SyncModule();

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
