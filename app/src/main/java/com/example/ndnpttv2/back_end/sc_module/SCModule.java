package com.example.ndnpttv2.back_end.sc_module;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.example.ndnpttv2.back_end.StreamInfo;
import com.example.ndnpttv2.back_end.StreamState;
import com.example.ndnpttv2.back_end.app_logic_module.AppLogicModule;
import com.example.ndnpttv2.back_end.sc_module.stream_consumer.StreamConsumer;
import com.example.ndnpttv2.back_end.sc_module.stream_player.StreamPlayer;
import com.example.ndnpttv2.back_end.sc_module.stream_player.exoplayer_customization.InputStreamDataSource;
import com.example.ndnpttv2.back_end.UiEventInfo;
import com.example.ndnpttv2.front_end.UiManager;

import net.named_data.jndn.Name;

import java.util.HashMap;

public class SCModule {

    private static final String TAG = "SCModule";

    // Public constants
    public static final int SC_MODULE_MSG_BASE = 500;

    // Private Constants
    private static final int DEFAULT_JITTER_BUFFER_SIZE = 5;
    private static final int SC_MODULE_INTERNAL_MSG_BASE = SC_MODULE_MSG_BASE + 100;

    // Messages from other modules
    public static final int MSG_APPLOGIC_NEW_STREAM_AVAILABLE = SC_MODULE_MSG_BASE;

    // Messages from Stream Consumers
    public static final int MSG_STREAM_CONSUMER_INITIALIZED = SC_MODULE_INTERNAL_MSG_BASE;
    public static final int MSG_STREAM_CONSUMER_FETCH_COMPLETE = SC_MODULE_INTERNAL_MSG_BASE + 1;
    public static final int MSG_STREAM_FETCHER_PRODUCTION_WINDOW_GROW = SC_MODULE_INTERNAL_MSG_BASE + 2;
    public static final int MSG_STREAM_FETCHER_INTEREST_SKIP = SC_MODULE_INTERNAL_MSG_BASE + 3;
    public static final int MSG_STREAM_FETCHER_AUDIO_RETRIEVED = SC_MODULE_INTERNAL_MSG_BASE + 4;
    public static final int MSG_STREAM_FETCHER_NACK_RETRIEVED = SC_MODULE_INTERNAL_MSG_BASE + 5;
    public static final int MSG_STREAM_FETCHER_FINAL_BLOCK_ID_LEARNED = SC_MODULE_INTERNAL_MSG_BASE + 6;
    public static final int MSG_STREAM_BUFFER_FRAME_PLAYED = SC_MODULE_INTERNAL_MSG_BASE + 7;
    public static final int MSG_STREAM_BUFFER_FRAME_SKIP = SC_MODULE_INTERNAL_MSG_BASE + 8;
    public static final int MSG_STREAM_BUFFER_FINAL_FRAME_NUM_LEARNED = SC_MODULE_INTERNAL_MSG_BASE + 9;
    public static final int MSG_STREAM_BUFFER_BUFFERING_COMPLETE = SC_MODULE_INTERNAL_MSG_BASE + 10;

    // Messages from Stream Player
    public static final int MSG_STREAM_PLAYER_PLAY_COMPLETE = SC_MODULE_INTERNAL_MSG_BASE + 11;

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

        if (msg.what < SC_MODULE_INTERNAL_MSG_BASE) {

            StreamInfo streamInfo = (StreamInfo) msg.obj;

            switch (msg.what) {
                case MSG_APPLOGIC_NEW_STREAM_AVAILABLE: {
                    Log.d(TAG, "notifyNewStreamAvailable " + "(" +
                            "streamName: " + streamInfo.streamName.toString() +
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
        else if (msg.what >= SC_MODULE_INTERNAL_MSG_BASE) {

            UiEventInfo uiEventInfo = (UiEventInfo) msg.obj;
            Name streamName = uiEventInfo.streamName;
            StreamState streamState = streamStates_.get(streamName);

            if (streamState == null) {
                Log.w(TAG, "streamState was null for msg (" +
                        "msg.what " + msg.what + ", " +
                        "stream name " + streamName.toString() +
                        ")");
                return;
            }

            switch (msg.what) {
                case MSG_STREAM_CONSUMER_INITIALIZED: {
                    Log.d(TAG, "fetching of stream " + streamName.toString() + " started");
                    streamState.streamConsumer.getHandler()
                            .obtainMessage(StreamConsumer.MSG_FETCH_START)
                            .sendToTarget();
                    streamState.streamConsumer.getHandler()
                            .obtainMessage(StreamConsumer.MSG_PLAY_START)
                            .sendToTarget();
                    break;
                }
                case MSG_STREAM_CONSUMER_FETCH_COMPLETE: {
                    Log.d(TAG, "fetching of stream " + streamName.toString() + " finished");
                    break;
                }
                case MSG_STREAM_PLAYER_PLAY_COMPLETE: {
                    Log.d(TAG, "playing of stream " + streamName.toString() +
                            " finished");
                    streamState.streamConsumer.close();
                    streamState.streamPlayer.close();
                    streamStates_.remove(streamName);
                    mainThreadHandler_
                            .obtainMessage(AppLogicModule.MSG_SC_STREAM_PLAYING_FINISHED,
                                    new StreamInfo(streamName, streamState.framesPerSegment, streamState.producerSamplingRate))
                            .sendToTarget();
                    break;
                }
                case MSG_STREAM_FETCHER_PRODUCTION_WINDOW_GROW: {
                    long highestSegProduced = uiEventInfo.arg1;
                    streamState.highestSegAnticipated = highestSegProduced;
                    break;
                }
                case MSG_STREAM_FETCHER_INTEREST_SKIP: {
                    long segNum = uiEventInfo.arg1;
                    streamState.interestsSkipped++;
                    break;
                }
                case MSG_STREAM_FETCHER_AUDIO_RETRIEVED: {
                    long segNum = uiEventInfo.arg1;
                    streamState.segmentsFetched++;
                    break;
                }
                case MSG_STREAM_FETCHER_NACK_RETRIEVED: {
                    long segNum = uiEventInfo.arg1;
                    streamState.nacksFetched++;
                    break;
                }
                case MSG_STREAM_FETCHER_FINAL_BLOCK_ID_LEARNED: {
                    streamState.finalBlockId = uiEventInfo.arg1;
                    break;
                }
                case MSG_STREAM_BUFFER_FRAME_PLAYED: {
                    long frameNum = uiEventInfo.arg1;
                    streamState.framesPlayed++;
                    break;
                }
                case MSG_STREAM_BUFFER_FRAME_SKIP: {
                    long frameNum = uiEventInfo.arg1;
                    streamState.framesSkipped++;
                    break;
                }
                case MSG_STREAM_BUFFER_BUFFERING_COMPLETE: {
                    Log.d(TAG, "buffering of stream " + streamName.toString() +
                            " finished");
                    break;
                }
                case MSG_STREAM_BUFFER_FINAL_FRAME_NUM_LEARNED: {
                    streamState.finalFrameNum = uiEventInfo.arg1;
                    break;
                }
                default: {
                    throw new IllegalStateException("unexpected msg " + msg.what);
                }
            }
        }

    }

}
