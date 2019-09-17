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
import android.widget.Button;
import android.widget.TextView;

import com.example.ndnpttv2.R;
import com.example.ndnpttv2.back_end.ProgressEventInfo;
import com.example.ndnpttv2.front_end.custom_progress_bar.CustomProgressBar;

import net.named_data.jndn.Name;

import java.util.concurrent.LinkedTransferQueue;

public abstract class ProgressBarFragment extends Fragment {

    TextView nameDisplay_;
    CustomProgressBar progressBar_;

    private Name streamName_;
    private Handler handler_;
    private boolean viewInitialized_ = false;
    private LinkedTransferQueue<Message> prematureMessages_;

    ProgressBarFragment(Name streamName, Looper mainThreadLooper) {

        streamName_ = streamName;
        prematureMessages_ = new LinkedTransferQueue<>();

        handler_ = new Handler(mainThreadLooper) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                handleMessageInternal(msg);
            }
        };
        
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
        String displayString = getString(R.string.progress_bar_name_label) + " " + streamName_.toString();
        nameDisplay_.setText(displayString);
        nameDisplay_.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                showPopUp(view);
                return true;
            }
        });

        progressBar_ = (CustomProgressBar) view.findViewById(R.id.progress_bar);
        progressBar_.getThumb().setAlpha(0);
        progressBar_.setEnabled(false);
        progressBar_.init();

        viewInitialized_ = true;

        while (prematureMessages_.size() != 0) {
            Message msg = prematureMessages_.poll();
            if (msg == null) continue;
            msg.sendToTarget();
        }

        return view;
    }

    void processProgressEvent(int msg_what, ProgressEventInfo progressEventInfo) {
        Message msg = handler_.obtainMessage(msg_what, progressEventInfo);
        if (!viewInitialized_) {
            prematureMessages_.put(msg);
        }
        else {
            msg.sendToTarget();
        }
    }
    
    abstract void handleMessageInternal(Message msg);

    abstract Name getStreamName();

    abstract void showPopUp(View anchorView);

    long getNumFrames(long finalBlockId, long framesPerSegment) {
        return finalBlockId * framesPerSegment + framesPerSegment - 1;
    }
}