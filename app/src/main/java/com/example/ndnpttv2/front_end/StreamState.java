package com.example.ndnpttv2.front_end;

import com.example.ndnpttv2.back_end.pq_module.StreamInfo;

import net.named_data.jndn.Name;

public class StreamState {
    // Public constants
    public static final int FINAL_BLOCK_ID_UNKNOWN = -1;
    public static final int FINAL_FRAME_NUM_UNKNOWN = -1;
    public static final int NO_SEGMENTS_PRODUCED = -1;

    public StreamState(StreamInfo streamInfo) {
        this.streamName = streamInfo.streamName;
        this.framesPerSegment = streamInfo.framesPerSegment;
        this.producerSamplingRate = streamInfo.producerSamplingRate;
    }

    public Name streamName;
    public long framesPerSegment;
    public long producerSamplingRate;
    public long finalBlockId = FINAL_BLOCK_ID_UNKNOWN;
    public long highestSegAnticipated = NO_SEGMENTS_PRODUCED;
    public long finalFrameNum = FINAL_FRAME_NUM_UNKNOWN;
    public long segmentsFetched = 0;
    public long interestsSkipped = 0;
    public long nacksFetched = 0;
    public long framesPlayed = 0;
    public long framesSkipped = 0;
}
