package com.example.ndnpttv2.back_end.rec_module;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.ndnpttv2.back_end.shared_state.AppState;
import com.example.ndnpttv2.back_end.structs.ProgressEventInfo;
import com.example.ndnpttv2.back_end.structs.StreamInfo;
import com.example.ndnpttv2.back_end.threads.NetworkThread;
import com.example.ndnpttv2.back_end.rec_module.stream_producer.StreamProducer;
import com.example.ndnpttv2.util.Logger;
import com.pploder.events.Event;
import com.pploder.events.SimpleEvent;

import net.named_data.jndn.Name;

import java.util.HashMap;

import static com.example.ndnpttv2.util.Logger.DebugInfo.LOG_DEBUG;
import static com.example.ndnpttv2.util.Logger.DebugInfo.LOG_ERROR;

public class RecorderModule {

    private static final String TAG = "RecorderModule";

    // Public constants
    public static final int REQUEST_VALID = 0;
    public static final int REQUEST_INVALID = 1;

    // Messages
    private static final int MSG_RECORDING_COMPLETE = 0;
    private static final int MSG_RECORD_REQUEST_START = 1;
    private static final int MSG_RECORD_REQUEST_STOP = 2;

    // Events
    public Event<StreamInfo> eventRecordingStarted;
    public Event<StreamInfoAndStreamState> eventStreamStateCreated;
    public Event<Object> eventRecordingStartRequestIgnored;

    private Handler progressEventHandler_;
    private Handler moduleMessageHandler_;
    private HashMap<Name, InternalStreamProductionState> pastStreamProducers_;
    private StreamProducer currentStreamProducer_;
    private Name applicationDataPrefix_;
    private NetworkThread.Info networkThreadInfo_;
    private long lastStreamId_ = 0;

    private AppState appState_;
    private Options options_;

    public static class Options {
        public Options(int producerSamplingRate, int framesPerSegment) {
            this.producerSamplingRate = producerSamplingRate;
            this.framesPerSegment = framesPerSegment;
        }
        int producerSamplingRate;
        int framesPerSegment;
    }

    public static class StreamInfoAndStreamState {
        StreamInfoAndStreamState(StreamInfo streamInfo, InternalStreamProductionState streamState) {
            this.streamInfo = streamInfo;
            this.streamState = streamState;
        }
        public StreamInfo streamInfo;
        public InternalStreamProductionState streamState;
    }

