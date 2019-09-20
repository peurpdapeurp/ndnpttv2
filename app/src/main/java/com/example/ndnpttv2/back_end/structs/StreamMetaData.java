package com.example.ndnpttv2.back_end.structs;

public class StreamMetaData {

    public StreamMetaData(long framesPerSegment, long producerSamplingRate, long recordingStartTime) {
        this.framesPerSegment = framesPerSegment;
        this.producerSamplingRate = producerSamplingRate;
        this.recordingStartTime = recordingStartTime;
    }

    public long framesPerSegment;
    public long producerSamplingRate;
    public long recordingStartTime;

    @Override
    public String toString() {
        return "framesPerSegment " + framesPerSegment + ", " +
                "producerSamplingRate " + producerSamplingRate + ", " +
                "recordingStartTime " + recordingStartTime;
    }
}
