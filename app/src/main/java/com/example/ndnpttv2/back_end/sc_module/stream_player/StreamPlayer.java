package com.example.ndnpttv2.back_end.sc_module.stream_player;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;

import com.example.ndnpttv2.back_end.sc_module.SCModule;
import com.example.ndnpttv2.back_end.sc_module.stream_player.exoplayer_customization.AdtsExtractorFactory;
import com.example.ndnpttv2.back_end.sc_module.stream_player.exoplayer_customization.InputStreamDataSource;
import com.example.ndnpttv2.back_end.UiEventInfo;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;

import net.named_data.jndn.Name;

public class StreamPlayer {

    private static final String TAG = "StreamPlayer";

    // Events for stream statistics and ui notifications
    private static final int EVENT_STREAM_PLAY_COMPLETE = 0;

    private ExoPlayer player_;
    private Handler uiHandler_;
    private Name streamName_;

    public StreamPlayer(Context ctx, InputStreamDataSource dataSource,
                        Name streamName, Handler uiHandler) {

        streamName_ = streamName;
        uiHandler_ = uiHandler;

        player_ = ExoPlayerFactory.newSimpleInstance(ctx, new DefaultTrackSelector(),
                new DefaultLoadControl.Builder()
                        .setBackBuffer(600, false)
                        .setBufferDurationsMs(600, 600, 600, 600)
                        .setTargetBufferBytes(600)
                        .createDefaultLoadControl());
        // This is the MediaSource representing the media to be played.
        MediaSource audioSource = new ProgressiveMediaSource.Factory(
                () -> dataSource,
                new AdtsExtractorFactory())
                .createMediaSource(Uri.parse("fake_uri"));

        player_.addListener(new Player.EventListener() {
            @Override
            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                String playbackStateString = "";
                switch (playbackState) {
                    case Player.STATE_IDLE:
                        playbackStateString = "STATE_IDLE";
                        break;
                    case Player.STATE_BUFFERING:
                        playbackStateString = "STATE_BUFFERING";
                        break;
                    case Player.STATE_READY:
                        playbackStateString = "STATE_READY";
                        break;
                    case Player.STATE_ENDED:
                        playbackStateString = "STATE_ENDED";
                        break;
                    default:
                        playbackStateString = "UNEXPECTED STATE CODE (" + playbackState + ")";
                }
                Log.d(TAG, System.currentTimeMillis() + ": " +
                        "Exoplayer state changed to: " + playbackStateString);

                if (playbackState == Player.STATE_ENDED) {
                    notifyUiEvent(EVENT_STREAM_PLAY_COMPLETE, 0);
                }
            }
        });

        player_.prepare(audioSource);

        player_.setPlayWhenReady(true);
    }

    public void close() {
        player_.release();
    }

    private void notifyUiEvent(int event_code, long arg1) {
        int what;
        switch (event_code) {
            case EVENT_STREAM_PLAY_COMPLETE:
                what = SCModule.MSG_STREAM_PLAYER_PLAY_COMPLETE;
                break;
            default:
                throw new IllegalStateException("unrecognized event_code: " + event_code);
        }
        uiHandler_
                .obtainMessage(what, new UiEventInfo(streamName_, arg1))
                .sendToTarget();
    }

}
