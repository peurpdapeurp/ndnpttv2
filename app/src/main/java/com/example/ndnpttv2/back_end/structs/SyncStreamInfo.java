package com.example.ndnpttv2.back_end.structs;

public class SyncStreamInfo {

    public SyncStreamInfo(ChannelUserSession channelUserSession, long seqNum) {
        this.channelUserSession = channelUserSession;
        this.seqNum = seqNum;
    }

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
