package com.example.ndnpttv2.back_end.pq_module;

import com.example.ndnpttv2.back_end.pq_module.stream_consumer.StreamConsumer;
import com.example.ndnpttv2.back_end.pq_module.stream_player.StreamPlayer;

class InternalStreamConsumptionState {

    InternalStreamConsumptionState(StreamConsumer streamConsumer, StreamPlayer streamPlayer) {
        this.streamConsumer = streamConsumer;
        this.streamPlayer = streamPlayer;
    }

    StreamConsumer streamConsumer;
    StreamPlayer streamPlayer;

}
