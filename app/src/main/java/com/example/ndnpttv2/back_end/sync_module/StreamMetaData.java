package com.example.ndnpttv2.back_end.sync_module;

public class StreamMetaData {

    public StreamMetaData(long framesPerSegment, long producerSamplingRate) {
        this.framesPerSegment = framesPerSegment;
        this.producerSamplingRate = producerSamplingRate;
    }

    public long framesPerSegment;
    public long producerSamplingRate;

    @Override
    public String toString() {
        return "framesPerSegment " + framesPerSegment + ", " +
                "producerSamplingRate " + producerSamplingRate;
    }
}
