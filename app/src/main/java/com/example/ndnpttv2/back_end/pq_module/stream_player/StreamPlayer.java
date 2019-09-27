package com.example.ndnpttv2.back_end.pq_module.stream_player;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;

import com.example.ndnpttv2.back_end.pq_module.stream_player.exoplayer_customization.AdtsExtractorFactory;
import com.example.ndnpttv2.back_end.pq_module.stream_player.exoplayer_customization.InputStreamDataSource;
import com.example.ndnpttv2.back_end.structs.ProgressEventInfo;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.pploder.events.Event;
import com.pploder.events.SimpleEvent;

import net.named_data.jndn.Name;

import static com.example.ndnpttv2.back_end.Constants.NO_PROGRESS_TRACKER_ID;

public class StreamPlayer {

    private static final String TAG = "StreamPlayer";

    private ExoPlayer player_;
    private Name streamName_;
    private boolean closed_ = false;
    long progressTrackerId_;

    // Events
    public Event<ProgressEventInfo> eventPlayingCompleted;

    public StreamPlayer(Context ctx, InputStreamDataSource dataSource,
                        long progressTrackerId, Name streamName) {

        progressTrackerId_ = progressTrackerId;
        streamName_ = streamName;

        eventPlayingCompleted = new SimpleEvent<>();

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
                    eventPlayingCompleted.trigger(new ProgressEventInfo(progressTrackerId_, streamName_, 0, null));
                }
            }

            @Override
            public void onPlayerError(ExoPlaybackException error) {
                Log.e(TAG, "ExoPlayer had error: " + error.getMessage());
                close();
                eventPlayingCompleted.trigger(new ProgressEventInfo(progressTrackerId_, streamName_, 0, null));
            }
        });

        player_.prepare(audioSource);

        player_.setPlayWhenReady(true);
    }

    public void close() {
        if (closed_) return;
        closed_ = true;
        player_.release();
    }

}
