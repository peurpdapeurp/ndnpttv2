package com.example.ndnpttv2.back_end.structs;

public class ChannelUserSession {
    public ChannelUserSession(String channelName, String userName, long sessionId) {
        this.channelName = channelName;
        this.userName = userName;
        this.sessionId = sessionId;
    }

    public String channelName;
    public String userName;
    public long sessionId;

    @Override
    public String toString() {
        return "channelName " + channelName + ", " +
                "userName " + userName + ", " +
                "session " + sessionId;
    }

    @Override
    public int hashCode() {
        int prime = 31;
        int result = 1;
        result = prime * result + ((channelName == null) ? 0 : channelName.hashCode());
        result = prime * result + ((userName == null) ? 0 : userName.hashCode());
        result = prime * result + Long.valueOf(sessionId).hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        ChannelUserSession other;
        try {
            other = (ChannelUserSession) obj;
        }
        catch (Exception e) {
            return false;
        }
        return (channelName.equals(other.channelName) &&
                userName.equals(other.userName) &&
                sessionId == other.sessionId);
    }
}