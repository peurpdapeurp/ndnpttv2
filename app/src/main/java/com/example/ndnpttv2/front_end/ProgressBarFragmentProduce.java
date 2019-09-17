package com.example.ndnpttv2.front_end;

import android.os.Looper;
import android.os.Message;

import com.example.ndnpttv2.R;
import com.example.ndnpttv2.back_end.ProgressEventInfo;
import com.example.ndnpttv2.back_end.StreamInfo;
import com.example.ndnpttv2.back_end.rec_module.RecorderModule;
import com.example.ndnpttv2.back_end.rec_module.stream_producer.StreamProducer;

import net.named_data.jndn.Name;

public class ProgressBarFragmentProduce extends ProgressBarFragment {

    // Messages
    private static final int MSG_STREAM_PRODUCER_SEGMENT_PUBLISHED = 0;
    private static final int MSG_STREAM_PRODUCER_FINAL_SEGMENT_PUBLISHED = 1;

    StreamState state_;

    private class StreamState {
        static final int FINAL_BLOCK_ID_UNKNOWN = -1;
        static final int NO_SEGMENTS_PUBLISHED = -1;

        StreamState(StreamInfo streamInfo) {
            this.streamName = streamInfo.streamName;
            this.framesPerSegment = streamInfo.framesPerSegment;
            this.producerSamplingRate = streamInfo.producerSamplingRate;
        }

        Name streamName;
        long framesPerSegment;
        long producerSamplingRate;
        long finalBlockId = FINAL_BLOCK_ID_UNKNOWN;
        long numSegsPublished = 0;
        long highestSegPublished = NO_SEGMENTS_PUBLISHED;
    }

    public ProgressBarFragmentProduce(RecorderModule.StreamInfoAndStreamState streamInfoAndStreamState,
                                      Looper mainThreadLooper) {
        super(streamInfoAndStreamState.streamInfo.streamName, mainThreadLooper);

        state_ = new StreamState(streamInfoAndStreamState.streamInfo);

        StreamProducer streamProducer = streamInfoAndStreamState.streamState.streamProducer;
        streamProducer.eventSegmentPublished.addListener(progressEventInfo ->
                processProgressEvent(MSG_STREAM_PRODUCER_SEGMENT_PUBLISHED, progressEventInfo));
        streamProducer.eventFinalSegmentPublished.addListener(progressEventInfo ->
                processProgressEvent(MSG_STREAM_PRODUCER_FINAL_SEGMENT_PUBLISHED, progressEventInfo));



    }

    @Override
    void handleMessageInternal(Message msg) {

        ProgressEventInfo progressEventInfo = (ProgressEventInfo) msg.obj;

        switch (msg.what) {
            case MSG_STREAM_PRODUCER_SEGMENT_PUBLISHED: {
                state_.numSegsPublished++;
                if (progressEventInfo.arg1 > state_.highestSegPublished) {
                    state_.highestSegPublished = progressEventInfo.arg1;
                }
                updateProgressBar(msg.what, progressEventInfo.arg1, state_);
                break;
            }
            case MSG_STREAM_PRODUCER_FINAL_SEGMENT_PUBLISHED: {
                state_.finalBlockId = progressEventInfo.arg1;
                updateProgressBar(msg.what, 0, state_);
                break;
            }
            default: {
                throw new IllegalStateException("unexpected msg.what " + msg.what);
            }
        }
    }

    @Override
    Name getStreamName() {
        return state_.streamName;
    }

    void updateProgressBar(int msg_what, long arg1, StreamState streamState) {
        boolean finalBlockIdKnown = streamState.finalBlockId != StreamState.FINAL_BLOCK_ID_UNKNOWN;

        // rescaling logic
        if (finalBlockIdKnown) {
            progressBar_.setTotalSegments((int) streamState.finalBlockId + 1);
        }
        if (!finalBlockIdKnown &&
                ((float) streamState.highestSegPublished / (float) progressBar_.getTotalSegments()) > 0.90f) {
            progressBar_.setTotalSegments(progressBar_.getTotalSegments() * 2);
        }

        switch (msg_what) {
            case MSG_STREAM_PRODUCER_SEGMENT_PUBLISHED: {
                progressBar_.updateSingleSegmentColor((int) arg1, R.color.blue);
                break;
            }
            case MSG_STREAM_PRODUCER_FINAL_SEGMENT_PUBLISHED: {
                break;
            }
            default: {
                throw new IllegalStateException("unexpected msg_what " + msg_what);
            }
        }

    }
}
