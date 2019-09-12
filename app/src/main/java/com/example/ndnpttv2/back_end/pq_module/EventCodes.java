package com.example.ndnpttv2.back_end.pq_module;

public class EventCodes {

    // StreamConsumer event codes
    public static final int EVENT_INITIALIZED = 0;
    public static final int EVENT_PRODUCTION_WINDOW_GROWTH = 1;
    public static final int EVENT_AUDIO_RETRIEVED = 2;
    public static final int EVENT_INTEREST_SKIP = 3;
    public static final int EVENT_NACK_RETRIEVED = 4;
    public static final int EVENT_FINAL_BLOCK_ID_LEARNED = 5;
    public static final int EVENT_FETCHING_COMPLETE = 6;
    public static final int EVENT_FRAME_PLAY = 7;
    public static final int EVENT_FRAME_SKIP = 8;
    public static final int EVENT_FINAL_FRAME_NUM_LEARNED = 9;
    public static final int EVENT_BUFFERING_COMPLETE = 10;

    // StreamPlayer event codes
    public static final int EVENT_PLAYING_COMPLETE = 11;

}
