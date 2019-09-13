package com.example.ndnpttv2.back_end.rec_module;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.ndnpttv2.back_end.ProgressEventInfo;
import com.example.ndnpttv2.back_end.Threads.NetworkThread;
import com.example.ndnpttv2.back_end.pq_module.StreamInfo;
import com.example.ndnpttv2.back_end.rec_module.stream_producer.StreamProducer;

import net.named_data.jndn.Name;

import java.util.HashMap;

public class RecorderModule {

    private static final String TAG = "RecorderModule";

    // Private constants
    private static final int PROCESSING_INTERVAL_MS = 50;

    // Messages
    private static final int MSG_DO_SOME_WORK = 0;
    private static final int MSG_RECORDING_COMPLETE = 1;
    private static final int MSG_RECORD_REQUEST_START = 2;
    private static final int MSG_RECORD_REQUEST_STOP = 3;

    private Handler progressEventHandler_;
    private Handler moduleMessageHandler_;
    private HashMap<Name, InternalStreamProductionState> pastStreamProducers_;
    private StreamProducer currentStreamProducer_;
    private boolean currentlyRecording_ = false;
    private Name applicationDataPrefix_;
    private NetworkThread.Info networkThreadInfo_;
    private long lastStreamId_ = 0;

    public RecorderModule(Name applicationDataPrefix, NetworkThread.Info networkThreadInfo) {

        applicationDataPrefix_ = applicationDataPrefix;
        networkThreadInfo_ = networkThreadInfo;
        pastStreamProducers_ = new HashMap<>();

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
                        currentlyRecording_ = false;
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
                        if (currentlyRecording_) {
                            Log.e(TAG, "Got request to record while already recording, ignoring request.");
                            return;
                        }
                        Log.d(TAG, "Got request to start recording, last stream id " + lastStreamId_);
                        currentStreamProducer_ = new StreamProducer(applicationDataPrefix_, ++lastStreamId_,
                                networkThreadInfo_,
                                new StreamProducer.Options(1, 8000, 5));
                        currentStreamProducer_.eventFinalSegmentRecorded.addListener(progressEventInfo -> {
                            progressEventHandler_
                                    .obtainMessage(MSG_RECORDING_COMPLETE, progressEventInfo)
                                    .sendToTarget();
                        });
                        pastStreamProducers_.put(new Name(applicationDataPrefix_).appendSequenceNumber(lastStreamId_),
                                new InternalStreamProductionState(currentStreamProducer_));
                        currentStreamProducer_.recordStart();
                        currentlyRecording_ = true;
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
