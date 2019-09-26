package com.example.ndnpttv2.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;

import com.example.ndnpttv2.back_end.pq_module.stream_consumer.StreamConsumer;
import com.example.ndnpttv2.back_end.rec_module.RecorderModule;
import com.example.ndnpttv2.back_end.structs.SyncStreamInfo;
import com.example.ndnpttv2.back_end.wifi_module.WifiModule;
import com.example.ndnpttv2.front_end.MainActivity;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

public class Logger {

    // Messages
    private static final int MSG_LOG_EVENT = 0;

    private static File logFile_;
    private static OutputStream logFileOutputStream_;
    private static Handler handler_;

    // Logged event types
    public static final String APP_INIT = "APP_INIT";
    public static final String STREAMCONSUMER_INTEREST_TRANSMIT = "STREAMCONSUMER_INTEREST_TRANSMIT";
    public static final String STREAMCONSUMER_INTEREST_RTO = "STREAMCONSUMER_INTEREST_RTO";
    public static final String STREAMCONSUMER_AUDIO_DATA_RECEIVE = "STREAMCONSUMER_AUDIO_DATA_RECEIVE";
    public static final String STREAMCONSUMER_NACK_RECEIVE = "STREAMCONSUMER_NACK_RECEIVE";
    public static final String STREAMCONSUMER_INTEREST_SKIP = "STREAMCONSUMER_INTEREST_SKIP";
    public static final String STREAMCONSUMER_FETCHING_COMPLETE = "STREAMCONSUMER_FETCHING_COMPLETE";
    public static final String STREAMCONSUMER_BUFFERING_START = "STREAMCONSUMER_BUFFERING_START";
    public static final String STREAMCONSUMER_BUFFERING_COMPLETE = "STREAMCONSUMER_BUFFERING_COMPLETE";
    public static final String PQMODULE_NEW_STREAM_AVAILABLE = "PQMODULE_NEW_STREAM_AVAILABLE";
    public static final String PQMODULE_NEW_WIFI_STATE = "PQMODULE_NEW_WIFI_STATE";
    public static final String RECMODULE_RECORD_REQUEST_START = "RECMODULE_RECORD_REQUEST_START";
    public static final String RECMODULE_RECORD_REQUEST_STOP = "RECMODULE_RECORD_REQUEST_STOP";
    public static final String SYNCMODULE_PUBLISHED_NEW_STREAM = "SYNCMODULE_PUBLISHED_NEW_STREAM";
    public static final String SYNCMODULE_DISCOVERED_NEW_STREAM = "SYNCMODULE_DISCOVERED_NEW_STREAM";
    public static final String WIFIMODULE_NEW_WIFI_STATE = "WIFIMODULE_NEW_WIFI_STATE";

    public static class LogEventInfo {
        public LogEventInfo(String eventString, long timestamp, int arg1, Object obj1, Object obj2) {
            this.eventString = eventString;
            this.timestamp = timestamp;
            this.arg1 = arg1;
            this.obj1 = obj1;
            this.obj2 = obj2;
        }
        String eventString;
        long timestamp;
        int arg1;
        Object obj1;
        Object obj2;
    }

