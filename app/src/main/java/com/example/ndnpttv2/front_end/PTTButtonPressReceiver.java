package com.example.ndnpttv2.front_end;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.ndnpttv2.helpers.InterModuleInfo;
import com.example.ndnpttv2.helpers.Logger;

public class PTTButtonPressReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (MainActivity.getInstance() != null) {
            if (intent.getAction().equals("com.sonim.intent.action.PTT_KEY_DOWN")) {
                Intent pttButtonPressedIntent = new Intent(InterModuleInfo.PTTButtonPressReceiver_PTT_BUTTON_DOWN);
                LocalBroadcastManager.getInstance(MainActivity.getInstance()).sendBroadcast(pttButtonPressedIntent);
            } else if (intent.getAction().equals("com.sonim.intent.action.PTT_KEY_UP")) {
                Intent pttButtonPressedIntent = new Intent(InterModuleInfo.PTTButtonPressReceiver_PTT_BUTTON_UP);
                LocalBroadcastManager.getInstance(MainActivity.getInstance()).sendBroadcast(pttButtonPressedIntent);
            } else {
                Logger.logMessage(System.currentTimeMillis(), Logger.LOG_MODULE_PTT_BUTTON_PRESS_RECEIVER,
                        "Unexpected intent: " + intent.getAction());
            }
        }
    }

    public static IntentFilter getIntentFilter() {
        IntentFilter ret = new IntentFilter();
        ret.addAction(InterModuleInfo.PTTButtonPressReceiver_PTT_BUTTON_DOWN);
        ret.addAction(InterModuleInfo.PTTButtonPressReceiver_PTT_BUTTON_UP);

        return ret;
    }
}
