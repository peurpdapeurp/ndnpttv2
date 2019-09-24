package com.example.ndnpttv2.back_end.pq_module;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.ndnpttv2.back_end.shared_state.AppState;
import com.example.ndnpttv2.back_end.shared_state.PeerStateTable;
import com.example.ndnpttv2.back_end.structs.ChannelUserSession;
import com.example.ndnpttv2.back_end.structs.SyncStreamInfo;
import com.example.ndnpttv2.back_end.threads.NetworkThread;
import com.example.ndnpttv2.back_end.pq_module.stream_consumer.StreamConsumer;
import com.example.ndnpttv2.back_end.pq_module.stream_player.StreamPlayer;
import com.example.ndnpttv2.back_end.pq_module.stream_player.exoplayer_customization.InputStreamDataSource;
import com.example.ndnpttv2.back_end.structs.ProgressEventInfo;
import com.example.ndnpttv2.back_end.wifi_module.WifiModule;
import com.example.ndnpttv2.util.Helpers;
import com.example.ndnpttv2.util.Logger;
import com.pploder.events.Event;
import com.pploder.events.SimpleEvent;

import net.named_data.jndn.Name;

import java.util.HashMap;
import java.util.concurrent.LinkedTransferQueue;

import static com.example.ndnpttv2.back_end.wifi_module.WifiModule.CONNECTED;

public class PlaybackQueueModule {

    private static final String TAG = "PlaybackQueueModule";

    // Private constants
    private static final int PROCESSING_INTERVAL_MS = 50;

    // Messages
    private static final int MSG_DO_SOME_WORK = 0;
    private static final int MSG_STREAM_CONSUMER_FETCHING_COMPLETE = 1;
    private static final int MSG_STREAM_CONSUMER_FETCHING_FAILED = 2;
    private static final int MSG_STREAM_PLAYER_PLAYING_COMPLETE = 3;
    private static final int MSG_NEW_STREAM_AVAILABLE = 4;
    private static final int MSG_NEW_WIFI_STATE = 5;
    private static final int MSG_PROCESS_SOFT_FAILURES = 6;

    // Events
    public Event<StreamNameAndStreamState> eventStreamStateCreated;

    private PeerStateTable peerStateTable_;
    private AppState appState_;

    private Context ctx_;
    private Handler progressEventHandler_;
    private Handler moduleMessageHandler_;
    private Handler workHandler_;

    private Name networkDataPrefix_;
    private LinkedTransferQueue<SyncStreamInfo> fetchingQueue_;
    private LinkedTransferQueue<Name> playbackQueue_;
    private LinkedTransferQueue<Name> softFailureQueue_;
    private HashMap<Name, InternalStreamConsumptionState> streamStates_;
    private NetworkThread.Info networkThreadInfo_;
    private StreamConsumer.Options options_;
    private int wifiConnectionState_;

    public static class StreamNameAndStreamState {
        StreamNameAndStreamState(Name streamName, InternalStreamConsumptionState streamState) {
            this.streamName = streamName;
            this.streamState = streamState;
        }
        public Name streamName;
        public InternalStreamConsumptionState streamState;
    }

