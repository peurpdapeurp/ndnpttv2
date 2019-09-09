package com.example.ndnpttv2.front_end;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class PTTButtonPressReceiver extends BroadcastReceiver {

    private static final String TAG = "PTTButtonPressReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals("com.sonim.intent.action.PTT_KEY_DOWN")) {
            Intent pttButtonPressedIntent = new Intent(IntentInfo.PTTButtonPressReceiver_PTT_BUTTON_DOWN);
            LocalBroadcastManager.getInstance(context).sendBroadcast(pttButtonPressedIntent);
        } else if (intent.getAction().equals("com.sonim.intent.action.PTT_KEY_UP")) {
            Intent pttButtonPressedIntent = new Intent(IntentInfo.PTTButtonPressReceiver_PTT_BUTTON_UP);
            LocalBroadcastManager.getInstance(context).sendBroadcast(pttButtonPressedIntent);
        } else {
            Log.d(TAG, "Unexpected intent: " + intent.getAction());
        }
    }

    public static IntentFilter getIntentFilter() {
        IntentFilter ret = new IntentFilter();
        ret.addAction(IntentInfo.PTTButtonPressReceiver_PTT_BUTTON_DOWN);
        ret.addAction(IntentInfo.PTTButtonPressReceiver_PTT_BUTTON_UP);
        return ret;
    }
}
