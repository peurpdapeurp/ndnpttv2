package com.example.ndnpttv2.back_end.back_end_impl;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import com.example.ndnpttv2.back_end.Streamer_Module;
import com.example.ndnpttv2.helpers.Logger;

import java.io.IOException;

public class Streamer extends Streamer_Module {

    AudioManager audioManager_;

    public Streamer(Context ctx, String path) {
        super(ctx);
        audioManager_ = new AudioManager(path);
        Logger.logMessage(System.currentTimeMillis(), Logger.LOG_MODULE_STREAMER,
                "Successfully initialized.");
    }

    @Override
    protected void RECORD_REQUEST_START_LOGIC() {
        audioManager_.startRecording();
        super.RECORD_REQUEST_START_LOGIC();
    }

    @Override
    protected void RECORD_REQUEST_STOP_LOGIC() {
        audioManager_.stopRecording();
        super.RECORD_REQUEST_STOP_LOGIC();
    }

    public class AudioManager {

        private MediaPlayer player_ = null;
        private MediaRecorder recorder_ = null;
        private boolean isRecording_;
        private boolean isPlaying_;
        private String rootPath_;

        AudioManager(String rootPath) {
            isRecording_ = false;
            isPlaying_ = false;
            rootPath_ = rootPath;
        }

        public boolean isRecording() {
            return isRecording_;
        }

        public boolean isPlaying() {
            return isPlaying_;
        }

        public void startPlaying(String fileName) {

            if (isRecording()) {
                Logger.logMessage(System.currentTimeMillis(), Logger.LOG_MODULE_STREAMER_AUDIOMANAGER,
                        "Ignored call to startPlaying, currently recording.");
                return;
            }

            if (isPlaying()) {
                Logger.logMessage(System.currentTimeMillis(), Logger.LOG_MODULE_STREAMER_AUDIOMANAGER,
                        "Ignored call to startPlaying, currently already playing.");
                return;
            }

            Logger.logMessage(System.currentTimeMillis(), Logger.LOG_MODULE_STREAMER_AUDIOMANAGER,
                    "Attempting to play file with audio path: " + fileName);

            player_ = new MediaPlayer();
            try {
                player_.setDataSource(fileName);
                player_.prepare();
                player_.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mediaPlayer) {
                        player_.start();
                    }
                });
                player_.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mediaPlayer) {
                        stopPlaying();
                    }
                });
            } catch (IOException e) {
                Logger.logMessage(System.currentTimeMillis(), Logger.LOG_MODULE_STREAMER_AUDIOMANAGER,
                        "player_.prepare() failed: " + e.getMessage());
                stopPlaying();
                return;
            }

            isPlaying_ = true;
        }

        public void stopPlaying() {
            if (player_ != null) {
                player_.release();
                player_ = null;
            }

            isPlaying_ = false;
        }

        public void startRecording() {

            if (isRecording()) {
                Logger.logMessage(System.currentTimeMillis(), Logger.LOG_MODULE_STREAMER_AUDIOMANAGER,
                        "Ignored call to startRecording, currently already recording.");
                return;
            }

            if (isPlaying()) {
                Logger.logMessage(System.currentTimeMillis(), Logger.LOG_MODULE_STREAMER_AUDIOMANAGER,
                        "Ignored call to startRecording, currently playing.");
                return;
            }

            recorder_ = new MediaRecorder();
            recorder_.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder_.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            recorder_.setOutputFile(rootPath_ + "/" + "test.wav");
            recorder_.setAudioSamplingRate(22050);
            recorder_.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

            try {
                recorder_.prepare();
            } catch (IOException e) {
                Logger.logMessage(System.currentTimeMillis(), Logger.LOG_MODULE_STREAMER_AUDIOMANAGER,
                        "recorder_.prepare() failed: " + e.getMessage());
            }

            recorder_.start();

            Logger.logMessage(System.currentTimeMillis(), Logger.LOG_MODULE_STREAMER_AUDIOMANAGER,
                    "Started recording...");

            isRecording_ = true;
        }

        public void stopRecording() {
            if (recorder_ != null) {
                try {
                    recorder_.stop();
                }
                catch (Exception e) {
                    Logger.logMessage(System.currentTimeMillis(), Logger.LOG_MODULE_STREAMER_AUDIOMANAGER,
                            "recorder_.stop() failed: " + e.getMessage());
                }
                recorder_.release();
                recorder_ = null;
            }

            Logger.logMessage(System.currentTimeMillis(), Logger.LOG_MODULE_STREAMER_AUDIOMANAGER,
                    "Stopped recording.");

            isRecording_ = false;
        }

        public void close() {
            if (recorder_ != null) {
                recorder_.release();
                recorder_ = null;
            }

            if (player_ != null) {
                player_.release();
                player_ = null;
            }
        }
    }

}
