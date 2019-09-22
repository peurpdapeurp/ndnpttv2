package com.example.ndnpttv2.front_end;

import android.graphics.drawable.ColorDrawable;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.example.ndnpttv2.R;
import com.example.ndnpttv2.back_end.structs.ProgressEventInfo;
import com.example.ndnpttv2.back_end.structs.StreamInfo;
import com.example.ndnpttv2.back_end.pq_module.PlaybackQueueModule;
import com.example.ndnpttv2.back_end.pq_module.stream_consumer.StreamConsumer;
import com.example.ndnpttv2.back_end.pq_module.stream_player.StreamPlayer;
import com.example.ndnpttv2.back_end.structs.StreamMetaData;

import net.named_data.jndn.Name;

import java.text.SimpleDateFormat;

public class ProgressBarFragmentConsume extends ProgressBarFragment {

    private static final String TAG = "PBFConsume";

    // Private constants
    private static final int POPUP_WINDOW_WIDTH = 900;
    private static final int POPUP_WINDOW_HEIGHT = 680;

    // Messages
    private static final int MSG_STREAM_FETCHER_PRODUCTION_WINDOW_GROW = 0;
    private static final int MSG_STREAM_FETCHER_INTEREST_SKIPPED = 1;
    private static final int MSG_STREAM_FETCHER_AUDIO_RETRIEVED = 2;
    private static final int MSG_STREAM_FETCHER_NACK_RETRIEVED = 3;
    private static final int MSG_STREAM_FETCHER_FINAL_BLOCK_ID_LEARNED = 4;
    public static final int MSG_STREAM_BUFFER_BUFFERING_STARTED = 5;
    private static final int MSG_STREAM_BUFFER_FRAME_BUFFERED = 6;
    private static final int MSG_STREAM_BUFFER_FRAME_SKIPPED = 7;
    private static final int MSG_STREAM_BUFFER_FINAL_FRAME_NUM_LEARNED = 8;
    private static final int MSG_STREAM_PLAYER_PLAYING_FINISHED = 9;
    public static final int MSG_STREAM_FETCHER_META_DATA_FETCH_FAILED = 10;

    StreamState state_;
    boolean bufferingStarted_;
    boolean viewInitialized_;

    public class StreamState {
        static final int FINAL_BLOCK_ID_UNKNOWN = -1;
        static final int FINAL_FRAME_NUM_UNKNOWN = -1;
        static final int NO_SEGMENTS_ANTICIPATED = -1;
        static final int FRAMES_PER_SEGMENT_UNKNOWN = -1;
        static final int PRODUCER_SAMPLING_RATE_UNKNOWN = -1;
        static final int RECORDING_START_TIME_UNKNOWN = -1;

        StreamState(Name streamName) {
            this.streamName = streamName;
        }

        Name streamName;
        long framesPerSegment = FRAMES_PER_SEGMENT_UNKNOWN;
        long producerSamplingRate = PRODUCER_SAMPLING_RATE_UNKNOWN;
        long recordingStartTime = RECORDING_START_TIME_UNKNOWN;
        long finalBlockId = FINAL_BLOCK_ID_UNKNOWN;
        long highestSegAnticipated = NO_SEGMENTS_ANTICIPATED;
        long finalFrameNum = FINAL_FRAME_NUM_UNKNOWN;
        long segmentsFetched = 0;
        long segmentsSkipped = 0;
        long nacksFetched = 0;
        long framesBuffered = 0;
        long framesSkipped = 0;

        @Override
        public String toString() {
            java.util.Date d = new java.util.Date(recordingStartTime);
            String itemDateStr = "";
            if (recordingStartTime != RECORDING_START_TIME_UNKNOWN) {
                itemDateStr = new SimpleDateFormat("dd-MMM HH:mm:ss.SSS").format(d);
            }
            else {
                itemDateStr = "?";
            }

            return
                    "Frames per segment: " +
                        ((framesPerSegment == FRAMES_PER_SEGMENT_UNKNOWN) ? "?" : framesPerSegment) + "\n" +
                    "Sampling rate: " +
                        ((producerSamplingRate == PRODUCER_SAMPLING_RATE_UNKNOWN) ? "?" : producerSamplingRate) + "\n" +
                    "Recording start time: " + itemDateStr + "\n" +
                    "Final block id: " +
                        ((finalBlockId == FINAL_BLOCK_ID_UNKNOWN) ? "?" : finalBlockId) + "\n" +
                    "Final frame number: " +
                        ((finalFrameNum == FINAL_FRAME_NUM_UNKNOWN) ? "?" : finalFrameNum) + "\n" +
                    "Highest segment anticipated: " +
                        ((highestSegAnticipated == NO_SEGMENTS_ANTICIPATED) ? "none" : highestSegAnticipated) + "\n" +
                    "Segments fetched: " + segmentsFetched + "\n" +
                    "Segments skipped: " + segmentsSkipped + "\n" +
                    "Nacks fetched: " + nacksFetched + "\n" +
                    "Frames buffered: " + framesBuffered + "\n" +
                    "Frames skipped: " + framesSkipped;
        }
    }

