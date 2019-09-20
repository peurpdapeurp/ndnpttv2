package com.example.ndnpttv2.back_end.structs;

import net.named_data.jndn.Name;

public class StreamInfo {

    public StreamInfo(Name streamName, long framesPerSegment, long producerSamplingRate, long recordingStartTime) {
        this.streamName = streamName;
        metaData = new StreamMetaData(framesPerSegment, producerSamplingRate, recordingStartTime);
    }

    public Name streamName;
    public StreamMetaData metaData;

    @Override
    public String toString() {
        return "streamName " + streamName.toString() + ", " +
                metaData.toString();
    }
}
