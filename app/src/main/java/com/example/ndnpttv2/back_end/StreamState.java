package com.example.ndnpttv2.back_end;

import com.example.ndnpttv2.back_end.sc_module.stream_consumer.StreamConsumer;
import com.example.ndnpttv2.back_end.sc_module.stream_player.StreamPlayer;

public class StreamState {

    public StreamState(StreamConsumer streamConsumer, StreamPlayer streamPlayer) {
        this.streamConsumer = streamConsumer;
        this.streamPlayer = streamPlayer;
    }

    public StreamConsumer streamConsumer;
    public StreamPlayer streamPlayer;

}
