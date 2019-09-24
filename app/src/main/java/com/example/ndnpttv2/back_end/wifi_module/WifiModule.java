package com.example.ndnpttv2.back_end.wifi_module;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.ndnpttv2.util.Logger;
import com.pploder.events.Event;
import com.pploder.events.SimpleEvent;

import java.util.function.Consumer;

public class WifiModule {

    private static final String TAG = "WifiModule";

    // Public constants
    public static final int CONNECTED = 0;
    public static final int DISCONNECTED = 1;

    // Private constants
    private static final int PROCESSING_INTERVAL_MS = 50;

    // Messages
    private static final int MSG_DO_SOME_WORK = 0;

    // Events
    public Event<Integer> eventWifiStateChanged;

    private Handler handler_;
    private WifiManager wifiManager_;
    private int lastWifiConnectionState_;

    public WifiModule(Context ctx, Looper mainThreadLooper, Consumer<Integer> wifiStateChangeListener) {

        wifiManager_ = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);

        lastWifiConnectionState_ = checkWifiConnectionState();

        eventWifiStateChanged = new SimpleEvent<>();

        handler_ = new Handler(mainThreadLooper) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                switch (msg.what) {
                    case MSG_DO_SOME_WORK: {
                        doSomeWork();
                        break;
                    }
                    default: {
                        throw new IllegalStateException("unexpected msg.what " + msg.what);
                    }
                }
            }
        };

        handler_.obtainMessage(MSG_DO_SOME_WORK).sendToTarget();

        Log.d(TAG, "Initialized with wifi state " + lastWifiConnectionState_);

        eventWifiStateChanged.addListener(wifiStateChangeListener);

        eventWifiStateChanged.trigger(lastWifiConnectionState_);

        Logger.logEvent(new Logger.LogEventInfo(Logger.WIFIMODULE_NEW_WIFI_STATE, System.currentTimeMillis(),
                lastWifiConnectionState_, null, null));

    }

    private void doSomeWork() {
        int currentWifiConnectionState = checkWifiConnectionState();

        if (currentWifiConnectionState != lastWifiConnectionState_) {
            Log.d(TAG, "detected wifi state change to " + currentWifiConnectionState);
            eventWifiStateChanged.trigger(currentWifiConnectionState);
            lastWifiConnectionState_ = currentWifiConnectionState;
        }
        scheduleNextWork(SystemClock.uptimeMillis());
    }

    private void scheduleNextWork(long thisOperationStartTimeMs) {
        handler_.removeMessages(MSG_DO_SOME_WORK);
        handler_.sendEmptyMessageAtTime(MSG_DO_SOME_WORK, thisOperationStartTimeMs + PROCESSING_INTERVAL_MS);
    }

    // https://stackoverflow.com/questions/3841317/how-do-i-see-if-wi-fi-is-connected-on-android
    private int checkWifiConnectionState() {
        if (wifiManager_.isWifiEnabled()) { // Wi-Fi adapter is ON

            WifiInfo wifiInfo = wifiManager_.getConnectionInfo();

            if (wifiInfo.getNetworkId() == -1) {
                return DISCONNECTED; // Not connected to an access point
            }
            return CONNECTED; // Connected to an access point
        } else {
            return DISCONNECTED; // Wi-Fi adapter is OFF
        }
    }

}
