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
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.example.ndnpttv2.R;
import com.example.ndnpttv2.back_end.Threads.NetworkThread;
import com.example.ndnpttv2.back_end.pq_module.PlaybackQueueModule;
import com.example.ndnpttv2.back_end.pq_module.StreamInfo;
import com.example.ndnpttv2.back_end.rec_module.RecorderModule;
import com.example.ndnpttv2.back_end.sync_module.StreamMetaData;
import com.example.ndnpttv2.back_end.sync_module.SyncModule;

import net.named_data.jndn.Name;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    // Messages
    private static final int MSG_NETWORK_THREAD_INITIALIZED = 0;

    // Thread objects
    private NetworkThread networkThread_;

    // Back-end modules
    private PlaybackQueueModule playbackQueueModule_;
    private RecorderModule recorderModule_;
    private SyncModule syncModule_;

    // Configuration parameters
    private Name channelName_;
    private Name userName_;

    // UI objects
    Button notifyNewStreamButton_;

    private BroadcastReceiver pttButtonPressReceiverListener_;
    private Handler handler_;
    private Context ctx_;
    private long lastStreamId_ = 0;

    @SuppressLint("HandlerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        ctx_ = this;

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
                switch (msg.what) {
                    case MSG_NETWORK_THREAD_INITIALIZED: {
                        Log.d(TAG, "Network thread eventInitialized");
                        NetworkThread.Info networkThreadInfo = (NetworkThread.Info) msg.obj;
                        syncModule_ = new SyncModule(
                                new Name(getString(R.string.broadcast_prefix)).append(channelName_),
                                new Name(getString(R.string.data_prefix)).append(userName_),
                                networkThreadInfo.looper
                        );
                        playbackQueueModule_ = new PlaybackQueueModule(ctx_, getMainLooper(), networkThreadInfo);
                        break;
                    }
                    default:
                        throw new IllegalStateException("unexpected msg.what " + msg.what);
                }
            }
        };

        notifyNewStreamButton_ = (Button) findViewById(R.id.notify_new_stream_button);
        notifyNewStreamButton_.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                if (syncModule_ != null) {
//                    syncModule_.notifyNewStreamProducing(
//                            ++lastStreamId_, new StreamMetaData(1, 8000, System.currentTimeMillis())
//                    );
//                }
                if (playbackQueueModule_ != null) {
                    playbackQueueModule_.notifyNewStreamAvailable(
                            new StreamInfo(
                                    new Name("ndnpttv2")
                                    .append("test_stream")
                                    .append("0")
                                    .appendVersion(0),
                                    1, 8000
                            )
                    );
                }
            }
        });

        startActivityForResult(new Intent(this, LoginActivity.class), 0);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK) {
            throw new IllegalStateException("problem getting result from login activity, result code " + resultCode);
        }

        String[] configInfo = data.getStringArrayExtra(IntentInfo.LOGIN_CONFIG);

        channelName_ = new Name(configInfo[IntentInfo.CHANNEL]);
        userName_ = new Name(configInfo[IntentInfo.USER_NAME]);

        networkThread_ = new NetworkThread(new NetworkThread.Callbacks() {
            @Override
            public void onInitialized(NetworkThread.Info info) {
                handler_
                        .obtainMessage(MSG_NETWORK_THREAD_INITIALIZED, info)
                        .sendToTarget();
            }
        });
        networkThread_.start();

    }


    @Override
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(pttButtonPressReceiverListener_);
        super.onDestroy();
    }

}
