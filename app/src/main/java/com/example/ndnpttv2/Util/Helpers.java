package com.example.ndnpttv2.Util;

import com.example.ndnpttv2.back_end.StreamInfo;

import java.nio.ByteBuffer;

public class Helpers {

    // https://stackoverflow.com/questions/4485128/how-do-i-convert-long-to-byte-and-back-in-java
    public static long bytesToLong(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.put(bytes);
        buffer.flip();//need flip
        return buffer.getLong();
    }

    public static long getNumFrames(long finalBlockId, long framesPerSegment) {
        return finalBlockId * framesPerSegment + framesPerSegment - 1;
    }

    public static String getStreamInfoString(StreamInfo info) {
        return "stream name " + info.streamName + ", " +
                "frames per segment " + info.framesPerSegment + ", " +
                "producer sampling rate " + info.producerSamplingRate;
    }

}
