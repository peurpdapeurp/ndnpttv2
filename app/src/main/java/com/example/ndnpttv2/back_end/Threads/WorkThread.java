package com.example.ndnpttv2.back_end.Threads;

import android.os.HandlerThread;
import android.os.Looper;

public class WorkThread extends HandlerThread {

    private static final String TAG = "WorkThread";

    private Callbacks callbacks_;

    public static class Info {
        public Info(Looper looper) {
            this.looper = looper;
        }
        public Looper looper;
    }

    public interface Callbacks {
        void onInitialized(Info info);
    }

    public WorkThread(Callbacks callbacks) {
        super(TAG);
        callbacks_ = callbacks;
    }

    @Override
    protected void onLooperPrepared() {
        super.onLooperPrepared();

        callbacks_.onInitialized(new Info(getLooper()));
    }

}
