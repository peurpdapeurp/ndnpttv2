package com.example.ndnpttv2.back_end.structs;

import net.named_data.jndn.Name;

public class ProgressEventInfo {

    public ProgressEventInfo(Name streamName, long arg1, Object obj) {
        this.streamName = streamName;
        this.arg1 = arg1;
        this.obj = obj;
    }

    public Name streamName;
    public long arg1;
    public Object obj;

}
