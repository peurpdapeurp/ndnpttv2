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
import android.widget.EditText;
import android.widget.TextView;

import com.example.ndnpttv2.R;
import com.example.ndnpttv2.back_end.AppState;
import com.example.ndnpttv2.back_end.StreamInfo;
import com.example.ndnpttv2.back_end.threads.NetworkThread;
import com.example.ndnpttv2.back_end.pq_module.PlaybackQueueModule;
import com.example.ndnpttv2.back_end.rec_module.RecorderModule;
import com.example.ndnpttv2.back_end.sync_module.SyncModule;

import net.named_data.jndn.Name;
import net.named_data.jndn.encoding.EncodingException;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    // Messages
    private static final int MSG_NETWORK_THREAD_INITIALIZED = 0;
    private static final int MSG_BUTTON_RECORD_REQUEST_START = 1;
    private static final int MSG_BUTTON_RECORD_REQUEST_STOP = 2;
    private static final int MSG_RECORDER_RECORD_STARTED = 3;
    private static final int MSG_RECORDER_RECORD_FINISHED = 4;
    private static final int MSG_SYNC_NEW_STREAM_AVAILABLE = 5;
    private static final int MSG_PLAYBACKQUEUE_STREAM_STATE_CREATED = 6;
    private static final int MSG_RECORDER_STREAM_STATE_CREATED = 7;

    // Thread objects
    private boolean networkThreadInitialized_ = false;

    // Back-end modules
    private PlaybackQueueModule playbackQueueModule_;
    private RecorderModule recorderModule_;
    private SyncModule syncModule_;
    private AppState appState_;

    // Configuration parameters
    private Name applicationBroadcastPrefix_;
    private Name applicationDataPrefix_;
    private long syncSessionId_;

    // UI elements
    private TextView channelNameDisplay_;
    private TextView userNameDisplay_;
    private ProgressBarListFragment progressBarListFragment_;

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

        channelNameDisplay_ = (TextView) findViewById(R.id.channel_name_display);
        userNameDisplay_ = (TextView) findViewById(R.id.user_name_display);
        progressBarListFragment_ = (ProgressBarListFragment) getSupportFragmentManager().findFragmentById(R.id.progress_bar_list_fragment);

        pttButtonPressReceiverListener_ = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(IntentInfo.PTTButtonPressReceiver_PTT_BUTTON_DOWN)) {
                    if (networkThreadInitialized_) {
                        handler_.obtainMessage(MSG_BUTTON_RECORD_REQUEST_START).sendToTarget();
                    }
                } else if (intent.getAction().equals(IntentInfo.PTTButtonPressReceiver_PTT_BUTTON_UP)) {
                    if (networkThreadInitialized_) {
                        handler_.obtainMessage(MSG_BUTTON_RECORD_REQUEST_STOP).sendToTarget();
                    }
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
                                applicationBroadcastPrefix_,
                                applicationDataPrefix_,
                                syncSessionId_,
                                networkThreadInfo.looper
                        );
                        syncModule_.eventNewStreamAvailable.addListener(streamName ->
                                    handler_
                                    .obtainMessage(MSG_SYNC_NEW_STREAM_AVAILABLE, streamName)
                                    .sendToTarget()
                        );

                        appState_ = new AppState();

                        playbackQueueModule_ = new PlaybackQueueModule(
                                ctx_,
                                getMainLooper(),
                                networkThreadInfo,
                                appState_
                        );
                        playbackQueueModule_.eventStreamStateCreated.addListener(streamInfoAndStreamState ->
                                handler_
                                .obtainMessage(MSG_PLAYBACKQUEUE_STREAM_STATE_CREATED, streamInfoAndStreamState)
                                .sendToTarget());

                        recorderModule_ = new RecorderModule(
                                applicationDataPrefix_,
                                networkThreadInfo,
                                appState_
                        );
                        recorderModule_.eventRecordingStarted.addListener(streamInfo ->
                                handler_
                                .obtainMessage(MSG_RECORDER_RECORD_STARTED, streamInfo)
                                .sendToTarget());
                        recorderModule_.eventRecordingFinished.addListener(streamName ->
                                handler_
                                .obtainMessage(MSG_RECORDER_RECORD_FINISHED, streamName)
                                .sendToTarget());
                        recorderModule_.eventStreamStateCreated.addListener(streamInfoAndStreamState ->
                                handler_
                                .obtainMessage(MSG_RECORDER_STREAM_STATE_CREATED, streamInfoAndStreamState)
                                .sendToTarget());

                        networkThreadInitialized_ = true;
                        break;
                    }
                    case MSG_BUTTON_RECORD_REQUEST_START: {
                        recorderModule_.recordRequestStart();
                        break;
                    }
                    case MSG_BUTTON_RECORD_REQUEST_STOP: {
                        recorderModule_.recordRequestStop();
                        break;
                    }
                    case MSG_RECORDER_RECORD_STARTED: {
                        StreamInfo streamInfo = (StreamInfo) msg.obj;
                        try {
                            syncModule_.notifyNewStreamProducing(
                                streamInfo.streamName.get(-1).toSequenceNumber(),
                                    streamInfo.getMetaData()
                            );
                        } catch (EncodingException e) {
                            e.printStackTrace();
                        }
                        break;
                    }
                    case MSG_RECORDER_RECORD_FINISHED: {
                        Log.d(TAG, "RecorderModule finished recording");
                        break;
                    }
                    case MSG_SYNC_NEW_STREAM_AVAILABLE: {
                        StreamInfo streamInfo = (StreamInfo) msg.obj;
                        playbackQueueModule_.notifyNewStreamAvailable(streamInfo);
                        break;
                    }
                    case MSG_PLAYBACKQUEUE_STREAM_STATE_CREATED: {
                        PlaybackQueueModule.StreamInfoAndStreamState streamInfoAndStreamState =
                                (PlaybackQueueModule.StreamInfoAndStreamState) msg.obj;
                        progressBarListFragment_.addProgressBar(
                                new ProgressBarFragmentConsume(streamInfoAndStreamState, getMainLooper())
                        );
                        break;
                    }
                    case MSG_RECORDER_STREAM_STATE_CREATED: {
                        RecorderModule.StreamInfoAndStreamState streamInfoAndStreamState =
                                (RecorderModule.StreamInfoAndStreamState) msg.obj;
                        progressBarListFragment_.addProgressBar(
                                new ProgressBarFragmentProduce(streamInfoAndStreamState, getMainLooper())
                        );
                        break;
                    }
                    default:
                        throw new IllegalStateException("unexpected msg.what " + msg.what);
                }
            }
        };

        startActivityForResult(new Intent(this, LoginActivity.class), 0);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK) {
            throw new IllegalStateException("problem getting result from login activity, result code " + resultCode);
        }

        String[] configInfo = data.getStringArrayExtra(IntentInfo.LOGIN_CONFIG);

        String channelName = configInfo[IntentInfo.CHANNEL];
        String userName = configInfo[IntentInfo.USER_NAME];

        channelNameDisplay_.setText(getString(R.string.channel_name_label) + " " + channelName);
        userNameDisplay_.setText(getString(R.string.user_name_label) + " " + userName);

        syncSessionId_ = System.currentTimeMillis();

        applicationBroadcastPrefix_ = new Name(getString(R.string.broadcast_prefix))
                .append(channelName);
        applicationDataPrefix_ = new Name(getString(R.string.data_prefix))
                .append(channelName)
                .append(userName)
                .append(Long.toString(syncSessionId_));

        // Thread objects
        NetworkThread networkThread = new NetworkThread(
                applicationDataPrefix_,
                info -> handler_
                        .obtainMessage(MSG_NETWORK_THREAD_INITIALIZED, info)
                        .sendToTarget());

        networkThread.start();

    }


    @Override
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(pttButtonPressReceiverListener_);
        super.onDestroy();
    }

}
