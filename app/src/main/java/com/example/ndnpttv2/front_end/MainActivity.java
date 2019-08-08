package com.example.ndnpttv2.front_end;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.os.Bundle;

import com.example.ndnpttv2.R;
import com.example.ndnpttv2.back_end.back_end_impl.Recorder;
import com.example.ndnpttv2.back_end.back_end_impl.Streamer;
import com.example.ndnpttv2.helpers.InterModuleInfo;
import com.example.ndnpttv2.helpers.Logger;

public class MainActivity extends AppCompatActivity {

    public static MainActivity mainActivityInstance_;

    public Recorder recorder_;
    public Streamer streamer_;

    BroadcastReceiver pttButtonPressReceiverListener_ = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(InterModuleInfo.PTTButtonPressReceiver_PTT_BUTTON_DOWN)) {
                PTT_BUTTON_DOWN_LOGIC();
            } else if (intent.getAction().equals(InterModuleInfo.PTTButtonPressReceiver_PTT_BUTTON_UP)) {
                PTT_BUTTON_UP_LOGIC();
            } else {
                Logger.logMessage(System.currentTimeMillis(), Logger.LOG_MODULE_MAIN_ACTIVITY,
                        "pttButtonPressReceiverListener_ got unexpected intent: " + intent.getAction());
            }
        }
    };

    void PTT_BUTTON_DOWN_LOGIC() {
        Logger.logMessage(System.currentTimeMillis(), Logger.LOG_MODULE_MAIN_ACTIVITY,
                "PTT_BUTTON_DOWN_LOGIC was called.");
        LocalBroadcastManager.getInstance(this).sendBroadcast(
                new Intent(InterModuleInfo.MainActivity_RECORD_REQUEST_START));
    }

    void PTT_BUTTON_UP_LOGIC() {
        Logger.logMessage(System.currentTimeMillis(), Logger.LOG_MODULE_MAIN_ACTIVITY,
                "PTT_BUTTON_UP_LOGIC was called.");
        LocalBroadcastManager.getInstance(this).sendBroadcast(
                new Intent(InterModuleInfo.MainActivity_RECORD_REQUEST_STOP));
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        mainActivityInstance_ = this;

        recorder_ = new Recorder(this.getApplicationContext());
        streamer_ = new Streamer(MainActivity.getInstance().getApplicationContext(),
                getExternalCacheDir().getAbsolutePath());

        LocalBroadcastManager.getInstance(this).registerReceiver(pttButtonPressReceiverListener_,
                PTTButtonPressReceiver.getIntentFilter());

    }

    @Override
    protected void onDestroy() {

        LocalBroadcastManager.getInstance(this).unregisterReceiver(pttButtonPressReceiverListener_);

        super.onDestroy();
    }

    public static IntentFilter getIntentFilter() {
        IntentFilter ret = new IntentFilter();
        ret.addAction(InterModuleInfo.MainActivity_RECORD_REQUEST_START);
        ret.addAction(InterModuleInfo.MainActivity_RECORD_REQUEST_STOP);

        return ret;
    }

    public static MainActivity getInstance() {
        return mainActivityInstance_;
    }

}
