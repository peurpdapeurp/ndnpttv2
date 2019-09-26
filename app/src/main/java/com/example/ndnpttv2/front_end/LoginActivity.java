package com.example.ndnpttv2.front_end;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import com.example.ndnpttv2.R;
import com.example.ndnpttv2.util.Logger;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.util.Patterns;
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
    private final int[] SAMPLING_RATE_OPTIONS = {8000};
    private HashMap<Integer, Integer> spinnerIndexToSamplingRate_;
    private final String DEFAULT_CHANNEL_NAME = "DefaultChannelName";
    private final String DEFAULT_USER_NAME = "DefaultUserName";
    private final int DEFAULT_PRODUCER_SAMPLING_RATE_INDEX = 0;
    private final int DEFAULT_PRODUCER_FRAMES_PER_SEGMENT = 1;
    private final int DEFAULT_CONSUMER_JITTER_BUFFER_SIZE = 5;
    private final int DEFAULT_CONSUMER_MAX_HISTORICAL_STREAM_FETCH_TIME_MS = 300000; // 5 minutes
    private final int DEFAULT_CONSUMER_MEDIA_DATA_TIMEOUT_MS = 5000;
    private final int DEFAULT_CONSUMER_META_DATA_TIMEOUT_MS = 5000;
    private final String DEFAULT_ACCESS_POINT_IP_ADDRESS = "1.1.1.1";

    private EditText channelInput_;
    private EditText nameInput_;
    private Spinner producerSamplingRateInput_;
    private EditText producerFramesPerSegmentInput_;
    private EditText consumerJitterBufferSizeInput_;
    private EditText consumerMaxHistoricalStreamFetchTimeMsInput_;
    private EditText consumerMediaDataTimeoutMsInput_;
    private EditText consumerMetaDataTimeoutMsInput_;
    private EditText accessPointIpAddressInput_;
    private Button okButton_;

    private Toast currentErrorToast_;

    // shared preferences object to store login parameters between sessions
    SharedPreferences mPreferences;
    SharedPreferences.Editor mPreferencesEditor;
    private static String USER_NAME = "USER_NAME";
    private static String CHANNEL_NAME = "CHANNEL_NAME";
    private static String PRODUCER_SAMPLING_RATE_INDEX = "PRODUCER_SAMPLING_RATE_INDEX";
    private static String PRODUCER_FRAMES_PER_SEGMENT = "PRODUCER_FRAMES_PER_SEGMENT";
    private static String CONSUMER_JITTER_BUFFER_SIZE = "CONSUMER_JITTER_BUFFER_SIZE";
    private static String CONSUMER_MAX_HISTORICAL_STREAM_FETCH_TIME_MS = "CONSUMER_MAX_HISTORICAL_STREAM_FETCH_TIME_MS";
    private static String CONSUMER_MEDIA_DATA_TIMEOUT_MS = "CONSUMER_MEDIA_DATA_TIMEOUT_MS";
    private static String CONSUMER_META_DATA_TIMEOUT_MS = "CONSUMER_META_DATA_TIMEOUT_MS";
    private static String ACCESS_POINT_IP_ADDRESS = "ACCESS_POINT_IP_ADDRESS";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        Logger.initialize(this, System.currentTimeMillis(), getMainLooper());

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
        consumerMaxHistoricalStreamFetchTimeMsInput_ = (EditText) findViewById(R.id.consumer_max_historical_stream_fetch_time_ms_input);
        consumerMediaDataTimeoutMsInput_ = (EditText) findViewById(R.id.consumer_media_data_timeout_ms_input);
        consumerMetaDataTimeoutMsInput_ = (EditText) findViewById(R.id.consumer_meta_data_timeout_ms_input);
        accessPointIpAddressInput_ = (EditText) findViewById(R.id.access_point_ip_address_input);

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
        consumerMaxHistoricalStreamFetchTimeMsInput_.setText(
                Integer.toString(mPreferences.getInt(CONSUMER_MAX_HISTORICAL_STREAM_FETCH_TIME_MS,
                        DEFAULT_CONSUMER_MAX_HISTORICAL_STREAM_FETCH_TIME_MS)));
        consumerMediaDataTimeoutMsInput_.setText(
                Integer.toString(mPreferences.getInt(CONSUMER_MEDIA_DATA_TIMEOUT_MS,
                        DEFAULT_CONSUMER_MEDIA_DATA_TIMEOUT_MS)));
        consumerMetaDataTimeoutMsInput_.setText(
                Integer.toString(mPreferences.getInt(CONSUMER_META_DATA_TIMEOUT_MS,
                        DEFAULT_CONSUMER_META_DATA_TIMEOUT_MS)));
        accessPointIpAddressInput_.setText(mPreferences.getString(ACCESS_POINT_IP_ADDRESS, DEFAULT_ACCESS_POINT_IP_ADDRESS));

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
                int consumerMaxHistoricalStreamFetchTimeMs;
                try {
                    consumerMaxHistoricalStreamFetchTimeMs = Integer.parseInt(
                            consumerMaxHistoricalStreamFetchTimeMsInput_.getText().toString().trim());
                }
                catch (Exception e) {
                    showErrorToast("Please enter a valid consumer max historical stream fetch time.");
                    return;
                }
                int consumerMediaDataTimeoutMs;
                try {
                    consumerMediaDataTimeoutMs = Integer.parseInt(
                            consumerMediaDataTimeoutMsInput_.getText().toString().trim());
                }
                catch (Exception e) {
                    showErrorToast("Please enter a valid consumer media data timeout.");
                    return;
                }
                int consumerMetaDataTimeoutMs;
                try {
                    consumerMetaDataTimeoutMs = Integer.parseInt(
                            consumerMetaDataTimeoutMsInput_.getText().toString().trim());
                }
                catch (Exception e) {
                    showErrorToast("Please enter a valid consumer meta data timeout.");
                    return;
                }

                String accessPointIpAddress = accessPointIpAddressInput_.getText().toString().trim();

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
                else if (consumerMaxHistoricalStreamFetchTimeMs < 1) {
                    showErrorToast("Please enter a valid consumer max historical stream fetch time.");
                    return;
                }
                else if (consumerMediaDataTimeoutMs < 1) {
                    showErrorToast("Please enter a valid consumer media data timeout.");
                    return;
                }
                else if (consumerMetaDataTimeoutMs < 1) {
                    showErrorToast("Please enter a valid consumer meta data timeout.");
                    return;
                }
                else if (!Patterns.IP_ADDRESS.matcher(accessPointIpAddress).matches()) {
                    showErrorToast("Please enter a valid access point ip address.");
                    return;
                }

                // all the inputs are good, save them for next time
                mPreferencesEditor.putString(CHANNEL_NAME, channel).commit();
                mPreferencesEditor.putString(USER_NAME, name).commit();
                mPreferencesEditor.putInt(PRODUCER_SAMPLING_RATE_INDEX, producerSamplingRateIndex).commit();
                mPreferencesEditor.putInt(PRODUCER_FRAMES_PER_SEGMENT, producerFramesPerSegment).commit();
                mPreferencesEditor.putInt(CONSUMER_JITTER_BUFFER_SIZE, consumerJitterBufferSize).commit();
                mPreferencesEditor.putInt(CONSUMER_MAX_HISTORICAL_STREAM_FETCH_TIME_MS, consumerMaxHistoricalStreamFetchTimeMs).commit();
                mPreferencesEditor.putInt(CONSUMER_MEDIA_DATA_TIMEOUT_MS, consumerMediaDataTimeoutMs).commit();
                mPreferencesEditor.putInt(CONSUMER_META_DATA_TIMEOUT_MS, consumerMetaDataTimeoutMs).commit();
                mPreferencesEditor.putString(ACCESS_POINT_IP_ADDRESS, accessPointIpAddress).commit();

                Intent intent = new Intent(LoginActivity.this, MainActivity.class);

                String[] configInfo = new String[9];
                configInfo[IntentInfo.CHANNEL_NAME] = channel;
                configInfo[IntentInfo.USER_NAME] = name;
                configInfo[IntentInfo.PRODUCER_SAMPLING_RATE] = Integer.toString(SAMPLING_RATE_OPTIONS[producerSamplingRateIndex]);
                configInfo[IntentInfo.PRODUCER_FRAMES_PER_SEGMENT] = Integer.toString(producerFramesPerSegment);
                configInfo[IntentInfo.CONSUMER_JITTER_BUFFER_SIZE] = Integer.toString(consumerJitterBufferSize);
                configInfo[IntentInfo.CONSUMER_MAX_HISTORICAL_STREAM_FETCH_TIME_MS] = Integer.toString(consumerMaxHistoricalStreamFetchTimeMs);
                configInfo[IntentInfo.CONSUMER_MEDIA_DATA_TIMEOUT_MS] = Integer.toString(consumerMediaDataTimeoutMs);
                configInfo[IntentInfo.CONSUMER_META_DATA_TIMEOUT_MS] = Integer.toString(consumerMetaDataTimeoutMs);
                configInfo[IntentInfo.ACCESS_POINT_IP_ADDRESS] = accessPointIpAddress;
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
        if (currentErrorToast_ != null) {
            currentErrorToast_.cancel();
        }
        currentErrorToast_ = Toast.makeText(LoginActivity.this,
                msg, Toast.LENGTH_SHORT);
        currentErrorToast_.show();
    }

}