    ProgressBarFragmentConsume(PlaybackQueueModule.StreamNameAndStreamState streamNameAndStreamState,
                               Looper mainThreadLooper) {
        super(streamNameAndStreamState.streamName, mainThreadLooper);

        state_ = new StreamState(streamNameAndStreamState.streamName);

        StreamConsumer streamConsumer = streamNameAndStreamState.streamState.streamConsumer;
        streamConsumer.eventProductionWindowGrowth.addListener(progressEventInfo ->
                processProgressEvent(MSG_STREAM_FETCHER_PRODUCTION_WINDOW_GROW, progressEventInfo));
        streamConsumer.eventInterestSkipped.addListener(progressEventInfo ->
                processProgressEvent(MSG_STREAM_FETCHER_INTEREST_SKIPPED, progressEventInfo));
        streamConsumer.eventAudioRetrieved.addListener(progressEventInfo ->
                processProgressEvent(MSG_STREAM_FETCHER_AUDIO_RETRIEVED, progressEventInfo));
        streamConsumer.eventNackRetrieved.addListener(progressEventInfo ->
                processProgressEvent(MSG_STREAM_FETCHER_NACK_RETRIEVED, progressEventInfo));
        streamConsumer.eventFinalBlockIdLearned.addListener(progressEventInfo ->
                processProgressEvent(MSG_STREAM_FETCHER_FINAL_BLOCK_ID_LEARNED, progressEventInfo));
        streamConsumer.eventBufferingStarted.addListener(progressEventInfo ->
                processProgressEvent(MSG_STREAM_BUFFER_BUFFERING_STARTED, progressEventInfo));
        streamConsumer.eventFrameBuffered.addListener(progressEventInfo ->
                processProgressEvent(MSG_STREAM_BUFFER_FRAME_BUFFERED, progressEventInfo));
        streamConsumer.eventFrameSkipped.addListener(progressEventInfo ->
                processProgressEvent(MSG_STREAM_BUFFER_FRAME_SKIPPED, progressEventInfo));
        streamConsumer.eventFinalFrameNumLearned.addListener(progressEventInfo ->
                processProgressEvent(MSG_STREAM_BUFFER_FINAL_FRAME_NUM_LEARNED, progressEventInfo));
        streamConsumer.eventMetaDataFetchFailed.addListener(progressEventInfo ->
                processProgressEvent(MSG_STREAM_FETCHER_META_DATA_FETCH_FAILED, progressEventInfo));

        StreamPlayer streamPlayer = streamNameAndStreamState.streamState.streamPlayer;
        streamPlayer.eventPlayingCompleted.addListener(progressEventInfo ->
                processProgressEvent(MSG_STREAM_PLAYER_PLAYING_FINISHED, progressEventInfo));
    }

    void handleMessageInternal(Message msg) {

        ProgressEventInfo progressEventInfo = (ProgressEventInfo) msg.obj;

        switch (msg.what) {
            case MSG_STREAM_FETCHER_PRODUCTION_WINDOW_GROW: {
                state_.highestSegAnticipated = progressEventInfo.arg1;
                updateProgressBar(msg.what, 0, state_);
                break;
            }
            case MSG_STREAM_FETCHER_INTEREST_SKIPPED: {
                state_.segmentsSkipped++;
                break;
            }
            case MSG_STREAM_FETCHER_AUDIO_RETRIEVED: {
                state_.segmentsFetched++;
                updateProgressBar(msg.what, progressEventInfo.arg1, state_);
                break;
            }
            case MSG_STREAM_FETCHER_NACK_RETRIEVED: {
                state_.nacksFetched++;
                break;
            }
            case MSG_STREAM_FETCHER_FINAL_BLOCK_ID_LEARNED: {
                state_.finalBlockId = progressEventInfo.arg1;
                updateProgressBar(msg.what, 0, state_);
                break;
            }
            case MSG_STREAM_BUFFER_BUFFERING_STARTED: {
                Log.d(TAG, "got signal that buffering started");
                StreamMetaData metaData = (StreamMetaData) progressEventInfo.obj;
                state_.framesPerSegment = metaData.framesPerSegment;
                state_.producerSamplingRate = metaData.producerSamplingRate;
                state_.recordingStartTime = metaData.recordingStartTime;
                bufferingStarted_ = true;
                if (viewInitialized_)
                    startRendering();
                break;
            }
            case MSG_STREAM_BUFFER_FRAME_BUFFERED: {
                state_.framesBuffered++;
                updateProgressBar(msg.what, progressEventInfo.arg1, state_);
                break;
            }
            case MSG_STREAM_BUFFER_FRAME_SKIPPED: {
                state_.framesSkipped++;
                updateProgressBar(msg.what, progressEventInfo.arg1, state_);
                break;
            }
            case MSG_STREAM_BUFFER_FINAL_FRAME_NUM_LEARNED: {
                state_.finalFrameNum = progressEventInfo.arg1;
                updateProgressBar(msg.what, 0, state_);
                break;
            }
            case MSG_STREAM_PLAYER_PLAYING_FINISHED: {
                enableStreamInfoPopUp();
                break;
            }
            case MSG_STREAM_FETCHER_META_DATA_FETCH_FAILED: {
                Log.d(TAG, "got signal that stream meta data fetch failed");
                enableStreamInfoPopUp();
                break;
            }
            default: {
                throw new IllegalStateException("unexpected msg.what " + msg.what);
            }
        }
    }

