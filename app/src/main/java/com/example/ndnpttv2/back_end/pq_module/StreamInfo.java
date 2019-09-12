package com.example.ndnpttv2.back_end.pq_module;

import net.named_data.jndn.Name;

public class StreamInfo {

    public StreamInfo(Name streamName, long framesPerSegment, long producerSamplingRate) {
        this.streamName = streamName;
        this.framesPerSegment = framesPerSegment;
        this.producerSamplingRate = producerSamplingRate;
    }

    public Name streamName;
    public long framesPerSegment;
    public long producerSamplingRate;

    @Override
    public String toString() {
        return "streamName " + streamName.toString() + ", " +
                "framesPerSegment " + framesPerSegment + ", " +
                "producerSamplingRate " + producerSamplingRate;
    }
}
