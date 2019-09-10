package com.example.ndnpttv2.back_end;

import net.named_data.jndn.Name;

public class ProgressEventInfo {

    public ProgressEventInfo(Name streamName, long arg1) {
        this.streamName = streamName;
        this.arg1 = arg1;
    }

    public Name streamName;
    public long arg1;

}
