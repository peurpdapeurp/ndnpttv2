package com.example.ndnpttv2.back_end.rec_module;

import com.example.ndnpttv2.back_end.rec_module.stream_producer.StreamProducer;

class InternalStreamProductionState {

    InternalStreamProductionState(StreamProducer streamProducer) {
        this.streamProducer = streamProducer;
    }

    public StreamProducer streamProducer;

}