    public PlaybackQueueModule(Context ctx, Looper mainThreadLooper, NetworkThread.Info networkThreadInfo,
                               AppState appState, Name networkDataPrefix, PeerStateTable peerStateTable,
                               StreamConsumer.Options options) {

        appState_ = appState;
        options_ = options;

        networkDataPrefix_ = networkDataPrefix;
        peerStateTable_ = peerStateTable;

        ctx_ = ctx;
        fetchingQueue_ = new LinkedTransferQueue<>();
        playbackQueue_ = new LinkedTransferQueue<>();
        softFailureQueue_ = new LinkedTransferQueue<>();
        streamStates_ = new HashMap<>();
        networkThreadInfo_ = networkThreadInfo;

        eventStreamStateCreated = new SimpleEvent<>();

        progressEventHandler_ = new Handler(mainThreadLooper) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                ProgressEventInfo progressEventInfo = (ProgressEventInfo) msg.obj;
                Name streamName = progressEventInfo.streamName;
                InternalStreamConsumptionState streamState = streamStates_.get(streamName);

                if (streamState == null) {
                    Log.w(TAG, "streamState was null for msg (" +
                            "msg.what " + msg.what + ", " +
                            "stream name " + streamName.toString() +
                            ")");
                    return;
                }

                switch (msg.what) {
                    case MSG_STREAM_CONSUMER_FETCHING_COMPLETE: {
                        Log.d(TAG, "fetching of stream " + streamName.toString() + " finished");
                        break;
                    }
                    case MSG_STREAM_CONSUMER_FETCHING_FAILED: {

                        streamState.streamPlayer.close();
                        playbackQueue_.remove(streamName);
                        streamStates_.remove(streamName);
                        appState_.stopPlaying();

                        String failureTypeString = "";
                        switch ((int) progressEventInfo.arg1) {
                            case StreamConsumer.FAILURE_CODE_META_DATA_FETCH_FAILED: {
                                failureTypeString = "meta data fetch fail";
                                if (wifiConnectionState_ == WifiModule.DISCONNECTED) {
                                    softFailureQueue_.put(progressEventInfo.streamName);
                                }
                                break;
                            }
                            case StreamConsumer.FAILURE_CODE_SUCCESSFUL_DATA_FETCH_TIME_LIMIT_REACHED: {
                                failureTypeString = "successful data fetch time limit reached";
                                if (wifiConnectionState_ == WifiModule.DISCONNECTED) {
                                    softFailureQueue_.put(progressEventInfo.streamName);
                                }
                                break;
                            }
                            case StreamConsumer.FAILURE_CODE_STREAM_RECORDED_TOO_FAR_IN_PAST: {
                                failureTypeString = "stream recorded too far in past";
                                break;
                            }
                            default: {
                                throw new IllegalStateException("unexpected progressEventInfo.arg1 " + progressEventInfo.arg1);
                            }
                        }
                        Log.d(TAG, "playing of stream " + streamName.toString() + " finished (" + failureTypeString + ")");

                        break;
                    }
                    case MSG_STREAM_PLAYER_PLAYING_COMPLETE: {
                        Log.d(TAG, "playing of stream " + streamName.toString() + " finished");
                        streamState.streamPlayer.close();
                        streamStates_.remove(streamName);
                        appState_.stopPlaying();
                        break;
                    }
                    default: {
                        throw new IllegalStateException("unexpected msg.what " + msg.what);
                    }
                }
            }
        };

        moduleMessageHandler_ = new Handler(mainThreadLooper) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                switch (msg.what) {
                    case MSG_NEW_STREAM_AVAILABLE: {
                        SyncStreamInfo syncStreamInfo = (SyncStreamInfo) msg.obj;
                        Log.d(TAG, "Notified of new stream " + "(" +
                                syncStreamInfo.toString() +
                                ")");
                        fetchingQueue_.add(syncStreamInfo);
                        Name streamName = Helpers.getStreamName(networkDataPrefix_, syncStreamInfo);
                        playbackQueue_.add(streamName);
                        Logger.logEvent(new Logger.LogEventInfo(Logger.PQMODULE_NEW_STREAM_AVAILABLE, System.currentTimeMillis(),
                                0, Helpers.getStreamName(networkDataPrefix_, syncStreamInfo).toString(), null));
                        break;
                    }
                    case MSG_NEW_WIFI_STATE: {
                        wifiConnectionState_ = (Integer) msg.arg1;
                        Log.d(TAG, "wifi state changed to " + wifiConnectionState_);
                        if (wifiConnectionState_ == WifiModule.CONNECTED) {
                            Log.d(TAG, "wifi got connected, processing soft failures");
                            Message processSoftFailuresMsg = workHandler_.obtainMessage(MSG_PROCESS_SOFT_FAILURES);
                            workHandler_.sendMessageAtTime(processSoftFailuresMsg,
                                    SystemClock.uptimeMillis() + NetworkThread.ROUTE_REGISTRATION_DELAY_MS);
                        }
                    Logger.logEvent(new Logger.LogEventInfo(Logger.PQMODULE_NEW_WIFI_STATE, System.currentTimeMillis(),
                                wifiConnectionState_, null, null));
                        break;
                    }
                    default: {
                        throw new IllegalStateException("unexpected msg.what " + msg.what);
                    }
                }
            }
        };

        workHandler_ = new Handler(mainThreadLooper) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                switch (msg.what) {
                    case MSG_DO_SOME_WORK:
                    {
                        doSomeWork();
                        break;
                    }
                    case MSG_PROCESS_SOFT_FAILURES: {
                        processSoftFailures();
                        break;
                    }
                    default: {
                        throw new IllegalStateException("unexpected msg.what " + msg.what);
                    }
                }
            }
        };

        workHandler_.obtainMessage(MSG_DO_SOME_WORK).sendToTarget(); // start the work handler's work cycle

        Log.d(TAG, "PlaybackQueueModule constructed.");

    }

    public void notifyNewStreamAvailable(SyncStreamInfo syncStreamInfo) {
        moduleMessageHandler_.obtainMessage(MSG_NEW_STREAM_AVAILABLE, syncStreamInfo).sendToTarget();
    }

    public void notifyNewWifiState(int newWifiState) {
        moduleMessageHandler_.obtainMessage(MSG_NEW_WIFI_STATE, newWifiState, 0).sendToTarget();
    }

    private void processSoftFailures() {
        // check for streams for which fetching soft-failed (i.e. failed due to wifi disconnection), and add them
        // back into the fetching queue
        if (softFailureQueue_.size() != 0 && wifiConnectionState_ == CONNECTED) {
            Name streamName = softFailureQueue_.poll();
            ChannelUserSession channelUserSession = Helpers.getChannelUserSession(streamName);
            try {
                fetchingQueue_.add(new SyncStreamInfo(
                        channelUserSession.channelName,
                        channelUserSession.userName,
                        channelUserSession.sessionId,
                        streamName.get(-1).toSequenceNumber()
                ));
            }
            catch (Exception e) {
                throw new IllegalStateException("failed to parse name of soft failure " + streamName.toString());
            }
        }
    }

    private void doSomeWork() {

        // initiate the fetching of streams ready for fetching
        if (fetchingQueue_.size() != 0) {

            SyncStreamInfo syncStreamInfo = fetchingQueue_.poll();
            Name streamName = Helpers.getStreamName(networkDataPrefix_, syncStreamInfo);
            Log.d(TAG, "fetching queue was non empty, fetching stream " + streamName.toString());

            InputStreamDataSource transferSource = new InputStreamDataSource();

            StreamPlayer streamPlayer = new StreamPlayer(ctx_, transferSource,
                    streamName, progressEventHandler_);
            streamPlayer.eventPlayingCompleted.addListener(progressEventInfo ->
                progressEventHandler_
                        .obtainMessage(MSG_STREAM_PLAYER_PLAYING_COMPLETE, progressEventInfo)
                        .sendToTarget());

            StreamConsumer streamConsumer = new StreamConsumer(
                    networkDataPrefix_,
                    syncStreamInfo,
                    transferSource,
                    networkThreadInfo_,
                    peerStateTable_,
                    options_
            );
            InternalStreamConsumptionState internalStreamConsumptionState = new InternalStreamConsumptionState(streamConsumer, streamPlayer);
            streamStates_.put(streamName, internalStreamConsumptionState);
            streamConsumer.eventFetchingCompleted.addListener(progressEventInfo ->
                progressEventHandler_
                        .obtainMessage(MSG_STREAM_CONSUMER_FETCHING_COMPLETE, progressEventInfo)
                        .sendToTarget());
            streamConsumer.eventStreamFetchingFailure.addListener(progressEventInfo ->
                    progressEventHandler_
                        .obtainMessage(MSG_STREAM_CONSUMER_FETCHING_FAILED, progressEventInfo)
                        .sendToTarget());

            eventStreamStateCreated.trigger(new StreamNameAndStreamState(streamName, internalStreamConsumptionState));

            streamConsumer.streamFetchStart();

        }

        // initiate the playback of streams ready for playback
        if (playbackQueue_.size() != 0 && !appState_.isRecording() && !appState_.isPlaying()) {

            Name streamName = playbackQueue_.poll();

            Log.d(TAG, "playback queue was non empty, playing stream " + streamName.toString());

            InternalStreamConsumptionState streamState = streamStates_.get(streamName);
            streamState.streamConsumer.streamBufferStart();

            appState_.startPlaying();

        }

        scheduleNextWork(SystemClock.uptimeMillis());
    }

    private void scheduleNextWork(long thisOperationStartTimeMs) {
        workHandler_.removeMessages(MSG_DO_SOME_WORK);
        workHandler_.sendEmptyMessageAtTime(MSG_DO_SOME_WORK, thisOperationStartTimeMs + PROCESSING_INTERVAL_MS);
    }
}
