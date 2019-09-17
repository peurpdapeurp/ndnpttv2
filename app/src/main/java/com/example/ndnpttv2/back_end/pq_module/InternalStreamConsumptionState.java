package com.example.ndnpttv2.back_end.pq_module;

import com.example.ndnpttv2.back_end.pq_module.stream_consumer.StreamConsumer;
import com.example.ndnpttv2.back_end.pq_module.stream_player.StreamPlayer;

public class InternalStreamConsumptionState {

    public InternalStreamConsumptionState(StreamConsumer streamConsumer, StreamPlayer streamPlayer) {
        this.streamConsumer = streamConsumer;
        this.streamPlayer = streamPlayer;
    }

    public StreamConsumer streamConsumer;
    public StreamPlayer streamPlayer;

}
