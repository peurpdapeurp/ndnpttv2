package com.example.ndnpttv2.back_end.sc_module;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.example.ndnpttv2.Util.Helpers;
import com.example.ndnpttv2.back_end.MessageTypes;
import com.example.ndnpttv2.back_end.StreamInfo;
import com.example.ndnpttv2.back_end.StreamState;
import com.example.ndnpttv2.back_end.sc_module.stream_consumer.StreamConsumer;
import com.example.ndnpttv2.back_end.sc_module.stream_player.StreamPlayer;
import com.example.ndnpttv2.back_end.sc_module.stream_player.exoplayer_customization.InputStreamDataSource;
import com.example.ndnpttv2.back_end.ProgressEventInfo;
import com.example.ndnpttv2.front_end.UiManager;

import net.named_data.jndn.Name;

import java.util.HashMap;

public class SCModule {

    private static final String TAG = "SCModule";

    // Private constants
    private static final int DEFAULT_JITTER_BUFFER_SIZE = 5;

    private Context ctx_;
    private Handler mainThreadHandler_;
    private UiManager uiManager_;
    private Name lastStreamName_;

    private HashMap<Name, StreamState> streamStates_;

    public SCModule(Context ctx, Handler mainThreadHandler, UiManager uiManager) {
        ctx_ = ctx;
        mainThreadHandler_ = mainThreadHandler;
        uiManager_ = uiManager;
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
                    streamStates_.put(streamInfo.streamName, new StreamState(streamConsumer, streamPlayer,
                            streamInfo.framesPerSegment, streamInfo.producerSamplingRate));
                    streamConsumer.start();
                    break;
                }
                default: {
                    throw new IllegalStateException("unexpected msg " + msg.what);
                }
            }

        }
        else if (msg.what  == MessageTypes.MSG_PROGRESS_EVENT) {

            ProgressEventInfo progressEventInfo = (ProgressEventInfo) msg.obj;
            Name streamName = progressEventInfo.streamName;
            StreamState streamState = streamStates_.get(streamName);

            if (streamState == null) {
                Log.w(TAG, "streamState was null for msg (" +
                        "msg.what " + msg.what + ", " +
                        "stream name " + streamName.toString() +
                        ")");
                return;
            }

            switch (msg.arg1) {
                case MessageTypes.MSG_PROGRESS_EVENT_STREAM_CONSUMER_INITIALIZED: {
                    Log.d(TAG, "fetching of stream " + streamName.toString() + " started");
                    streamState.streamConsumer.getHandler()
                            .obtainMessage(StreamConsumer.MSG_FETCH_START)
                            .sendToTarget();
                    streamState.streamConsumer.getHandler()
                            .obtainMessage(StreamConsumer.MSG_PLAY_START)
                            .sendToTarget();
                    break;
                }
                case MessageTypes.MSG_PROGRESS_EVENT_STREAM_FETCHER_FETCHING_COMPLETE: {
                    Log.d(TAG, "fetching of stream " + streamName.toString() + " finished");
                    break;
                }
                case MessageTypes.MSG_PROGRESS_EVENT_STREAM_PLAYER_PLAYING_COMPLETE: {
                    Log.d(TAG, "playing of stream " + streamName.toString() +
                            " finished");
                    streamState.streamConsumer.close();
                    streamState.streamPlayer.close();
                    streamStates_.remove(streamName);
                    // TODO: playback queue related logic for end of a stream play
                    break;
                }
                case MessageTypes.MSG_PROGRESS_EVENT_STREAM_FETCHER_PRODUCTION_WINDOW_GROW: {
                    long highestSegProduced = progressEventInfo.arg1;
                    streamState.highestSegAnticipated = highestSegProduced;
                    break;
                }
                case MessageTypes.MSG_PROGRESS_EVENT_STREAM_FETCHER_INTEREST_SKIP: {
                    long segNum = progressEventInfo.arg1;
                    streamState.interestsSkipped++;
                    break;
                }
                case MessageTypes.MSG_PROGRESS_EVENT_STREAM_FETCHER_AUDIO_RETRIEVED: {
                    long segNum = progressEventInfo.arg1;
                    streamState.segmentsFetched++;
                    break;
                }
                case MessageTypes.MSG_PROGRESS_EVENT_STREAM_FETCHER_NACK_RETRIEVED: {
                    long segNum = progressEventInfo.arg1;
                    streamState.nacksFetched++;
                    break;
                }
                case MessageTypes.MSG_PROGRESS_EVENT_STREAM_FETCHER_FINAL_BLOCK_ID_LEARNED: {
                    streamState.finalBlockId = progressEventInfo.arg1;
                    break;
                }
                case MessageTypes.MSG_PROGRESS_EVENT_STREAM_BUFFER_FRAME_PLAYED: {
                    long frameNum = progressEventInfo.arg1;
                    streamState.framesPlayed++;
                    break;
                }
                case MessageTypes.MSG_PROGRESS_EVENT_STREAM_BUFFER_FRAME_SKIP: {
                    long frameNum = progressEventInfo.arg1;
                    streamState.framesSkipped++;
                    break;
                }
                case MessageTypes.MSG_PROGRESS_EVENT_STREAM_BUFFER_BUFFERING_COMPLETE: {
                    Log.d(TAG, "buffering of stream " + streamName.toString() +
                            " finished");
                    break;
                }
                case MessageTypes.MSG_PROGRESS_EVENT_STREAM_BUFFER_FINAL_FRAME_NUM_LEARNED: {
                    streamState.finalFrameNum = progressEventInfo.arg1;
                    break;
                }
                default: {
                    throw new IllegalStateException("unexpected msg " + msg.what);
                }
            }
        }

    }

}
