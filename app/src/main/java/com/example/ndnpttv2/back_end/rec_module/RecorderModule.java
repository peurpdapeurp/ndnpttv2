package com.example.ndnpttv2.back_end.rec_module;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.ndnpttv2.back_end.AppState;
import com.example.ndnpttv2.back_end.ProgressEventInfo;
import com.example.ndnpttv2.back_end.StreamInfo;
import com.example.ndnpttv2.back_end.threads.NetworkThread;
import com.example.ndnpttv2.back_end.rec_module.stream_producer.StreamProducer;
import com.pploder.events.Event;
import com.pploder.events.SimpleEvent;

import net.named_data.jndn.Name;

import java.util.HashMap;

public class RecorderModule {

    private static final String TAG = "RecorderModule";

    // Messages
    private static final int MSG_RECORDING_COMPLETE = 0;
    private static final int MSG_RECORD_REQUEST_START = 1;
    private static final int MSG_RECORD_REQUEST_STOP = 2;

    // Events
    public Event<StreamInfo> eventRecordingStarted;
    public Event<Name> eventRecordingFinished;

    private Handler progressEventHandler_;
    private Handler moduleMessageHandler_;
    private HashMap<Name, InternalStreamProductionState> pastStreamProducers_;
    private StreamProducer currentStreamProducer_;
    private Name applicationDataPrefix_;
    private NetworkThread.Info networkThreadInfo_;
    private long lastStreamId_ = 0;

    private AppState appState_;

    public RecorderModule(Name applicationDataPrefix, NetworkThread.Info networkThreadInfo,
                          AppState appState) {

        appState_ = appState;

        applicationDataPrefix_ = applicationDataPrefix;
        networkThreadInfo_ = networkThreadInfo;
        pastStreamProducers_ = new HashMap<>();

        eventRecordingStarted = new SimpleEvent<>();
        eventRecordingFinished = new SimpleEvent<>();

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
                        Log.d(TAG, "recording of stream " + streamName.toString() + " finished");
                        appState_.stopRecording();
                        eventRecordingFinished.trigger(streamName);
                        break;
                    }
                    default: {
                        throw new IllegalStateException("unexpected msg.what " + msg.what);
                    }
                }
            }
        };

        moduleMessageHandler_ = new Handler(networkThreadInfo_.looper) {
            @Override
            public void handleMessage(@NonNull Message msg) {

                Log.d(TAG, "got message " + msg.what);

                switch (msg.what) {
                    case MSG_RECORD_REQUEST_START: {
                        if (appState_.isRecording()) {
                            Log.e(TAG, "Got request to record while already recording, ignoring request.");
                            return;
                        }
                        if (appState_.isPlaying()) {
                            Log.e(TAG, "Got request to record while PlaybackQueueModule was playing, ignoring request.");
                            return;
                        }

                        Log.d(TAG, "Got valid request to start recording, last stream id " + lastStreamId_);

                        lastStreamId_++;

                        currentStreamProducer_ = new StreamProducer(applicationDataPrefix_, lastStreamId_,
                                networkThreadInfo_,
                                new StreamProducer.Options(1, 8000, 5));
                        currentStreamProducer_.eventFinalSegmentPublished.addListener(progressEventInfo -> {
                            progressEventHandler_
                                    .obtainMessage(MSG_RECORDING_COMPLETE, progressEventInfo)
                                    .sendToTarget();
                        });
                        pastStreamProducers_.put(new Name(applicationDataPrefix_).appendSequenceNumber(lastStreamId_),
                                new InternalStreamProductionState(currentStreamProducer_));
                        currentStreamProducer_.recordStart();
                        eventRecordingStarted.trigger(
                                new StreamInfo(
                                        new Name(applicationDataPrefix_).appendSequenceNumber(lastStreamId_),
                                        1,
                                        8000,
                                        System.currentTimeMillis()
                                )
                        );

                        appState_.startRecording();
                        break;
                    }
                    case MSG_RECORD_REQUEST_STOP: {
                        currentStreamProducer_.recordStop();
                        break;
                    }
                    default: {
                        throw new IllegalStateException("unexpected msg.what " + msg.what);
                    }
                }
            }
        };

        Log.d(TAG, "RecorderModule constructed.");

    }

    public void recordRequestStart() {
        moduleMessageHandler_
                .obtainMessage(MSG_RECORD_REQUEST_START)
                .sendToTarget();
    }

    public void recordRequestStop() {
        moduleMessageHandler_
                .obtainMessage(MSG_RECORD_REQUEST_STOP)
                .sendToTarget();
    }


}
