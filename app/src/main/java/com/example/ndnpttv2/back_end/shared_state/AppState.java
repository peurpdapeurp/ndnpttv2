package com.example.ndnpttv2.back_end.shared_state;

import com.example.ndnpttv2.util.Logger;

import static com.example.ndnpttv2.util.Logger.DebugInfo.LOG_ERROR;

public class AppState {

    private static final String TAG = "AppState";

    boolean currentlyRecording_ = false;
    boolean currentlyPlaying_ = false;

    public boolean isRecording() {
        return currentlyRecording_;
    }

    public boolean isPlaying() {
        return currentlyPlaying_;
    }

    public void startRecording() {
        if (currentlyPlaying_) {
            Logger.logDebugEvent(TAG,LOG_ERROR,"Tried to start recording while playing.",System.currentTimeMillis());
            throw new IllegalStateException("Tried to start recording while playing.");
        }
        currentlyRecording_ = true;
    }

    public void stopRecording() {
        currentlyRecording_ = false;
    }

    public void startPlaying() {
        if (currentlyRecording_) {
            Logger.logDebugEvent(TAG,LOG_ERROR,"Tried to start playing while recording.",System.currentTimeMillis());
            throw new IllegalStateException("Tried to start playing while recording.");
        }
        currentlyPlaying_ = true;
    }

    public void stopPlaying() {
        currentlyPlaying_ = false;
    }
}
