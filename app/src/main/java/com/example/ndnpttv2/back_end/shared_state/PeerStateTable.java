package com.example.ndnpttv2.back_end.shared_state;

import com.example.ndnpttv2.back_end.structs.ChannelUserSession;
import com.example.ndnpttv2.back_end.structs.StreamMetaData;

import java.util.HashMap;

public class PeerStateTable {

    public static final int NO_SEQ_NUMS = -1;
    public static final int NO_META_DATA_SEQ_NUM = -1;

    private HashMap<ChannelUserSession, PeerState> peerState_;

    public static class PeerState {
        public PeerState(long highestSeqNum, long lastKnownMetaDataSeqNum, StreamMetaData lastKnownMetaData) {
            this.highestSeqNum = highestSeqNum;
            this.lastKnownMetaDataSeqNum = lastKnownMetaDataSeqNum;
            this.lastKnownMetaData = lastKnownMetaData;
        }
        public long highestSeqNum;
        public long lastKnownMetaDataSeqNum;
        public StreamMetaData lastKnownMetaData;
    }

    public PeerStateTable() {
        peerState_ = new HashMap<>();
    }

    public PeerState getPeerState(ChannelUserSession channelUserSession) {
        PeerState peerState = peerState_.get(channelUserSession);
        if (peerState == null) {
            peerState_.put(channelUserSession,
                new PeerState(NO_SEQ_NUMS, NO_META_DATA_SEQ_NUM, null));
        }
        return peerState_.get(channelUserSession);
    }

}
