package com.example.ndnpttv2.back_end;

import com.example.ndnpttv2.back_end.sc_module.stream_consumer.StreamConsumer;
import com.example.ndnpttv2.back_end.sc_module.stream_player.StreamPlayer;

public class StreamState {

    // Public constants
    public static final int FINAL_BLOCK_ID_UNKNOWN = -1;
    public static final int FINAL_FRAME_NUM_UNKNOWN = -1;
    public static final int NO_SEGMENTS_PRODUCED = -1;
    public static final int FRAMES_PER_SEGMENT_UNKNOWN = -1;

    public StreamState(StreamConsumer streamConsumer, StreamPlayer streamPlayer,
                        long framesPerSegment, long producerSamplingRate) {
        this.streamConsumer = streamConsumer;
        this.streamPlayer = streamPlayer;
        this.framesPerSegment = framesPerSegment;
        this.producerSamplingRate = producerSamplingRate;
    }

    public StreamConsumer streamConsumer;
    public StreamPlayer streamPlayer;
    public long finalBlockId = FINAL_BLOCK_ID_UNKNOWN;
    public long highestSegAnticipated = NO_SEGMENTS_PRODUCED;
    public long finalFrameNum = FINAL_FRAME_NUM_UNKNOWN;
    public long framesPerSegment;
    public long producerSamplingRate;
    public long segmentsFetched = 0;
    public long interestsSkipped = 0;
    public long nacksFetched = 0;
    public long framesPlayed = 0;
    public long framesSkipped = 0;

}
