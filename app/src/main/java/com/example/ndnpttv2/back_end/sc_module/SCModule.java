package com.example.ndnpttv2.back_end.sc_module;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.example.ndnpttv2.Util.Helpers;
import com.example.ndnpttv2.back_end.MessageTypes;
import com.example.ndnpttv2.back_end.StreamInfo;
import com.example.ndnpttv2.back_end.InternalStreamState;
import com.example.ndnpttv2.back_end.sc_module.stream_consumer.StreamConsumer;
import com.example.ndnpttv2.back_end.sc_module.stream_player.StreamPlayer;
import com.example.ndnpttv2.back_end.sc_module.stream_player.exoplayer_customization.InputStreamDataSource;
import com.example.ndnpttv2.back_end.ProgressEventInfo;
import com.example.ndnpttv2.front_end.StreamState;

import net.named_data.jndn.Name;

import java.util.HashMap;
import java.util.concurrent.LinkedTransferQueue;

public class SCModule {

    private static final String TAG = "SCModule";

    // Private constants
    private static final int DEFAULT_JITTER_BUFFER_SIZE = 5;

    private Context ctx_;
    private Handler mainThreadHandler_;
    private LinkedTransferQueue<StreamInfo> playbackQueue_;
    private HashMap<Name, InternalStreamState> streamStates_;
    private boolean currentlyPlaying_ = false;

    public SCModule(Context ctx, Handler mainThreadHandler) {
        ctx_ = ctx;
        mainThreadHandler_ = mainThreadHandler;
        playbackQueue_ = new LinkedTransferQueue<>();
        streamStates_ = new HashMap<>();
    }

    public void handleMessage(Message msg) {

        if (msg.what == MessageTypes.MSG_SC_MODULE) {

            StreamInfo streamInfo = (StreamInfo) msg.obj;

            switch (msg.arg1) {
                case MessageTypes.MSG_SC_MODULE_NEW_STREAM_AVAILABLE: {
                    Log.d(TAG, "Notified of new stream " + "(" +
                            Helpers.getStreamInfoString(streamInfo) +
                            ")");
                    playbackQueue_.add(streamInfo);
                    break;
                }
                default: {
                    throw new IllegalStateException("unexpected msg " + msg.what);
                }
            }

        }
        else if (msg.what  == MessageTypes.MSG_PROGRESS_EVENT) {

            if (msg.arg1 == MessageTypes.MSG_PROGRESS_EVENT_STREAM_STATE_CREATED) {
                // ignore
                return;
            }

            ProgressEventInfo progressEventInfo = (ProgressEventInfo) msg.obj;
            Name streamName = progressEventInfo.streamName;
            InternalStreamState streamState = streamStates_.get(streamName);

            if (streamState == null) {
                Log.w(TAG, "streamState was null for msg (" +
                        "msg.what " + msg.what + ", " +
                        "stream name " + streamName.toString() +
                        ")");
                return;
            }

            switch (msg.arg1) {
                case MessageTypes.MSG_PROGRESS_EVENT_STREAM_CONSUMER_INITIALIZED: {
                    streamState.streamConsumer.getHandler()
                            .obtainMessage(StreamConsumer.MSG_FETCH_START)
                            .sendToTarget();
                    streamState.streamConsumer.getHandler()
                            .obtainMessage(StreamConsumer.MSG_PLAY_START)
                            .sendToTarget();
                    Log.d(TAG, "fetching of stream " + streamName.toString() + " started");
                    break;
                }
                case MessageTypes.MSG_PROGRESS_EVENT_STREAM_FETCHER_FETCHING_COMPLETE: {
                    Log.d(TAG, "fetching of stream " + streamName.toString() + " finished");
                    break;
                }
                case MessageTypes.MSG_PROGRESS_EVENT_STREAM_PLAYER_PLAYING_COMPLETE: {
                    Log.d(TAG, "playing of stream " + streamName.toString());
                    streamState.streamConsumer.close();
                    streamState.streamPlayer.close();
                    streamStates_.remove(streamName);
                    currentlyPlaying_ = false;
                    break;
                }
                case MessageTypes.MSG_PROGRESS_EVENT_STREAM_FETCHER_PRODUCTION_WINDOW_GROW: {
                    // ignore
                    break;
                }
                case MessageTypes.MSG_PROGRESS_EVENT_STREAM_FETCHER_INTEREST_SKIP: {
                    // ignore
                    break;
                }
                case MessageTypes.MSG_PROGRESS_EVENT_STREAM_FETCHER_AUDIO_RETRIEVED: {
                    // ignore
                    break;
                }
                case MessageTypes.MSG_PROGRESS_EVENT_STREAM_FETCHER_NACK_RETRIEVED: {
                    // ignore
                    break;
                }
                case MessageTypes.MSG_PROGRESS_EVENT_STREAM_FETCHER_FINAL_BLOCK_ID_LEARNED: {
                    // ignore
                    break;
                }
                case MessageTypes.MSG_PROGRESS_EVENT_STREAM_BUFFER_FRAME_PLAYED: {
                    // ignore
                    break;
                }
                case MessageTypes.MSG_PROGRESS_EVENT_STREAM_BUFFER_FRAME_SKIP: {
                    // ignore
                    break;
                }
                case MessageTypes.MSG_PROGRESS_EVENT_STREAM_BUFFER_BUFFERING_COMPLETE: {
                    // ignore
                    break;
                }
                case MessageTypes.MSG_PROGRESS_EVENT_STREAM_BUFFER_FINAL_FRAME_NUM_LEARNED: {
                    // ignore
                    break;
                }
                default: {
                    throw new IllegalStateException("unexpected msg " + msg.what);
                }
            }
        }

    }

    public void doSomeWork() {
        if (playbackQueue_.size() != 0 && !currentlyPlaying_) {
            currentlyPlaying_ = true;
            StreamInfo streamInfo = playbackQueue_.poll();
            Log.d(TAG, "playback queue was non empty, playing stream " + streamInfo.streamName.toString());
            InputStreamDataSource transferSource = new InputStreamDataSource();
            StreamPlayer streamPlayer = new StreamPlayer(ctx_, transferSource,
                    streamInfo.streamName,mainThreadHandler_);
            StreamConsumer streamConsumer = new StreamConsumer(
                    streamInfo.streamName,
                    transferSource,
                    mainThreadHandler_,
                    new StreamConsumer.Options(streamInfo.framesPerSegment,
                            DEFAULT_JITTER_BUFFER_SIZE,
                            streamInfo.producerSamplingRate));
            InternalStreamState internalStreamState = new InternalStreamState(streamConsumer, streamPlayer);
            streamStates_.put(streamInfo.streamName, internalStreamState);
            mainThreadHandler_
                    .obtainMessage(MessageTypes.MSG_PROGRESS_EVENT, MessageTypes.MSG_PROGRESS_EVENT_STREAM_STATE_CREATED, 0,
                            streamInfo)
                    .sendToTarget();
            streamConsumer.start();
        }
    }

}
