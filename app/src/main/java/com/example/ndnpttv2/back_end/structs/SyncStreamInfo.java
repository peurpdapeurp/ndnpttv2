package com.example.ndnpttv2.back_end.structs;

import com.example.ndnpttv2.back_end.structs.ChannelUserSession;

public class SyncStreamInfo {
    public SyncStreamInfo(String channelName, String userName, long sessionId, long seqNum) {
        this.channelUserSession = new ChannelUserSession(channelName, userName, sessionId);
        this.seqNum = seqNum;
    }
    public ChannelUserSession channelUserSession;
    public long seqNum;

    @Override
    public String toString() {
        return channelUserSession.toString() + ", " +
                "seqNum " + seqNum;
    }
}