    public static void initialize(Context ctx, long start_time, Looper mainThreadLooper) {
        java.util.Date d = new java.util.Date(start_time);
        String timeStr = new SimpleDateFormat("dd-MMM HH:mm:ss.SSS").format(d);
        logFile_ = new File(ctx.getExternalCacheDir().getAbsolutePath() + "/" + timeStr + ".log");
        try {
            logFileOutputStream_ = new FileOutputStream(logFile_);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new IllegalStateException("unable to create log file " + logFile_.getAbsolutePath());
        }
        handler_ = new Handler(mainThreadLooper) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                switch (msg.what) {
                    case MSG_LOG_EVENT: {
                        LogEventInfo logEventInfo = (LogEventInfo) msg.obj;
                        logEventInternal(logEventInfo);
                        break;
                    }
                    default: {
                        throw new IllegalStateException("unexpected msg.what " + msg.what);
                    }
                }
            }
        };
    }

    public static void logEvent(LogEventInfo logEventInfo) {
        handler_.obtainMessage(MSG_LOG_EVENT, logEventInfo).sendToTarget();
    }

    private static void logEventInternal(LogEventInfo logEventInfo) {
        String eventString = logEventInfo.eventString;
        ArrayList<String> params = new ArrayList<>();
        if (eventString.equals(APP_INIT)) {
            MainActivity.Settings settings = (MainActivity.Settings) logEventInfo.obj1;
            params.add(settings.channelName);
            params.add(settings.userName);
            params.add(Integer.toString(settings.producerSamplingRate));
            params.add(Integer.toString(settings.producerFramesPerSegment));
            params.add(Integer.toString(settings.consumerJitterBufferSize));
            params.add(Integer.toString(settings.consumerMaxHistoricalStreamFetchTimeMs));
            params.add(settings.accessPointIpAddress);
        }
        else if (eventString.equals(STREAMCONSUMER_INTEREST_TRANSMIT)) {
            String interestName = (String) logEventInfo.obj1;
            params.add(interestName);
        }
        else if (eventString.equals(STREAMCONSUMER_INTEREST_RTO)) {
            String interestName = (String) logEventInfo.obj1;
            params.add(interestName);
        }
        else if (eventString.equals(STREAMCONSUMER_AUDIO_DATA_RECEIVE)) {
            String dataName = (String) logEventInfo.obj1;
            params.add(dataName);
        }
        else if (eventString.equals(STREAMCONSUMER_NACK_RECEIVE)) {
            String dataName = (String) logEventInfo.obj1;
            params.add(dataName);
        }
        else if (eventString.equals(STREAMCONSUMER_INTEREST_SKIP)) {
            String interestName = (String) logEventInfo.obj1;
            params.add(interestName);
        }
        else if (eventString.equals(STREAMCONSUMER_FETCHING_COMPLETE)) {
            String streamName = (String) logEventInfo.obj1;
            String fetchCompleteCodeString = "";
            switch (logEventInfo.arg1) {
                case StreamConsumer.FETCH_COMPLETE_CODE_SUCCESS: {
                    fetchCompleteCodeString = "SUCCESS";
                    break;
                }
                case StreamConsumer.FETCH_COMPLETE_CODE_META_DATA_TIMEOUT: {
                    fetchCompleteCodeString = "FAILURE_META_DATA_TIMEOUT";
                    break;
                }
                case StreamConsumer.FETCH_COMPLETE_CODE_MEDIA_DATA_TIMEOUT: {
                    fetchCompleteCodeString = "FAILURE_MEDIA_DATA_TIMEOUT";
                    break;
                }
                case StreamConsumer.FETCH_COMPLETE_CODE_STREAM_RECORDED_TOO_FAR_IN_PAST: {
                    fetchCompleteCodeString = "FAILURE_STREAM_RECORDED_TOO_FAR_IN_PAST";
                    break;
                }
                default: {
                    throw new IllegalStateException("unexpected fetch complete code " + logEventInfo.arg1);
                }
            }
            params.add(streamName);
            params.add(fetchCompleteCodeString);
        }
        else if (eventString.equals(STREAMCONSUMER_BUFFERING_START)) {
            String streamName = (String) logEventInfo.obj1;
            params.add(streamName);
        }
        else if (eventString.equals(STREAMCONSUMER_BUFFERING_COMPLETE)) {
            String streamName = (String) logEventInfo.obj1;
            params.add(streamName);
        }
        else if (eventString.equals(PQMODULE_NEW_STREAM_AVAILABLE)) {
            String streamName = (String) logEventInfo.obj1;
            params.add(streamName);
        }
        else if (eventString.equals(PQMODULE_NEW_WIFI_STATE)) {
            int wifiConnectionState = logEventInfo.arg1;
            String stateString = "";
            if (wifiConnectionState == WifiModule.CONNECTED) {
                stateString = "CONNECTED";
            }
            else if (wifiConnectionState == WifiModule.DISCONNECTED) {
                stateString = "DISCONNECTED";
            }
            else {
                stateString = "UNRECOGNIZED WIFI STATE " + wifiConnectionState;
            }
            params.add(stateString);
        }
        else if (eventString.equals(RECMODULE_RECORD_REQUEST_START)) {
            int validity = logEventInfo.arg1;
            String validityString = "";
            if (validity == RecorderModule.REQUEST_VALID) {
                validityString = "valid";
            }
            else {
                validityString = "invalid";
            }
            params.add(validityString);
        }
        else if (eventString.equals(RECMODULE_RECORD_REQUEST_STOP)) {
            // no parameters
        }
        else if (eventString.equals(SYNCMODULE_PUBLISHED_NEW_STREAM)) {
            Long seqNum = (Long) logEventInfo.obj1;
            params.add(Long.toString(seqNum));
        }
        else if (eventString.equals(SYNCMODULE_DISCOVERED_NEW_STREAM)) {
            SyncStreamInfo syncStreamInfo = (SyncStreamInfo) logEventInfo.obj1;
            params.add(syncStreamInfo.channelUserSession.channelName);
            params.add(syncStreamInfo.channelUserSession.userName);
            params.add(Long.toString(syncStreamInfo.channelUserSession.sessionId));
            params.add(Long.toString(syncStreamInfo.seqNum));
        }
        else if (eventString.equals(WIFIMODULE_NEW_WIFI_STATE)) {
            int wifiConnectionState = logEventInfo.arg1;
            String stateString = "";
            if (wifiConnectionState == WifiModule.CONNECTED) {
                stateString = "CONNECTED";
            }
            else if (wifiConnectionState == WifiModule.DISCONNECTED) {
                stateString = "DISCONNECTED";
            }
            else {
                stateString = "UNRECOGNIZED WIFI STATE " + wifiConnectionState;
            }
            params.add(stateString);
        }
        else {
            throw new IllegalStateException("unexpected eventString " + eventString);
        }
        logMessage(generateLogMessage(eventString, logEventInfo.timestamp, params));
    }

    private static String generateLogMessage(String eventString, long timestamp, ArrayList<String> parameters) {
        String message = "";
        message += eventString;
        message += ",";
        message += Long.toString(timestamp);
        message += ":";
        message += " ";
        for (String param : parameters) {
            message += param + ",";
        }
        message += "\n";
        return message;
    }

    private static void logMessage(String message) {
        try {
            logFileOutputStream_.write(message.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
            throw new IllegalStateException("failed to write to log file " + logFile_.getAbsolutePath());
        }
    }

}
