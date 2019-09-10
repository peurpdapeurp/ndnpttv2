package com.example.ndnpttv2.front_end;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.example.ndnpttv2.R;
import com.example.ndnpttv2.back_end.StreamInfo;
import com.example.ndnpttv2.back_end.app_logic_module.AppLogicModule;

import net.named_data.jndn.Name;

public class UiManager {

    private static final String TAG = "UiManager";

    // Public constants
    public static final int EVENT_PRODUCTION_WINDOW_GROW = 0;
    public static final int EVENT_AUDIO_RETRIEVED = 1;
    public static final int EVENT_FRAME_SKIPPED = 2;
    public static final int EVENT_FRAME_PLAYED = 3;
    public static final int EVENT_FINAL_FRAME_NUM_LEARNED = 4;
    public static final int EVENT_FINAL_BLOCK_ID_LEARNED = 5;

    private MainActivity mainActivity_;
    private Context ctx_;

    private Button incrementStreamIdButton_;
    private EditText streamIdInput_;
    private Button notifyNewStreamButton_;

    public UiManager(MainActivity mainActivity) {

        mainActivity_ = mainActivity;
        ctx_ = mainActivity_.getApplication();

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
                        .obtainMessage(AppLogicModule.MSG_SYNC_NEW_STREAM_AVAILABLE,
                                new StreamInfo(streamName, 1, 8000))
                        .sendToTarget();
            }
        });

    }

}
