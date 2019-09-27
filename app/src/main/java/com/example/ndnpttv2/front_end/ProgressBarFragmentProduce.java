package com.example.ndnpttv2.front_end;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
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
import com.example.ndnpttv2.back_end.rec_module.RecorderModule;
import com.example.ndnpttv2.back_end.rec_module.stream_producer.StreamProducer;

import net.named_data.jndn.Name;

import java.text.SimpleDateFormat;

public class ProgressBarFragmentProduce extends ProgressBarFragment {

    private static final String TAG = "ProgressBarFragmentProd";

    // Private constants
    private static final int POPUP_WINDOW_WIDTH = 900;
    private static final int POPUP_WINDOW_HEIGHT = 375;

    // Messages
    private static final int MSG_STREAM_PRODUCER_SEGMENT_PUBLISHED = 0;
    private static final int MSG_STREAM_PRODUCER_FINAL_SEGMENT_PUBLISHED = 1;

    private StreamState state_;
    private Drawable currentIcon_;
    private boolean streamPopUpEnabled_ = false;

    private class StreamState {
        static final int FINAL_BLOCK_ID_UNKNOWN = -1;
        static final int NO_SEGMENTS_PUBLISHED = -1;

        StreamState(StreamInfo streamInfo) {
            this.streamName = streamInfo.streamName;
            this.framesPerSegment = streamInfo.metaData.framesPerSegment;
            this.producerSamplingRate = streamInfo.metaData.producerSamplingRate;
            this.recordingStartTime = streamInfo.metaData.recordingStartTime;
        }

        Name streamName;
        long framesPerSegment;
        long producerSamplingRate;
        long recordingStartTime;
        long finalBlockId = FINAL_BLOCK_ID_UNKNOWN;
        long numSegsPublished = 0;
        long highestSegPublished = NO_SEGMENTS_PUBLISHED;

        @Override
        public String toString() {
            java.util.Date d = new java.util.Date(recordingStartTime);
            String itemDateStr = new SimpleDateFormat("dd-MMM HH:mm:ss.SSS").format(d);
            return
                    "Frames per segment: " + framesPerSegment + "\n" +
                    "Sampling rate: " + producerSamplingRate + "\n" +
                    "Recording start time: " + itemDateStr + "\n" +
                    "Final block id (index): " +
                        ((finalBlockId == FINAL_BLOCK_ID_UNKNOWN) ? "?" : finalBlockId) + "\n" +
                    "Segments published (count): " + numSegsPublished;
//                    + "\n" + "Highest segment published: " +
//                        ((highestSegPublished == NO_SEGMENTS_PUBLISHED) ? "none" : highestSegPublished);
        }
    }

    ProgressBarFragmentProduce(RecorderModule.StreamInfoAndStreamState streamInfoAndStreamState,
                                      Looper mainThreadLooper, Context ctx) {
        super(streamInfoAndStreamState.streamInfo.streamName, mainThreadLooper, ctx);

        state_ = new StreamState(streamInfoAndStreamState.streamInfo);

        StreamProducer streamProducer = streamInfoAndStreamState.streamState.streamProducer;
        streamProducer.eventSegmentPublished.addListener(progressEventInfo ->
                processProgressEvent(MSG_STREAM_PRODUCER_SEGMENT_PUBLISHED, progressEventInfo));
        streamProducer.eventFinalSegmentPublished.addListener(progressEventInfo ->
                processProgressEvent(MSG_STREAM_PRODUCER_FINAL_SEGMENT_PUBLISHED, progressEventInfo));



    }

    @Override
    void handleMessageInternal(Message msg) {

        ProgressEventInfo progressEventInfo = (ProgressEventInfo) msg.obj;

        switch (msg.what) {
            case MSG_STREAM_PRODUCER_SEGMENT_PUBLISHED: {
                state_.numSegsPublished++;
                if (progressEventInfo.arg1 > state_.highestSegPublished) {
                    state_.highestSegPublished = progressEventInfo.arg1;
                }
                updateProgressBar(msg.what, progressEventInfo.arg1);
                break;
            }
            case MSG_STREAM_PRODUCER_FINAL_SEGMENT_PUBLISHED: {
                state_.finalBlockId = progressEventInfo.arg1;
                updateProgressBar(msg.what, 0);
                streamPopUpEnabled_ = true;
                currentIcon_ = ctx_.getDrawable(R.drawable.check_mark);
                break;
            }
            default: {
                throw new IllegalStateException("unexpected msg.what " + msg.what);
            }
        }

        render();
    }

    @Override
    void onViewInitialized() {
        refetchButton_.setEnabled(false);
        refetchButton_.setVisibility(View.INVISIBLE);
        startRendering();
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
        streamStatisticsDisplay.setText(state_.toString()
        );

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

    void updateProgressBar(int msg_what, long arg1) {
        boolean finalBlockIdKnown = state_.finalBlockId != StreamState.FINAL_BLOCK_ID_UNKNOWN;

        // rescaling logic
        if (finalBlockIdKnown) {
            progressBar_.setTotalSegments((int) state_.finalBlockId + 1);
        }
        if (!finalBlockIdKnown &&
                ((float) state_.highestSegPublished / (float) progressBar_.getTotalSegments()) > 0.90f) {
            progressBar_.setTotalSegments(progressBar_.getTotalSegments() * 2);
        }

        switch (msg_what) {
            case MSG_STREAM_PRODUCER_SEGMENT_PUBLISHED: {
                progressBar_.updateSingleSegmentColor((int) arg1, R.color.blue);
                break;
            }
            case MSG_STREAM_PRODUCER_FINAL_SEGMENT_PUBLISHED: {
                break;
            }
            default: {
                throw new IllegalStateException("unexpected msg_what " + msg_what);
            }
        }

    }

    @Override
    void render() {
        try {
            if (currentIcon_ != null)
                imageLabel_.setImageDrawable(currentIcon_);
            nameDisplay_.setText(Long.toString(syncStreamInfo_.seqNum));
            if (streamPopUpEnabled_)
                enableStreamInfoPopUp();
            progressBar_.render();
        }
        catch (NullPointerException e) {
            Log.e(TAG, "failed to render for " + (streamName_ == null ? "?" : streamName_.toString()) + ", error: " + e.getMessage());
        }
        catch (IllegalStateException e) {
            Log.e(TAG, "failed to render for " + (streamName_ == null ? "?" : streamName_.toString()) + ", error: " + e.getMessage());
        }
    }
}
