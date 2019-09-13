package com.example.ndnpttv2.back_end;

import com.example.ndnpttv2.back_end.sync_module.StreamMetaData;

import net.named_data.jndn.Name;

public class StreamInfo {

    public StreamInfo(Name streamName, long framesPerSegment, long producerSamplingRate,
                      long recordingStartTimestamp) {
        this.streamName = streamName;
        this.framesPerSegment = framesPerSegment;
        this.producerSamplingRate = producerSamplingRate;
        this.recordingStartTimestamp = recordingStartTimestamp;
    }

    public Name streamName;
    public long framesPerSegment;
    public long producerSamplingRate;
    public long recordingStartTimestamp;

    public StreamMetaData getMetaData() {
        return new StreamMetaData(framesPerSegment, producerSamplingRate, recordingStartTimestamp);
    }

    @Override
    public String toString() {
        return "streamName " + streamName.toString() + ", " +
                "framesPerSegment " + framesPerSegment + ", " +
                "producerSamplingRate " + producerSamplingRate + ", " +
                "recordingStartTimestamp " + recordingStartTimestamp;
    }
}
