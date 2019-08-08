package com.example.ndnpttv2.front_end;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.example.ndnpttv2.R;
import com.example.ndnpttv2.helpers.Logger;

public class MainActivity extends AppCompatActivity {

    public static MainActivity mainActivityInstance_;

    void PTT_BUTTON_DOWN_LOGIC() {
        Logger.logMessage(System.currentTimeMillis(), Logger.LOG_MODULE_MAIN_ACTIVITY,
                "PTT_BUTTON_DOWN_LOGIC was called.");
    }

    void PTT_BUTTON_UP_LOGIC() {
        Logger.logMessage(System.currentTimeMillis(), Logger.LOG_MODULE_MAIN_ACTIVITY,
                "PTT_BUTTON_UP_LOGIC was called.");
    }

    BroadcastReceiver pttPressListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(PTTButtonPressReceiver.ACTION_PTT_KEY_DOWN)) {
                PTT_BUTTON_DOWN_LOGIC();
            } else if (intent.getAction().equals(PTTButtonPressReceiver.ACTION_PTT_KEY_UP)) {
                PTT_BUTTON_UP_LOGIC();
            } else {
                Logger.logMessage(System.currentTimeMillis(), Logger.LOG_MODULE_MAIN_ACTIVITY,
                        "pttPressListener got unexpected intent: " + intent.getAction());
            }
        }
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        mainActivityInstance_ = this;

        LocalBroadcastManager.getInstance(this).registerReceiver(pttPressListener,
                PTTButtonPressReceiver.getIntentFilter());

    }

    @Override
    protected void onDestroy() {

        LocalBroadcastManager.getInstance(this).unregisterReceiver(pttPressListener);

        super.onDestroy();
    }

    public static MainActivity getInstance() {
        return mainActivityInstance_;
    }

}
