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
import com.example.ndnpttv2.back_end.structs.SyncStreamInfo;
import com.example.ndnpttv2.back_end.threads.NetworkThread;
import com.example.ndnpttv2.back_end.pq_module.stream_consumer.StreamConsumer;
import com.example.ndnpttv2.back_end.pq_module.stream_player.StreamPlayer;
import com.example.ndnpttv2.back_end.pq_module.stream_player.exoplayer_customization.InputStreamDataSource;
import com.example.ndnpttv2.back_end.structs.ProgressEventInfo;
import com.example.ndnpttv2.util.Helpers;
import com.example.ndnpttv2.util.Logger;
import com.pploder.events.Event;
import com.pploder.events.SimpleEvent;

import net.named_data.jndn.Name;

import java.util.HashMap;
import java.util.concurrent.LinkedTransferQueue;

import static com.example.ndnpttv2.util.Logger.DebugInfo.LOG_DEBUG;
import static com.example.ndnpttv2.util.Logger.DebugInfo.LOG_ERROR;

public class PlaybackQueueModule {

    private static final String TAG = "PlaybackQueueModule";

    // Private constants
    private static final int PROCESSING_INTERVAL_MS = 50;

    // Messages
    private static final int MSG_DO_SOME_WORK = 0;
    private static final int MSG_STREAM_CONSUMER_FETCHING_COMPLETE = 1;
    private static final int MSG_STREAM_PLAYER_PLAYING_COMPLETE = 2;
    private static final int MSG_NEW_STREAM_AVAILABLE = 3;

    // Events
    public Event<InternalStreamConsumptionState> eventStreamStateCreated;

    private PeerStateTable peerStateTable_;
    private AppState appState_;

    private Context ctx_;
    private Handler progressEventHandler_;
    private Handler moduleMessageHandler_;
    private Handler workHandler_;

    private Name networkDataPrefix_;
    private LinkedTransferQueue<Long> fetchingQueue_;
    private LinkedTransferQueue<Long> playbackQueue_;
    private HashMap<Long, InternalStreamConsumptionState> streamStates_;
    private NetworkThread.Info networkThreadInfo_;
    private StreamConsumer.Options options_;
    private long currentProgressTrackerId_ = 0;

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
        streamStates_ = new HashMap<>();
        networkThreadInfo_ = networkThreadInfo;

        eventStreamStateCreated = new SimpleEvent<>();

        progressEventHandler_ = new Handler(mainThreadLooper) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                ProgressEventInfo progressEventInfo = (ProgressEventInfo) msg.obj;
                Name streamName = progressEventInfo.streamName;
                InternalStreamConsumptionState streamState = streamStates_.get(progressEventInfo.progressTrackerId);

                if (streamState == null) {
                    Log.w(TAG, "streamState was null for msg (" +
                            "msg.what " + msg.what + ", " +
                            "progress tracker id " + progressEventInfo.progressTrackerId + ", " +
                            "stream name " + streamName.toString() +
                            ")");
                    return;
                }

