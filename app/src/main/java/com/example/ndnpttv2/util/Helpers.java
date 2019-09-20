package com.example.ndnpttv2.util;

import com.example.ndnpttv2.back_end.structs.SyncStreamInfo;

import net.named_data.jndn.Name;

import java.nio.ByteBuffer;

import static com.example.ndnpttv2.back_end.Constants.META_DATA_MARKER;

public class Helpers {

    public static Name getStreamName(Name networkDataPrefix, SyncStreamInfo syncStreamInfo) {
        return new Name(networkDataPrefix)
                .append(syncStreamInfo.channelUserSession.channelName)
                .append(syncStreamInfo.channelUserSession.userName)
                .append(Long.toString(syncStreamInfo.channelUserSession.sessionId))
                .appendSequenceNumber(syncStreamInfo.seqNum);
    }

    public static Name getStreamMetaDataName(Name networkDataPrefix, SyncStreamInfo syncStreamInfo) {
        return new Name(networkDataPrefix)
                .append(syncStreamInfo.channelUserSession.channelName)
                .append(syncStreamInfo.channelUserSession.userName)
                .append(Long.toString(syncStreamInfo.channelUserSession.sessionId))
                .appendSequenceNumber(syncStreamInfo.seqNum)
                .append(META_DATA_MARKER);
    }

    // https://stackoverflow.com/questions/4485128/how-do-i-convert-long-to-byte-and-back-in-java
    public static long bytesToLong(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.put(bytes);
        buffer.flip();//need flip
        return buffer.getLong();
    }

    // https://stackoverflow.com/questions/4485128/how-do-i-convert-long-to-byte-and-back-in-java
    public static byte[] longToBytes(long x) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(x);
        return buffer.array();
    }

    public static long getNumFrames(long finalBlockId, long framesPerSegment) {
        return finalBlockId * framesPerSegment + framesPerSegment - 1;
    }

    public static final byte[] temp_key = new byte[] {1, 1, 1, 1, 1, 1, 1, 1, 1, 1};

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

}
