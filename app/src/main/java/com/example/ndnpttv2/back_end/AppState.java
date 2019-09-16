package com.example.ndnpttv2.back_end;

public class AppState {

    boolean currentlyRecording_ = false;
    boolean currentlyPlaying_ = false;

    public boolean isRecording() {
        return currentlyRecording_;
    }

    public boolean isPlaying() {
        return currentlyPlaying_;
    }

    public void startRecording() {
        if (currentlyPlaying_)
            throw new IllegalStateException("Tried to start recording while playing.");
        currentlyRecording_ = true;
    }

    public void stopRecording() {
        currentlyRecording_ = false;
    }

    public void startPlaying() {
        if (currentlyRecording_)
            throw new IllegalStateException("Tried to start playing while recording.");
        currentlyPlaying_ = true;
    }

    public void stopPlaying() {
        currentlyPlaying_ = false;
    }
}