                switch (msg.what) {
                    case MSG_STREAM_CONSUMER_FETCHING_COMPLETE: {
                        if (progressEventInfo.arg1 == StreamConsumer.FETCH_COMPLETE_CODE_SUCCESS) {
                            Logger.logDebugEvent(TAG, LOG_DEBUG, "fetching of stream " + streamName.toString() + " finished",System.currentTimeMillis());
                        }
                        else {
                            streamState.streamPlayer.close();
                            playbackQueue_.remove(progressEventInfo.progressTrackerId);
                            streamStates_.remove(progressEventInfo.progressTrackerId);
                            appState_.stopPlaying();

                            String failureTypeString = "";
                            switch ((int) progressEventInfo.arg1) {
                                case StreamConsumer.FETCH_COMPLETE_CODE_META_DATA_TIMEOUT: {
                                    failureTypeString = "meta data timeout";
                                    break;
                                }
                                case StreamConsumer.FETCH_COMPLETE_CODE_MEDIA_DATA_TIMEOUT: {
                                    failureTypeString = "media data timeout";
                                    break;
                                }
                                case StreamConsumer.FETCH_COMPLETE_CODE_STREAM_RECORDED_TOO_FAR_IN_PAST: {
                                    failureTypeString = "stream recorded too far in past";
                                    break;
                                }
                                default: {
                                    Logger.logDebugEvent(TAG,LOG_ERROR,"unexpected progressEventInfo.arg1 " + progressEventInfo.arg1,System.currentTimeMillis());
                                    throw new IllegalStateException("unexpected progressEventInfo.arg1 " + progressEventInfo.arg1);
                                }
                            }
                            Logger.logDebugEvent(TAG, LOG_DEBUG, "playing of stream " + streamName.toString() + " failed (" + failureTypeString + ")",System.currentTimeMillis());
                        }
                        break;
                    }
                    case MSG_STREAM_PLAYER_PLAYING_COMPLETE: {
                        Logger.logDebugEvent(TAG, LOG_DEBUG, "playing of stream " + streamName.toString() + " finished",System.currentTimeMillis());
                        streamState.streamPlayer.close();
                        streamStates_.remove(progressEventInfo.progressTrackerId);
                        appState_.stopPlaying();
                        break;
                    }
                    default: {
                        Logger.logDebugEvent(TAG,LOG_ERROR,"unexpected msg.what " + msg.what,System.currentTimeMillis());
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
                        fetchingQueue_.add(currentProgressTrackerId_);
                        playbackQueue_.add(currentProgressTrackerId_);
                        Logger.logEvent(new Logger.LogEventInfo(Logger.PQMODULE_NEW_STREAM_AVAILABLE, System.currentTimeMillis(),
                                0, Helpers.getStreamName(networkDataPrefix_, syncStreamInfo).toString(), null));
                        Name streamName = Helpers.getStreamName(networkDataPrefix_, syncStreamInfo);
                        InternalStreamConsumptionState consumptionState = new InternalStreamConsumptionState(streamName, null, null);
                        streamStates_.put(currentProgressTrackerId_, consumptionState);
                        currentProgressTrackerId_++;
                        break;
                    }
                    default: {
                        Logger.logDebugEvent(TAG,LOG_ERROR,"unexpected msg.what " + msg.what,System.currentTimeMillis());
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
                    default: {
                        Logger.logDebugEvent(TAG,LOG_ERROR,"unexpected msg.what " + msg.what,System.currentTimeMillis());
                        throw new IllegalStateException("unexpected msg.what " + msg.what);
                    }
                }
            }
        };

        workHandler_.obtainMessage(MSG_DO_SOME_WORK).sendToTarget(); // start the work handler's work cycle

        Logger.logDebugEvent(TAG, LOG_DEBUG, "PlaybackQueueModule constructed.",System.currentTimeMillis());

    }

    public void notifyNewStreamAvailable(SyncStreamInfo syncStreamInfo) {
        moduleMessageHandler_.obtainMessage(MSG_NEW_STREAM_AVAILABLE, syncStreamInfo).sendToTarget();
    }

    public void notifyRefetchRequest(Name streamName) {
        moduleMessageHandler_.obtainMessage(MSG_NEW_STREAM_AVAILABLE, Helpers.getSyncStreamInfo(streamName)).sendToTarget();
    }

    private void doSomeWork() {

        // initiate the fetching of streams ready for fetching
        if (fetchingQueue_.size() != 0) {

            Long progressTrackerId = fetchingQueue_.poll();
            InternalStreamConsumptionState consumptionState = streamStates_.get(progressTrackerId);
            Name streamName = consumptionState.streamName;
            SyncStreamInfo syncStreamInfo = Helpers.getSyncStreamInfo(streamName);
            Logger.logDebugEvent(TAG, LOG_DEBUG, "fetching queue was non empty, fetching stream " + streamName.toString(),System.currentTimeMillis());

            InputStreamDataSource transferSource = new InputStreamDataSource();

            StreamPlayer streamPlayer = new StreamPlayer(ctx_, transferSource,
                    progressTrackerId, streamName);
            streamPlayer.eventPlayingCompleted.addListener(progressEventInfo ->
                progressEventHandler_
                        .obtainMessage(MSG_STREAM_PLAYER_PLAYING_COMPLETE, progressEventInfo)
                        .sendToTarget());

            StreamConsumer streamConsumer = new StreamConsumer(
                    progressTrackerId,
                    networkDataPrefix_,
                    syncStreamInfo,
                    transferSource,
                    networkThreadInfo_,
                    peerStateTable_,
                    options_
            );

            consumptionState.streamPlayer = streamPlayer;
            consumptionState.streamConsumer = streamConsumer;

            streamConsumer.eventFetchingCompleted.addListener(progressEventInfo ->
                progressEventHandler_
                        .obtainMessage(MSG_STREAM_CONSUMER_FETCHING_COMPLETE, progressEventInfo)
                        .sendToTarget());
            eventStreamStateCreated.trigger(consumptionState);

            streamConsumer.streamFetchStart();
        }

        // initiate the playback of streams ready for playback
        if (playbackQueue_.size() != 0 && !appState_.isRecording() && !appState_.isPlaying()) {

            Long progressTrackerId = playbackQueue_.poll();
            InternalStreamConsumptionState consumptionState = streamStates_.get(progressTrackerId);

            Logger.logDebugEvent(TAG, LOG_DEBUG, "playback queue was non empty, playing stream " + consumptionState.streamName.toString(),System.currentTimeMillis());

            consumptionState.streamConsumer.streamBufferStart();

            appState_.startPlaying();

        }

        scheduleNextWork(SystemClock.uptimeMillis());
    }

    private void scheduleNextWork(long thisOperationStartTimeMs) {
        workHandler_.removeMessages(MSG_DO_SOME_WORK);
        workHandler_.sendEmptyMessageAtTime(MSG_DO_SOME_WORK, thisOperationStartTimeMs + PROCESSING_INTERVAL_MS);
    }
}
