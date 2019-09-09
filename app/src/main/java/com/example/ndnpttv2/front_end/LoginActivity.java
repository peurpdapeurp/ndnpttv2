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
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class LoginActivity extends AppCompatActivity {

    private EditText m_channel_input = null;
    private EditText m_name_input = null;
    private EditText m_segment_interest_max_reattempts_input = null;
    private EditText m_segment_interest_lifetime_input = null;
    private EditText m_ap_ip_address_input = null;
    private Button m_ok_button = null;

    // shared preferences object to store login parameters for next time
    SharedPreferences mPreferences;
    SharedPreferences.Editor mPreferencesEditor;
    private static String USER_NAME = "USER_NAME";
    private static String CHANNEL_NAME = "CHANNEL_NAME";
    private static String SEGMENT_INTEREST_MAX_REATTEMPTS = "SEGMENT_INTEREST_MAX_REATTEMPTS";
    private static String SEGMENT_INTEREST_LIFETIME = "SEGMENT_INTEREST_LIFETIME";
    private static String AP_IP_ADDRESS = "AP_IP_ADDRESS";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        mPreferences = getSharedPreferences("mPreferences", Context.MODE_PRIVATE);
        mPreferencesEditor = mPreferences.edit();

        m_channel_input = (EditText) findViewById(R.id.channel_input);
        m_name_input = (EditText) findViewById(R.id.name_input);
        m_segment_interest_lifetime_input = (EditText) findViewById(R.id.segment_interest_lifetime_input);
        m_segment_interest_max_reattempts_input = (EditText) findViewById(R.id.segment_interest_max_reattempts_input);
        m_ap_ip_address_input = (EditText) findViewById(R.id.ap_ip_address);

        m_ok_button = (Button) findViewById(R.id.ok_button);

        m_channel_input.setText(mPreferences.getString(CHANNEL_NAME, "/defaultChannel"));
        m_name_input.setText(mPreferences.getString(USER_NAME, "DefaultUsername"));
        m_segment_interest_lifetime_input.setText(mPreferences.getString(SEGMENT_INTEREST_LIFETIME,
                "15000"));
        m_segment_interest_max_reattempts_input.setText(mPreferences.getString(SEGMENT_INTEREST_MAX_REATTEMPTS,
                "5"));
        m_ap_ip_address_input.setText(mPreferences.getString(AP_IP_ADDRESS, "192.168.4.1"));

        m_ok_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String channel = m_channel_input.getText().toString().trim();
                String name = m_name_input.getText().toString().trim();
                String segmentInterestLifetime =
                        m_segment_interest_lifetime_input.getText().toString().trim();
                String segmentInterestMaxReattempts =
                        m_segment_interest_max_reattempts_input.getText().toString().trim();
                String apIpAddress = m_ap_ip_address_input.getText().toString().trim();

                if (channel.equals("")) {
                    Toast toast = Toast.makeText(LoginActivity.this,
                            "Please enter a channel", Toast.LENGTH_SHORT);
                    toast.show();

                    return;
                }
                else if (name.equals("")) {
                    Toast toast = Toast.makeText(LoginActivity.this,
                            "Please enter a name", Toast.LENGTH_SHORT);
                    toast.show();

                    return;
                }
                else if (apIpAddress.equals("")) {
                    Toast toast = Toast.makeText(LoginActivity.this,
                            "Please enter an AP IP ADDRESS", Toast.LENGTH_SHORT);
                    toast.show();

                    return;
                }

                int segmentInterestLifetimeInt = -1;
                int segmentInterestMaxReattemptsInt = -1;
                try {
                    segmentInterestLifetimeInt = Integer.parseInt(segmentInterestLifetime);
                }
                catch (Exception e) {
                    Toast toast = Toast.makeText(LoginActivity.this,
                            "Enter a valid number for segment interest lifetime.", Toast.LENGTH_SHORT);
                    toast.show();
                    e.printStackTrace();
                    return;
                }

                try {
                    segmentInterestMaxReattemptsInt = Integer.parseInt(segmentInterestMaxReattempts);
                }
                catch (Exception e) {
                    Toast toast = Toast.makeText(LoginActivity.this,
                            "Enter a valid number for segment interest max reattempts.", Toast.LENGTH_SHORT);
                    toast.show();
                    e.printStackTrace();
                    return;
                }

                if (segmentInterestLifetimeInt < 0) {
                    Toast toast = Toast.makeText(LoginActivity.this,
                            "Enter a non negative number for segment interest lifetime.", Toast.LENGTH_SHORT);
                    toast.show();
                    return;
                }

                if (segmentInterestMaxReattemptsInt < 0) {
                    Toast toast = Toast.makeText(LoginActivity.this,
                            "Enter a non negative number for segment interest max reattempts.", Toast.LENGTH_SHORT);
                    toast.show();
                    return;
                }

                // all the inputs are good, save them for next time

                mPreferencesEditor.putString(CHANNEL_NAME, channel).commit();
                mPreferencesEditor.putString(USER_NAME, name).commit();
                mPreferencesEditor.putString(SEGMENT_INTEREST_LIFETIME, segmentInterestLifetime).commit();
                mPreferencesEditor.putString(SEGMENT_INTEREST_MAX_REATTEMPTS, segmentInterestMaxReattempts).commit();
                mPreferencesEditor.putString(AP_IP_ADDRESS, apIpAddress);

                Intent intent = new Intent(LoginActivity.this, MainActivity.class);

                String[] radioInfo = new String[5];
                radioInfo[IntentInfo.CHANNEL] = channel;
                radioInfo[IntentInfo.USER_NAME] = name;
                radioInfo[IntentInfo.SEGMENT_INTEREST_MAX_REATTEMPTS] = segmentInterestMaxReattempts;
                radioInfo[IntentInfo.SEGMENT_INTEREST_LIFETIME] = segmentInterestLifetime;
                radioInfo[IntentInfo.AP_IP_ADDRESS] = apIpAddress;
                intent.putExtra(IntentInfo.LoginActivity_CONFIG, radioInfo);

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

}

