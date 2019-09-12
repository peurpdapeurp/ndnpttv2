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

    private EditText channelInput_;
    private EditText nameInput_;
    private Button okButton_;

    // shared preferences object to store login parameters between sessions
    SharedPreferences mPreferences;
    SharedPreferences.Editor mPreferencesEditor;
    private static String USER_NAME = "USER_NAME";
    private static String CHANNEL_NAME = "CHANNEL_NAME";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        mPreferences = getSharedPreferences("mPreferences", Context.MODE_PRIVATE);
        mPreferencesEditor = mPreferences.edit();

        channelInput_ = (EditText) findViewById(R.id.channel_input);
        nameInput_ = (EditText) findViewById(R.id.user_name_input);

        okButton_ = (Button) findViewById(R.id.ok_button);

        channelInput_.setText(mPreferences.getString(CHANNEL_NAME, "DefaultChannelName"));
        nameInput_.setText(mPreferences.getString(USER_NAME, "DefaultUserName"));

        okButton_.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String channel = channelInput_.getText().toString().trim();
                String name = nameInput_.getText().toString().trim();

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

                // all the inputs are good, save them for next time

                mPreferencesEditor.putString(CHANNEL_NAME, channel).commit();
                mPreferencesEditor.putString(USER_NAME, name).commit();

                Intent intent = new Intent(LoginActivity.this, MainActivity.class);

                String[] radioInfo = new String[3];
                radioInfo[IntentInfo.CHANNEL] = channel;
                radioInfo[IntentInfo.USER_NAME] = name;
                intent.putExtra(IntentInfo.LOGIN_CONFIG, radioInfo);

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

