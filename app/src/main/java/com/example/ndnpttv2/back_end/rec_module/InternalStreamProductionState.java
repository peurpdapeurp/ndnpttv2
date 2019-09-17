package com.example.ndnpttv2.back_end.rec_module;

import com.example.ndnpttv2.back_end.rec_module.stream_producer.StreamProducer;

public class InternalStreamProductionState {

    public InternalStreamProductionState(StreamProducer streamProducer) {
        this.streamProducer = streamProducer;
    }

    public StreamProducer streamProducer;

}
