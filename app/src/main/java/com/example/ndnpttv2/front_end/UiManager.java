package com.example.ndnpttv2.front_end;

import android.content.Context;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.example.ndnpttv2.R;
import com.example.ndnpttv2.back_end.MessageTypes;
import com.example.ndnpttv2.back_end.ProgressEventInfo;
import com.example.ndnpttv2.back_end.StreamInfo;

import net.named_data.jndn.Name;

import java.util.HashMap;

public class UiManager {

    private static final String TAG = "UiManager";

    private MainActivity mainActivity_;
    private Context ctx_;

    private Button incrementStreamIdButton_;
    private EditText streamIdInput_;
    private Button notifyNewStreamButton_;

    private HashMap<Name, StreamState> streamStates_;

    public UiManager(MainActivity mainActivity) {

        mainActivity_ = mainActivity;
        ctx_ = mainActivity_.getApplication();

        streamStates_ = new HashMap<>();

        streamIdInput_ = (EditText) mainActivity_.findViewById(R.id.stream_id);

        incrementStreamIdButton_ = (Button) mainActivity_.findViewById(R.id.increment_stream_id_button);
        incrementStreamIdButton_.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                streamIdInput_.setText(Long.toString(Long.parseLong(streamIdInput_.getText().toString()) + 1));
            }
        });

        notifyNewStreamButton_ = (Button) mainActivity_.findViewById(R.id.notify_new_stream_button);
        notifyNewStreamButton_.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Name streamName = new Name(mainActivity_.getString(R.string.network_prefix))
                        .append("test_stream")
                        .append(streamIdInput_.getText().toString())
                        .appendVersion(0);
                mainActivity_.getHandler()
                        .obtainMessage(MessageTypes.MSG_SC_MODULE, MessageTypes.MSG_SC_MODULE_NEW_STREAM_AVAILABLE, 0,
                                new StreamInfo(streamName, 1, 8000))
                        .sendToTarget();
            }
        });

    }

    public void handleMessage(Message msg) {

        if (msg.arg1 == MessageTypes.MSG_PROGRESS_EVENT_STREAM_STATE_CREATED) {
            StreamInfo streamInfo = (StreamInfo) msg.obj;
            streamStates_.put(streamInfo.streamName, new StreamState(streamInfo));
            Log.d(TAG, "state for stream " + streamInfo.streamName + " received");
            return;
        }

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
                break;
            }
            case MessageTypes.MSG_PROGRESS_EVENT_STREAM_FETCHER_FETCHING_COMPLETE: {
                Log.d(TAG, "fetching of stream " + streamName.toString() + " finished");
                break;
            }
            case MessageTypes.MSG_PROGRESS_EVENT_STREAM_PLAYER_PLAYING_COMPLETE: {
                Log.d(TAG, "playing of stream " + streamName.toString() +
                        " finished");
                streamStates_.remove(streamName);
                break;
            }
            case MessageTypes.MSG_PROGRESS_EVENT_STREAM_FETCHER_PRODUCTION_WINDOW_GROW: {
                streamState.highestSegAnticipated = progressEventInfo.arg1;
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
