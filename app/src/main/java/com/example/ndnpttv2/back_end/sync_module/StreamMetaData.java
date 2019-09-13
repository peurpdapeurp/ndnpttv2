package com.example.ndnpttv2.back_end.sync_module;

public class StreamMetaData {

    public StreamMetaData(long framesPerSegment, long producerSamplingRate, long recordingStartTimeStamp) {
        this.framesPerSegment = framesPerSegment;
        this.producerSamplingRate = producerSamplingRate;
        this.recordingStartTimestamp = recordingStartTimeStamp;
    }

    public long framesPerSegment;
    public long producerSamplingRate;
    public long recordingStartTimestamp;

    @Override
    public String toString() {
        return "framesPerSegment " + framesPerSegment + ", " +
                "producerSamplingRate " + producerSamplingRate + ", " +
                "recordingStartTimestamp " + recordingStartTimestamp;
    }
}
