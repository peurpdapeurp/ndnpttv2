package com.example.ndnpttv2.front_end;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.example.ndnpttv2.R;
import com.example.ndnpttv2.back_end.pq_module.stream_consumer.StreamConsumer;
import com.example.ndnpttv2.back_end.shared_state.AppState;
import com.example.ndnpttv2.back_end.shared_state.PeerStateTable;
import com.example.ndnpttv2.back_end.structs.StreamInfo;
import com.example.ndnpttv2.back_end.structs.SyncStreamInfo;
import com.example.ndnpttv2.back_end.threads.NetworkThread;
import com.example.ndnpttv2.back_end.pq_module.PlaybackQueueModule;
import com.example.ndnpttv2.back_end.rec_module.RecorderModule;
import com.example.ndnpttv2.back_end.sync_module.SyncModule;
import com.example.ndnpttv2.back_end.wifi_module.WifiModule;
import com.example.ndnpttv2.util.Logger;

import net.named_data.jndn.Name;
import net.named_data.jndn.encoding.EncodingException;

import static android.widget.Toast.LENGTH_SHORT;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    // Private constants
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

    // Messages
    private static final int MSG_NETWORK_THREAD_INITIALIZED = 0;
    private static final int MSG_SYNC_MODULE_INITIALIZED = 1;
    private static final int MSG_BUTTON_RECORD_REQUEST_START = 2;
    private static final int MSG_BUTTON_RECORD_REQUEST_STOP = 3;
    private static final int MSG_RECORDER_RECORD_STARTED = 4;
    private static final int MSG_SYNC_NEW_STREAMS_AVAILABLE = 5;
    private static final int MSG_PLAYBACKQUEUE_STREAM_STATE_CREATED = 6;
    private static final int MSG_RECORDER_STREAM_STATE_CREATED = 7;
    private static final int MSG_RECORDER_RECORD_START_REQUEST_IGNORED = 8;
    private static final int MSG_WIFI_STATE_CHANGED = 9;

    // Thread objects
    private NetworkThread networkThread_;
    private boolean networkThreadInitialized_ = false;

    // Back-end modules
    private PlaybackQueueModule playbackQueueModule_;
    private RecorderModule recorderModule_;
    private SyncModule syncModule_;
    private boolean syncModuleInitialized_ = false; // ignore button presses until sync module is initialized
    private WifiModule wifiModule_;
    private AppState appState_;
    private PeerStateTable peerStateTable_;

    // Configuration parameters
    private Name applicationBroadcastPrefix_;
    private Name applicationDataPrefix_;
    private long syncSessionId_;

    // UI elements
    private TextView settingsDisplay_;
    private ProgressBarListFragment progressBarListFragment_;

    private BroadcastReceiver pttButtonPressReceiverListener_;
    private Handler handler_;
    private Context ctx_;
    private Vibrator v_;
    private Settings settings_;
    private Toast syncUninitializedErrorToast_;
    private Toast recordingRequestIgnoredToast_;
    private Toast recordingPermissionUnenabledToast_;

    // Requesting permission to RECORD_AUDIO
    private boolean permissionToRecordAccepted_ = false;
    private String[] permissions_ = {Manifest.permission.RECORD_AUDIO};

    public static class Settings {
        public String channelName;
        public String userName;
        public int producerSamplingRate;
        public int producerFramesPerSegment;
        public int consumerJitterBufferSize;
        public int consumerMaxHistoricalStreamFetchTimeMs;
        public int consumerMaxSuccessfulDataFetchIntervalMs;
        public int consumerMaxMetaDataFetchTimeMs;
        public String accessPointIpAddress;
    }

    @SuppressLint("HandlerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        ActivityCompat.requestPermissions(this, permissions_, REQUEST_RECORD_AUDIO_PERMISSION);

        ctx_ = this;

        settings_ = new Settings();

        v_ = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        settingsDisplay_ = (TextView) findViewById(R.id.settings_display);
        progressBarListFragment_ = (ProgressBarListFragment) getSupportFragmentManager().findFragmentById(R.id.progress_bar_list_fragment);

        pttButtonPressReceiverListener_ = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                if (!syncModuleInitialized_) {
                    // Vibrate for 300 milliseconds
                    v_.vibrate(300);
                    if (syncUninitializedErrorToast_ != null) {
                        syncUninitializedErrorToast_.cancel();
                    }
                    syncUninitializedErrorToast_ = Toast.makeText(ctx_,
                            "Sync Module not yet initialized (did you start NFD-Android?).",
                            LENGTH_SHORT);
                    syncUninitializedErrorToast_.show();
                    return;
                }

                if (!permissionToRecordAccepted_) {
                    // Vibrate for 300 milliseconds
                    v_.vibrate(300);
                    if (recordingPermissionUnenabledToast_ != null) {
                        recordingPermissionUnenabledToast_.cancel();
                    }
                    recordingPermissionUnenabledToast_ = Toast.makeText(ctx_,
                            "Please enable recording permission to record.",
                            LENGTH_SHORT);
                    recordingPermissionUnenabledToast_.show();
                    return;
                }

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

                        appState_ = new AppState();
                        peerStateTable_ = new PeerStateTable();

                        syncModule_ = new SyncModule(
                                applicationBroadcastPrefix_,
                                applicationDataPrefix_,
                                syncSessionId_,
                                networkThreadInfo.looper,
                                peerStateTable_
                        );
                        syncModule_.eventInitialized.addListener(object ->
                            handler_
                                    .obtainMessage(MSG_SYNC_MODULE_INITIALIZED)
                                    .sendToTarget());
                        syncModule_.eventNewStreamAvailable.addListener(syncStreamInfos ->
                            handler_
                                .obtainMessage(MSG_SYNC_NEW_STREAMS_AVAILABLE, syncStreamInfos)
                                .sendToTarget());

                        playbackQueueModule_ = new PlaybackQueueModule(
                                ctx_,
                                getMainLooper(),
                                networkThreadInfo,
                                appState_,
                                new Name(getString(R.string.data_prefix)),
                                peerStateTable_,
                                new StreamConsumer.Options(
                                        settings_.consumerJitterBufferSize,
                                        settings_.consumerMaxHistoricalStreamFetchTimeMs,
                                        settings_.consumerMaxSuccessfulDataFetchIntervalMs,
                                        settings_.consumerMaxMetaDataFetchTimeMs
                                )
                        );
                        playbackQueueModule_.eventStreamStateCreated.addListener(streamNameAndStreamState ->
                            handler_
                                .obtainMessage(MSG_PLAYBACKQUEUE_STREAM_STATE_CREATED, streamNameAndStreamState)
                                .sendToTarget());

                        recorderModule_ = new RecorderModule(
                                applicationDataPrefix_,
                                networkThreadInfo,
                                appState_,
                                new RecorderModule.Options(settings_.producerSamplingRate, settings_.producerFramesPerSegment)
                        );
                        recorderModule_.eventRecordingStarted.addListener(streamInfo ->
                            handler_
                                .obtainMessage(MSG_RECORDER_RECORD_STARTED, streamInfo)
                                .sendToTarget());
                        recorderModule_.eventStreamStateCreated.addListener(streamInfoAndStreamState ->
                            handler_
                                .obtainMessage(MSG_RECORDER_STREAM_STATE_CREATED, streamInfoAndStreamState)
                                .sendToTarget());
                        recorderModule_.eventRecordingStartRequestIgnored.addListener(object ->
                            handler_
                                .obtainMessage(MSG_RECORDER_RECORD_START_REQUEST_IGNORED)
                                .sendToTarget());

                        wifiModule_ = new WifiModule(ctx_, getMainLooper(),
                                newWifiState ->
                                    handler_
                                            .obtainMessage(MSG_WIFI_STATE_CHANGED, newWifiState, 0)
                                            .sendToTarget()
                        );

                        networkThreadInitialized_ = true;
                        break;
                    }
                    case MSG_SYNC_MODULE_INITIALIZED: {
                        syncModuleInitialized_ = true;
                        break;
                    }
                    case MSG_BUTTON_RECORD_REQUEST_START: {
                        recorderModule_.notifyRecordRequestStart();
                        break;
                    }
                    case MSG_BUTTON_RECORD_REQUEST_STOP: {
                        recorderModule_.notifyRecordRequestStop();
                        break;
                    }
                    case MSG_RECORDER_RECORD_STARTED: {
                        StreamInfo streamInfo = (StreamInfo) msg.obj;
                        try {
                            syncModule_.notifyNewStreamProducing(streamInfo.streamName.get(-1).toSequenceNumber());
                        } catch (EncodingException e) {
                            e.printStackTrace();
                        }
                        break;
                    }
                    case MSG_SYNC_NEW_STREAMS_AVAILABLE: {
                        SyncStreamInfo syncStreamInfo = (SyncStreamInfo) msg.obj;
                        playbackQueueModule_.notifyNewStreamAvailable(syncStreamInfo);
                        break;
                    }
                    case MSG_PLAYBACKQUEUE_STREAM_STATE_CREATED: {
                        PlaybackQueueModule.StreamNameAndStreamState streamNameAndStreamState =
                                (PlaybackQueueModule.StreamNameAndStreamState) msg.obj;
                        progressBarListFragment_.addProgressBar(
                                new ProgressBarFragmentConsume(streamNameAndStreamState, getMainLooper(), ctx_)
                        );
                        break;
                    }
                    case MSG_RECORDER_STREAM_STATE_CREATED: {
                        RecorderModule.StreamInfoAndStreamState streamInfoAndStreamState =
                                (RecorderModule.StreamInfoAndStreamState) msg.obj;
                        progressBarListFragment_.addProgressBar(
                                new ProgressBarFragmentProduce(streamInfoAndStreamState, getMainLooper(), ctx_)
                        );
                        break;
                    }
                    case MSG_RECORDER_RECORD_START_REQUEST_IGNORED: {
                        // Vibrate for 300 milliseconds
                        v_.vibrate(300);

                        if (recordingRequestIgnoredToast_ != null) {
                            recordingRequestIgnoredToast_.cancel();
                        }
                        recordingRequestIgnoredToast_ = Toast.makeText(ctx_, "Recording start request ignored.", LENGTH_SHORT);
                        recordingRequestIgnoredToast_.show();
                        break;
                    }
                    case MSG_WIFI_STATE_CHANGED: {
                        int newWifiState = msg.arg1;
                        playbackQueueModule_.notifyNewWifiState(newWifiState);
                        networkThread_.notifyNewWifiState(newWifiState);
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

        settings_.channelName = configInfo[IntentInfo.CHANNEL_NAME];
        settings_.userName = configInfo[IntentInfo.USER_NAME];
        settings_.producerSamplingRate = Integer.parseInt(configInfo[IntentInfo.PRODUCER_SAMPLING_RATE]);
        settings_.producerFramesPerSegment = Integer.parseInt(configInfo[IntentInfo.PRODUCER_FRAMES_PER_SEGMENT]);
        settings_.consumerJitterBufferSize = Integer.parseInt(configInfo[IntentInfo.CONSUMER_JITTER_BUFFER_SIZE]);
        settings_.consumerMaxHistoricalStreamFetchTimeMs = Integer.parseInt(configInfo[IntentInfo.CONSUMER_MAX_HISTORICAL_STREAM_FETCH_TIME_MS]);
        settings_.consumerMaxSuccessfulDataFetchIntervalMs = Integer.parseInt(configInfo[IntentInfo.CONSUMER_MAX_SUCCESSFUL_DATA_FETCH_INTERVAL_MS]);
        settings_.consumerMaxMetaDataFetchTimeMs = Integer.parseInt(configInfo[IntentInfo.CONSUMER_MAX_META_DATA_FETCH_TIME_MS]);
        settings_.accessPointIpAddress = configInfo[IntentInfo.ACCESS_POINT_IP_ADDRESS];

        Logger.logEvent(new Logger.LogEventInfo(Logger.APP_INIT, System.currentTimeMillis(), 0, settings_, null));

        String settingsString =
                getString(R.string.channel_name_label) + " " + settings_.channelName + "\n" +
                getString(R.string.user_name_label) + " " + settings_.userName + "\n" +
                getString(R.string.producer_sampling_rate_label) + " " + configInfo[IntentInfo.PRODUCER_SAMPLING_RATE] + "\n" +
                getString(R.string.producer_frames_per_segment_label) + " " + configInfo[IntentInfo.PRODUCER_FRAMES_PER_SEGMENT] + "\n" +
                getString(R.string.consumer_jitter_buffer_size_label) + " " + configInfo[IntentInfo.CONSUMER_JITTER_BUFFER_SIZE] + "\n" +
                getString(R.string.consumer_max_historical_stream_fetch_time_ms_label) + " " + configInfo[IntentInfo.CONSUMER_MAX_HISTORICAL_STREAM_FETCH_TIME_MS];
        settingsDisplay_.setText(settingsString);

        syncSessionId_ = System.currentTimeMillis();

        applicationBroadcastPrefix_ = new Name(getString(R.string.broadcast_prefix))
                .append(settings_.channelName);
        applicationDataPrefix_ = new Name(getString(R.string.data_prefix))
                .append(settings_.channelName)
                .append(settings_.userName)
                .append(Long.toString(syncSessionId_));

        // Thread objects
        networkThread_ = new NetworkThread(
                applicationDataPrefix_,
                info -> handler_
                        .obtainMessage(MSG_NETWORK_THREAD_INITIALIZED, info)
                        .sendToTarget(),
                new NetworkThread.Options(settings_.accessPointIpAddress));

        networkThread_.start();

    }


    @Override
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(pttButtonPressReceiverListener_);
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_RECORD_AUDIO_PERMISSION:
                permissionToRecordAccepted_ = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;
        }
        if (!permissionToRecordAccepted_) ActivityCompat.requestPermissions(this, permissions_, REQUEST_RECORD_AUDIO_PERMISSION);;

    }

}