    public RecorderModule(Name applicationDataPrefix, NetworkThread.Info networkThreadInfo,
                          AppState appState, Options options) {

        appState_ = appState;
        options_ = options;

        applicationDataPrefix_ = applicationDataPrefix;
        networkThreadInfo_ = networkThreadInfo;
        pastStreamProducers_ = new HashMap<>();

        eventRecordingStarted = new SimpleEvent<>();
        eventStreamStateCreated = new SimpleEvent<>();
        eventRecordingStartRequestIgnored = new SimpleEvent<>();

        progressEventHandler_ = new Handler(networkThreadInfo_.looper) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                ProgressEventInfo progressEventInfo = (ProgressEventInfo) msg.obj;
                Name streamName = progressEventInfo.streamName;
                InternalStreamProductionState streamState = pastStreamProducers_.get(streamName);

                if (streamState == null) {
                    Log.w(TAG, "streamState was null for msg (" +
                            "msg.what " + msg.what + ", " +
                            "stream name " + streamName.toString() +
                            ")");
                    return;
                }

                switch (msg.what) {
                    case MSG_RECORDING_COMPLETE: {
                        Logger.logDebugEvent(TAG, LOG_DEBUG, "recording of stream " + streamName.toString() + " finished",System.currentTimeMillis());
                        appState_.stopRecording();
                        break;
                    }
                    default: {
                        Logger.logDebugEvent(TAG,LOG_ERROR,"unexpected msg.what " + msg.what,System.currentTimeMillis());
                        throw new IllegalStateException("unexpected msg.what " + msg.what);
                    }
                }
            }
        };

        moduleMessageHandler_ = new Handler(networkThreadInfo_.looper) {
            @Override
            public void handleMessage(@NonNull Message msg) {

                Logger.logDebugEvent(TAG, LOG_DEBUG, "got message " + msg.what,System.currentTimeMillis());

                switch (msg.what) {
                    case MSG_RECORD_REQUEST_START: {
                        if (appState_.isRecording()) {
                            Logger.logDebugEvent(TAG,LOG_ERROR, "Got request to record while already recording, ignoring request.",System.currentTimeMillis());
                            eventRecordingStartRequestIgnored.trigger();
                            Logger.logEvent(new Logger.LogEventInfo(Logger.RECMODULE_RECORD_REQUEST_START, System.currentTimeMillis(),
                                    REQUEST_INVALID, null, null));
                            return;
                        }
                        if (appState_.isPlaying()) {
                            Logger.logDebugEvent(TAG,LOG_ERROR, "Got request to record while PlaybackQueueModule was playing, ignoring request.",System.currentTimeMillis());
                            eventRecordingStartRequestIgnored.trigger();
                            Logger.logEvent(new Logger.LogEventInfo(Logger.RECMODULE_RECORD_REQUEST_START, System.currentTimeMillis(),
                                    REQUEST_INVALID, null, null));
                            return;
                        }

                        Logger.logDebugEvent(TAG, LOG_DEBUG, "Got valid request to start recording, last stream id " + lastStreamId_,System.currentTimeMillis());

                        lastStreamId_++;

                        long recordingStartTime = System.currentTimeMillis();
                        Name streamName = new Name(applicationDataPrefix_).appendSequenceNumber(lastStreamId_);
                        StreamInfo streamInfo = new StreamInfo(
                                streamName,
                                options_.framesPerSegment,
                                options_.producerSamplingRate,
                                recordingStartTime
                        );

                        currentStreamProducer_ = new StreamProducer(applicationDataPrefix_, lastStreamId_,
                                networkThreadInfo_,
                                new StreamProducer.Options(options_.framesPerSegment, options_.producerSamplingRate, recordingStartTime));
                        currentStreamProducer_.eventFinalSegmentPublished.addListener(progressEventInfo ->
                            progressEventHandler_
                                    .obtainMessage(MSG_RECORDING_COMPLETE, progressEventInfo)
                                    .sendToTarget());

                        InternalStreamProductionState state = new InternalStreamProductionState(currentStreamProducer_);

                        pastStreamProducers_.put(streamName, state);

                        eventStreamStateCreated.trigger(new StreamInfoAndStreamState(streamInfo, state));

                        currentStreamProducer_.recordStart();
                        eventRecordingStarted.trigger(streamInfo);

                        appState_.startRecording();

                        Logger.logEvent(new Logger.LogEventInfo(Logger.RECMODULE_RECORD_REQUEST_START, System.currentTimeMillis(),
                                REQUEST_VALID, null, null));
                        break;
                    }
                    case MSG_RECORD_REQUEST_STOP: {
                        if (currentStreamProducer_ != null)
                            currentStreamProducer_.recordStop();
                        Logger.logEvent(new Logger.LogEventInfo(Logger.RECMODULE_RECORD_REQUEST_STOP, System.currentTimeMillis(),
                                0, null, null));
                        break;
                    }
                    default: {
                        Logger.logDebugEvent(TAG,LOG_ERROR,"unexpected msg.what " + msg.what,System.currentTimeMillis());
                        throw new IllegalStateException("unexpected msg.what " + msg.what);
                    }
                }
            }
        };

        Logger.logDebugEvent(TAG, LOG_DEBUG, "RecorderModule constructed.",System.currentTimeMillis());

    }

    public void notifyRecordRequestStart() {
        moduleMessageHandler_
                .obtainMessage(MSG_RECORD_REQUEST_START)
                .sendToTarget();
    }

    public void notifyRecordRequestStop() {
        moduleMessageHandler_
                .obtainMessage(MSG_RECORD_REQUEST_STOP)
                .sendToTarget();
    }


}
