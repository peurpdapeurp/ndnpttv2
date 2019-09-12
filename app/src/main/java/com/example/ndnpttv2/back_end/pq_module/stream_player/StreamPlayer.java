package com.example.ndnpttv2.back_end.pq_module.stream_player;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;

import com.example.ndnpttv2.back_end.pq_module.EventCodes;
import com.example.ndnpttv2.back_end.pq_module.stream_player.exoplayer_customization.AdtsExtractorFactory;
import com.example.ndnpttv2.back_end.pq_module.stream_player.exoplayer_customization.InputStreamDataSource;
import com.example.ndnpttv2.back_end.ProgressEventInfo;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.pploder.events.Event;
import com.pploder.events.SimpleEvent;

import net.named_data.jndn.Name;

public class StreamPlayer {

    private static final String TAG = "StreamPlayer";

    private ExoPlayer player_;
    private Handler uiHandler_;
    private Name streamName_;
    private Event<ProgressEventInfo> progressEvent_;

    public StreamPlayer(Context ctx, InputStreamDataSource dataSource,
                        Name streamName, Handler uiHandler) {

        streamName_ = streamName;
        uiHandler_ = uiHandler;

        progressEvent_ = new SimpleEvent<>();

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
                    progressEvent_.trigger(new ProgressEventInfo(streamName_, EventCodes.EVENT_PLAYING_COMPLETE, 0));
                }
            }
        });

        player_.prepare(audioSource);

        player_.setPlayWhenReady(true);
    }

    public void close() {
        player_.release();
    }

}
