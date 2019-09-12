package com.example.ndnpttv2.back_end;

import net.named_data.jndn.Name;

public class ProgressEventInfo {

    public ProgressEventInfo(Name streamName, int eventCode, long arg1) {
        this.streamName = streamName;
        this.eventCode = eventCode;
        this.arg1 = arg1;
    }

    public Name streamName;
    public int eventCode;
    public long arg1;

}