    @Override
    Name getStreamName() {
        return state_.streamName;
    }

    // https://stackoverflow.com/questions/18461990/pop-up-window-to-display-some-stuff-in-a-fragment
    @Override
    void showStreamInfoPopUp(View anchorView) {
        View popupView = getLayoutInflater().inflate(R.layout.popup_layout, null);

        PopupWindow popupWindow = new PopupWindow(popupView,
                POPUP_WINDOW_WIDTH, POPUP_WINDOW_HEIGHT);

        // Example: If you have a TextView inside `popup_layout.xml`
        TextView streamStatisticsDisplay = (TextView) popupView.findViewById(R.id.stream_statistics_display);
        streamStatisticsDisplay.setText(state_.toString());

        // If the PopupWindow should be focusable
        popupWindow.setFocusable(true);

        // If you need the PopupWindow to dismiss when when touched outside
        popupWindow.setBackgroundDrawable(new ColorDrawable());

        int location[] = new int[2];

        // Get the View's(the one that was clicked in the Fragment) location
        anchorView.getLocationOnScreen(location);

        // Using location, the PopupWindow will be displayed right under anchorView
        popupWindow.showAtLocation(anchorView, Gravity.NO_GRAVITY,
                location[0], location[1] + anchorView.getHeight());
    }

    @Override
    void onViewInitialized() {
        viewInitialized_ = true;
        if (bufferingStarted_)
            startRendering();
    }

    void updateProgressBar(int msg_what, long arg1, StreamState streamState) {
        boolean finalBlockIdKnown = streamState.finalBlockId != StreamState.FINAL_BLOCK_ID_UNKNOWN;
        boolean finalFrameNumKnown = streamState.finalFrameNum != StreamState.FINAL_FRAME_NUM_UNKNOWN;

        // rescaling logic
        if (!finalBlockIdKnown && finalFrameNumKnown) {
            if (progressBar_.getTotalSegments() != streamState.finalFrameNum + 1) {
                progressBar_.setTotalSegments((int) (streamState.finalFrameNum + 1));
            }
        }
        else if (finalBlockIdKnown) {
            long numFrames = getNumFrames(streamState.finalBlockId, streamState.framesPerSegment);
            if (progressBar_.getTotalSegments() != numFrames) {
                progressBar_.setTotalSegments((int) numFrames);
            }
        }
        else if ((float) streamState.highestSegAnticipated / (float) progressBar_.getTotalSegments() > 0.90f) {
            progressBar_.setTotalSegments(progressBar_.getTotalSegments() * 2);
        }

        // single progress bar segment update logic
        switch (msg_what) {
            case MSG_STREAM_FETCHER_PRODUCTION_WINDOW_GROW: {
                long segNum = streamState.highestSegAnticipated;
                for (int i = 0; i < streamState.framesPerSegment; i++) {
                    long frameNum = segNum * streamState.framesPerSegment + i;
                    progressBar_.updateSingleSegmentColor((int) frameNum, R.color.red);
                }
                break;
            }
            case MSG_STREAM_FETCHER_AUDIO_RETRIEVED: {
                long segNum = arg1;
                for (int i = 0; i < streamState.framesPerSegment; i++) {
                    long frameNum = segNum * streamState.framesPerSegment + i;
                    progressBar_.updateSingleSegmentColor((int) frameNum, R.color.yellow);
                }
                break;
            }
            case MSG_STREAM_BUFFER_FRAME_SKIPPED: {
                long frameNum = arg1;
                progressBar_.updateSingleSegmentColor((int) frameNum, R.color.black);
                break;
            }
            case MSG_STREAM_BUFFER_FRAME_BUFFERED: {
                long frameNum = arg1;
                progressBar_.updateSingleSegmentColor((int) frameNum, R.color.green);
                break;
            }
            case MSG_STREAM_BUFFER_FINAL_FRAME_NUM_LEARNED:
                break;
            case MSG_STREAM_FETCHER_FINAL_BLOCK_ID_LEARNED:
                break;
            default:
                throw new IllegalStateException("updateProgressBar unexpected event_code " + msg_what);
        }
    }

}
