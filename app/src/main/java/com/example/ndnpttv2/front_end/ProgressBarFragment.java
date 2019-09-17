package com.example.ndnpttv2.front_end;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.example.ndnpttv2.R;
import com.example.ndnpttv2.back_end.ProgressEventInfo;
import com.example.ndnpttv2.back_end.StreamInfo;
import com.example.ndnpttv2.back_end.pq_module.PlaybackQueueModule;
import com.example.ndnpttv2.back_end.pq_module.stream_consumer.StreamConsumer;
import com.example.ndnpttv2.front_end.custom_progress_bar.CustomProgressBar;

import net.named_data.jndn.Name;

import java.util.concurrent.LinkedTransferQueue;

public class ProgressBarFragment extends Fragment {

    // Messages
    private static final int MSG_STREAM_FETCHER_PRODUCTION_WINDOW_GROW = 0;
    private static final int MSG_STREAM_FETCHER_INTEREST_SKIPPED = 1;
    private static final int MSG_STREAM_FETCHER_AUDIO_RETRIEVED = 2;
    private static final int MSG_STREAM_FETCHER_NACK_RETRIEVED = 3;
    private static final int MSG_STREAM_FETCHER_FINAL_BLOCK_ID_LEARNED = 4;
    private static final int MSG_STREAM_BUFFER_FRAME_BUFFERED = 5;
    private static final int MSG_STREAM_BUFFER_FRAME_SKIPPED = 6;
    private static final int MSG_STREAM_BUFFER_FINAL_FRAME_NUM_LEARNED = 7;

    private StreamState state_;

    private TextView nameDisplay_;
    private CustomProgressBar progressBar_;

    private Handler handler_;
    private boolean viewInitialized_ = false;
    private LinkedTransferQueue<Message> prematureMessages_;

    public class StreamState {
        // Public constants
        static final int FINAL_BLOCK_ID_UNKNOWN = -1;
        static final int FINAL_FRAME_NUM_UNKNOWN = -1;
        static final int NO_SEGMENTS_PRODUCED = -1;

        StreamState(StreamInfo streamInfo) {
            this.streamName = streamInfo.streamName;
            this.framesPerSegment = streamInfo.framesPerSegment;
            this.producerSamplingRate = streamInfo.producerSamplingRate;
        }

        Name streamName;
        long framesPerSegment;
        long producerSamplingRate;
        long finalBlockId = FINAL_BLOCK_ID_UNKNOWN;
        long highestSegAnticipated = NO_SEGMENTS_PRODUCED;
        long finalFrameNum = FINAL_FRAME_NUM_UNKNOWN;
        long segmentsFetched = 0;
        long interestsSkipped = 0;
        long nacksFetched = 0;
        long framesBuffered = 0;
        long framesSkipped = 0;
    }

    ProgressBarFragment(PlaybackQueueModule.StreamInfoAndStreamState streamInfoAndStreamState, Looper mainThreadLooper) {

        state_ = new StreamState(streamInfoAndStreamState.streamInfo);
        prematureMessages_ = new LinkedTransferQueue<>();

        handler_ = new Handler(mainThreadLooper) {
            @Override
            public void handleMessage(@NonNull Message msg) {

                ProgressEventInfo progressEventInfo = (ProgressEventInfo) msg.obj;

                switch (msg.what) {
                    case MSG_STREAM_FETCHER_PRODUCTION_WINDOW_GROW: {
                        state_.highestSegAnticipated = progressEventInfo.arg1;
                        updateProgressBar(msg.what, 0, state_);
                        break;
                    }
                    case MSG_STREAM_FETCHER_INTEREST_SKIPPED: {
                        state_.interestsSkipped++;
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
                    default: {
                        throw new IllegalStateException("unexpected msg.what " + msg.what);
                    }
                }
            }
        };

        StreamConsumer streamConsumer = streamInfoAndStreamState.streamState.streamConsumer;
        streamConsumer.eventProductionWindowGrowth.addListener(progressEventInfo ->
                processProgressEvent(MSG_STREAM_FETCHER_PRODUCTION_WINDOW_GROW, progressEventInfo)
        );
        streamConsumer.eventInterestSkipped.addListener(progressEventInfo ->
                processProgressEvent(MSG_STREAM_FETCHER_INTEREST_SKIPPED, progressEventInfo)
        );
        streamConsumer.eventAudioRetrieved.addListener(progressEventInfo ->
                processProgressEvent(MSG_STREAM_FETCHER_AUDIO_RETRIEVED, progressEventInfo)
        );
        streamConsumer.eventNackRetrieved.addListener(progressEventInfo ->
                processProgressEvent(MSG_STREAM_FETCHER_NACK_RETRIEVED, progressEventInfo)
        );
        streamConsumer.eventFinalBlockIdLearned.addListener(progressEventInfo ->
                processProgressEvent(MSG_STREAM_FETCHER_FINAL_BLOCK_ID_LEARNED, progressEventInfo)
        );
        streamConsumer.eventFrameBuffered.addListener(progressEventInfo ->
                processProgressEvent(MSG_STREAM_BUFFER_FRAME_BUFFERED, progressEventInfo)
        );
        streamConsumer.eventFrameSkipped.addListener(progressEventInfo ->
                processProgressEvent(MSG_STREAM_BUFFER_FRAME_SKIPPED, progressEventInfo)
        );
        streamConsumer.eventFinalFrameNumLearned.addListener(progressEventInfo ->
                processProgressEvent(MSG_STREAM_BUFFER_FINAL_FRAME_NUM_LEARNED, progressEventInfo)
        );
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view =  inflater.inflate(R.layout.fragment_progress_bar, container, false);

        nameDisplay_ = (TextView) view.findViewById(R.id.name_display);
        String displayString = getString(R.string.progress_bar_name_label) + state_.streamName.toString();
        nameDisplay_.setText(displayString);

        progressBar_ = (CustomProgressBar) view.findViewById(R.id.progress_bar);
        progressBar_.setEnabled(false);
        progressBar_.getThumb().setAlpha(0);
        progressBar_.init();

        viewInitialized_ = true;

        while (prematureMessages_.size() != 0) {
            Message msg = prematureMessages_.poll();
            if (msg == null) continue;
            msg.sendToTarget();
        }

        return view;
    }

    private void processProgressEvent(int msg_what, ProgressEventInfo progressEventInfo) {
        Message msg = handler_.obtainMessage(msg_what, progressEventInfo);
        if (!viewInitialized_) {
            prematureMessages_.put(msg);
        }
        else {
            msg.sendToTarget();
        }
    }

    private void updateProgressBar(int msg_what, long arg1, StreamState streamState) {
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

    Name getStreamName() {
        return state_.streamName;
    }

    private long getNumFrames(long finalBlockId, long framesPerSegment) {
        return finalBlockId * framesPerSegment + framesPerSegment - 1;
    }
}