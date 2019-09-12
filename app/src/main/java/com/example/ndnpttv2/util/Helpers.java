package com.example.ndnpttv2.util;

import com.example.ndnpttv2.back_end.pq_module.StreamInfo;
import com.example.ndnpttv2.back_end.sync_module.StreamMetaData;

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

}
