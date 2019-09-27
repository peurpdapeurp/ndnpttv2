package com.example.ndnpttv2.back_end.pq_module;

import com.example.ndnpttv2.back_end.pq_module.stream_consumer.StreamConsumer;
import com.example.ndnpttv2.back_end.pq_module.stream_player.StreamPlayer;

import net.named_data.jndn.Name;

public class InternalStreamConsumptionState {

    public InternalStreamConsumptionState(Name streamName, StreamConsumer streamConsumer, StreamPlayer streamPlayer) {
        this.streamName = streamName;
        this.streamConsumer = streamConsumer;
        this.streamPlayer = streamPlayer;
    }

    public Name streamName;
    public StreamConsumer streamConsumer;
    public StreamPlayer streamPlayer;

}
