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
import com.example.ndnpttv2.back_end.Threads.WorkThread;
import com.example.ndnpttv2.back_end.pq_module.PlaybackQueueModule;
import com.example.ndnpttv2.back_end.pq_module.StreamInfo;
import com.example.ndnpttv2.back_end.r_module.RecorderModule;
import com.example.ndnpttv2.back_end.sync_module.SyncModule;

import net.named_data.jndn.Name;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    // Messages
    private static final int MSG_NETWORK_THREAD_INITIALIZED = 0;
    private static final int MSG_WORK_THREAD_INITIALIZED = 1;

    // Thread objects
    private NetworkThread networkThread_;
    private NetworkThread.Info networkThreadInfo_;
    boolean networkThreadInitialized_ = false;
    private WorkThread workThread_;
    private WorkThread.Info workThreadInfo_;
    boolean workThreadInitialized_ = false;

    // Back-end modules
    private PlaybackQueueModule playbackQueueModule_;
    private RecorderModule recorderModule_;
    private SyncModule syncModule_;

    // UI objects
    private Button notifyNewStreamButton_;
    private Button incrementStreamIdButton_;
    private EditText streamIdInput_;

    private BroadcastReceiver pttButtonPressReceiverListener_;
    private Handler handler_;
    private Context ctx_;

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
                        networkThreadInfo_ = (NetworkThread.Info) msg.obj;
                        networkThreadInitialized_ = true;
                        if (workThreadInitialized_) {
                            playbackQueueModule_ = new PlaybackQueueModule(ctx_, workThreadInfo_.looper, networkThreadInfo_.looper);
                        }
                        break;
                    }
                    case MSG_WORK_THREAD_INITIALIZED: {
                        Log.d(TAG, "Work thread eventInitialized");
                        workThreadInfo_ = (WorkThread.Info) msg.obj;
                        workThreadInitialized_ = true;
                        if (networkThreadInitialized_) {
                            playbackQueueModule_ = new PlaybackQueueModule(ctx_, workThreadInfo_.looper, networkThreadInfo_.looper);
                        }
                        break;
                    }
                    default:
                        throw new IllegalStateException("unexpected msg.what " + msg.what);
                }
            }
        };

        networkThread_ = new NetworkThread(new NetworkThread.Callbacks() {
            @Override
            public void onInitialized(NetworkThread.Info info) {
                handler_
                        .obtainMessage(MSG_NETWORK_THREAD_INITIALIZED, info)
                        .sendToTarget();
            }
        });
        networkThread_.start();

        workThread_ = new WorkThread(new WorkThread.Callbacks() {
            @Override
            public void onInitialized(WorkThread.Info info) {
                handler_
                        .obtainMessage(MSG_WORK_THREAD_INITIALIZED, info)
                        .sendToTarget();
            }
        });
        workThread_.start();

        streamIdInput_ = (EditText) findViewById(R.id.stream_id);

        incrementStreamIdButton_ = (Button) findViewById(R.id.increment_stream_id_button);
        incrementStreamIdButton_.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                streamIdInput_.setText(Long.toString(Long.parseLong(streamIdInput_.getText().toString()) + 1));
            }
        });

        notifyNewStreamButton_ = (Button) findViewById(R.id.notify_new_stream_button);
        notifyNewStreamButton_.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (playbackQueueModule_ != null) {
                    Name streamName = new Name(getString(R.string.network_prefix))
                            .append("test_stream")
                            .append(streamIdInput_.getText().toString())
                            .appendVersion(0);
                    playbackQueueModule_.notifyNewStreamAvailable(
                            new StreamInfo(streamName, 1, 8000)
                    );
                }
            }
        });

    }

    @Override
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(pttButtonPressReceiverListener_);
        super.onDestroy();
    }

}
