package com.example.ndnpttv2.front_end;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import com.example.ndnpttv2.R;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;

public class LoginActivity extends AppCompatActivity {

    // Private constants
    private final int[] SAMPLING_RATE_OPTIONS = {8000, 11025, 16000, 22050, 44100};
    private HashMap<Integer, Integer> spinnerIndexToSamplingRate_;
    private final String DEFAULT_CHANNEL_NAME = "DefaultChannelName";
    private final String DEFAULT_USER_NAME = "DefaultUserName";
    private final int DEFAULT_PRODUCER_SAMPLING_RATE_INDEX = 0;
    private final int DEFAULT_PRODUCER_FRAMES_PER_SEGMENT = 1;
    private final int DEFAULT_CONSUMER_JITTER_BUFFER_SIZE = 5;

    private EditText channelInput_;
    private EditText nameInput_;
    private Spinner producerSamplingRateInput_;
    private EditText producerFramesPerSegmentInput_;
    private EditText consumerJitterBufferSizeInput_;
    private Button okButton_;

    // shared preferences object to store login parameters between sessions
    SharedPreferences mPreferences;
    SharedPreferences.Editor mPreferencesEditor;
    private static String USER_NAME = "USER_NAME";
    private static String CHANNEL_NAME = "CHANNEL_NAME";
    private static String PRODUCER_SAMPLING_RATE_INDEX = "PRODUCER_SAMPLING_RATE_INDEX";
    private static String PRODUCER_FRAMES_PER_SEGMENT = "PRODUCER_FRAMES_PER_SEGMENT";
    private static String CONSUMER_JITTER_BUFFER_SIZE = "CONSUMER_JITTER_BUFFER_SIZE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        spinnerIndexToSamplingRate_ = new HashMap<>();
        for (int i = 0; i < SAMPLING_RATE_OPTIONS.length; i++) {
            spinnerIndexToSamplingRate_.put(i, SAMPLING_RATE_OPTIONS[i]);
        }

        mPreferences = getSharedPreferences("mPreferences", Context.MODE_PRIVATE);
        mPreferencesEditor = mPreferences.edit();

        channelInput_ = (EditText) findViewById(R.id.channel_input);
        nameInput_ = (EditText) findViewById(R.id.user_name_input);
        producerSamplingRateInput_ = (Spinner) findViewById(R.id.producer_sampling_rate_input);
        producerFramesPerSegmentInput_ = (EditText) findViewById(R.id.producer_frames_per_segment_input);
        consumerJitterBufferSizeInput_ = (EditText) findViewById(R.id.consumer_jitter_buffer_size_input);

        okButton_ = (Button) findViewById(R.id.ok_button);

        channelInput_.setText(mPreferences.getString(CHANNEL_NAME, DEFAULT_CHANNEL_NAME));
        nameInput_.setText(mPreferences.getString(USER_NAME, DEFAULT_USER_NAME));
        ArrayAdapter<Integer> spinnerArrayAdapter = new ArrayAdapter<Integer>(this, android.R.layout.simple_spinner_item,
                new ArrayList<Integer>() {{ for (int i : SAMPLING_RATE_OPTIONS) add(i); }});
        spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); // The drop down vieww
        producerSamplingRateInput_.setAdapter(spinnerArrayAdapter);
        producerSamplingRateInput_.invalidate();
        producerSamplingRateInput_.setSelection(mPreferences.getInt(PRODUCER_SAMPLING_RATE_INDEX,
                DEFAULT_PRODUCER_SAMPLING_RATE_INDEX), true);
        producerFramesPerSegmentInput_.setText(
                Integer.toString(mPreferences.getInt(PRODUCER_FRAMES_PER_SEGMENT, DEFAULT_PRODUCER_FRAMES_PER_SEGMENT)));
        consumerJitterBufferSizeInput_.setText(
                Integer.toString(mPreferences.getInt(CONSUMER_JITTER_BUFFER_SIZE, DEFAULT_CONSUMER_JITTER_BUFFER_SIZE)));

        okButton_.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String channel = channelInput_.getText().toString().trim();
                String name = nameInput_.getText().toString().trim();
                int producerSamplingRateIndex = producerSamplingRateInput_.getSelectedItemPosition();
                int producerFramesPerSegment;
                try {
                    producerFramesPerSegment = Integer.parseInt(producerFramesPerSegmentInput_.getText().toString().trim());
                }
                catch (Exception e) {
                    showErrorToast("Please enter a valid producer frames per segment.");
                    return;
                }
                int consumerJitterBufferSize;
                try {
                    consumerJitterBufferSize = Integer.parseInt(consumerJitterBufferSizeInput_.getText().toString().trim());
                }
                catch (Exception e) {
                    showErrorToast("Please enter a valid consumer jitter buffer size.");
                    return;
                }

                if (channel.equals("")) {
                    showErrorToast("Please enter a valid channel name.");
                    return;
                }
                else if (name.equals("")) {
                    showErrorToast("Please enter a valid user name.");
                    return;
                }
                else if (producerFramesPerSegment < 1) {
                    showErrorToast("Please enter a valid producer frames per segment.");
                    return;
                }
                else if (consumerJitterBufferSize < 1) {
                    showErrorToast("Please enter a valid consumer jitter buffer size.");
                    return;
                }

                // all the inputs are good, save them for next time
                mPreferencesEditor.putString(CHANNEL_NAME, channel).commit();
                mPreferencesEditor.putString(USER_NAME, name).commit();
                mPreferencesEditor.putInt(PRODUCER_SAMPLING_RATE_INDEX, producerSamplingRateIndex).commit();
                mPreferencesEditor.putInt(PRODUCER_FRAMES_PER_SEGMENT, producerFramesPerSegment).commit();
                mPreferencesEditor.putInt(CONSUMER_JITTER_BUFFER_SIZE, consumerJitterBufferSize).commit();

                Intent intent = new Intent(LoginActivity.this, MainActivity.class);

                String[] configInfo = new String[5];
                configInfo[IntentInfo.CHANNEL_NAME] = channel;
                configInfo[IntentInfo.USER_NAME] = name;
                configInfo[IntentInfo.PRODUCER_SAMPLING_RATE] = Integer.toString(SAMPLING_RATE_OPTIONS[producerSamplingRateIndex]);
                configInfo[IntentInfo.PRODUCER_FRAMES_PER_SEGMENT] = Integer.toString(producerFramesPerSegment);
                configInfo[IntentInfo.CONSUMER_JITTER_BUFFER_SIZE] = Integer.toString(consumerJitterBufferSize);
                intent.putExtra(IntentInfo.LOGIN_CONFIG, configInfo);

                setResult(RESULT_OK, intent);
                finish();
            }
        });

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        hideKeyboard(this);
        return super.onTouchEvent(event);
    }

    public static void hideKeyboard(Activity activity) {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        //Find the currently focused view, so we can grab the correct window token from it.
        View view = activity.getCurrentFocus();
        //If no view currently has focus, create a new one, just so we can grab a window token from it
        if (view == null) {
            view = new View(activity);
        }
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void showErrorToast(String msg) {
        Toast toast = Toast.makeText(LoginActivity.this,
                msg, Toast.LENGTH_SHORT);
        toast.show();
    }

}

