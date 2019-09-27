package com.example.ndnpttv2.back_end.pq_module.stream_consumer;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.ndnpttv2.back_end.shared_state.PeerStateTable;
import com.example.ndnpttv2.back_end.structs.StreamMetaData;
import com.example.ndnpttv2.back_end.structs.ChannelUserSession;
import com.example.ndnpttv2.back_end.structs.SyncStreamInfo;
import com.example.ndnpttv2.back_end.threads.NetworkThread;
import com.example.ndnpttv2.util.Helpers;
import com.example.ndnpttv2.back_end.Constants;
import com.example.ndnpttv2.back_end.pq_module.stream_consumer.jndn_utils.RttEstimator;
import com.example.ndnpttv2.back_end.pq_module.stream_player.exoplayer_customization.InputStreamDataSource;
import com.example.ndnpttv2.back_end.structs.ProgressEventInfo;
import com.example.ndnpttv2.util.Logger;
import com.google.gson.Gson;
import com.pploder.events.Event;
import com.pploder.events.SimpleEvent;

import net.named_data.jndn.ContentType;
import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.Interest;
import net.named_data.jndn.Name;
import net.named_data.jndn.encoding.EncodingException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.PriorityQueue;

import static com.example.ndnpttv2.back_end.shared_state.PeerStateTable.NO_META_DATA_SEQ_NUM;

public class StreamConsumer {

    private static final String TAG = "StreamConsumer";

    // Public constants
    public static final int FETCH_COMPLETE_CODE_SUCCESS = 0;
    public static final int FETCH_COMPLETE_CODE_META_DATA_TIMEOUT = 1;
    public static final int FETCH_COMPLETE_CODE_MEDIA_DATA_TIMEOUT = 2;
    public static final int FETCH_COMPLETE_CODE_STREAM_RECORDED_TOO_FAR_IN_PAST = 3;

    // Private constants
    private static final int PROCESSING_INTERVAL_MS = 50;
    private static final int DEFAULT_META_DATA_FRAMES_PER_SEGMENT = 1;
    private static final int DEFAULT_META_DATA_SAMPLING_RATE = 8000;

    // Messages
    private static final int MSG_DO_SOME_WORK = 0;
    private static final int MSG_FETCH_START = 1;
    private static final int MSG_BUFFER_START = 2;
    private static final int MSG_CLOSE_SUCCESS = 3;
    private static final int MSG_CLOSE_META_DATA_TIMEOUT = 4;
    private static final int MSG_CLOSE_MEDIA_DATA_TIMEOUT = 5;
    private static final int MSG_CLOSE_STREAM_RECORDED_TOO_FAR_IN_PAST = 6;

    private Network network_;
    private StreamFetcher streamFetcher_;
    private StreamPlayerBuffer streamPlayerBuffer_;
    private boolean streamFetchStartCalled_ = false;
    private boolean streamPlayStartCalled_ = false;
    private ChannelUserSession channelUserSession_;
    private Name streamMetaDataName_;
    private Name streamName_;
    private long streamSeqNum_;
    private InputStreamDataSource audioOutputSource_;
    private Handler handler_;
    private boolean streamConsumerClosed_ = false;
    private Options options_;
    private StreamMetaData streamMetaData_;
    private PeerStateTable peerStateTable_;
    private long progressTrackerId_;

    // Events
    public Event<ProgressEventInfo> eventProductionWindowGrowth;
    public Event<ProgressEventInfo> eventAudioRetrieved;
    public Event<ProgressEventInfo> eventNackRetrieved;
    public Event<ProgressEventInfo> eventInterestSkipped;
    public Event<ProgressEventInfo> eventFinalBlockIdLearned;
    public Event<ProgressEventInfo> eventMetaDataFetched;
    public Event<ProgressEventInfo> eventFetchingCompleted;
    public Event<ProgressEventInfo> eventFrameBuffered;
    public Event<ProgressEventInfo> eventFrameSkipped;
    public Event<ProgressEventInfo> eventFinalFrameNumLearned;
    public Event<ProgressEventInfo> eventBufferingCompleted;

    public static class Options {
        public Options(long jitterBufferSize, long maxHistoricalStreamFetchTimeMs, long mediaDataTimeoutMs,
                       long metaDataTimeoutMs) {
            this.jitterBufferSize = jitterBufferSize;
            this.maxHistoricalStreamFetchTimeMs = maxHistoricalStreamFetchTimeMs;
            this.mediaDataTimeoutMs = mediaDataTimeoutMs;
            this.metaDataTimeoutMs = metaDataTimeoutMs;
        }
        long jitterBufferSize; // # of initial frames in StreamPlayerBuffer's jitter buffer before playback begins
        long maxHistoricalStreamFetchTimeMs; // maximum amount of ms in the past the stream was recorded to still fetch stream
        long mediaDataTimeoutMs; // maximum amount of time between successful data fetches to still fetch stream
        long metaDataTimeoutMs; // maximum amount of time to successfully fetch meta data to still fetch stream

        @Override
        public String toString() {
            return "jitterBufferSize " + jitterBufferSize + ", " +
                    "consumerMaxHistoricalStreamFetchTimeMs " + maxHistoricalStreamFetchTimeMs + ", " +
                    "mediaDataTimeoutMs " + mediaDataTimeoutMs;
        }
    }

    public StreamConsumer(long progressTrackerId, Name networkDataPrefix, SyncStreamInfo syncStreamInfo,
                          InputStreamDataSource audioOutputSource, NetworkThread.Info networkThreadInfo,
                          PeerStateTable peerStateTable, Options options) {

        channelUserSession_ = syncStreamInfo.channelUserSession;
        streamSeqNum_ = syncStreamInfo.seqNum;
        progressTrackerId_ = progressTrackerId;
        streamName_ = Helpers.getStreamName(networkDataPrefix, syncStreamInfo);
        streamMetaDataName_ = Helpers.getStreamMetaDataName(networkDataPrefix, syncStreamInfo);
        options_ = options;
        audioOutputSource_ = audioOutputSource;
        peerStateTable_ = peerStateTable;
        PeerStateTable.PeerState peerState = peerStateTable_.getPeerState(channelUserSession_);
        streamMetaData_ = (peerState.lastKnownMetaData != null ?
                            peerState.lastKnownMetaData :
                            new StreamMetaData(
                                    DEFAULT_META_DATA_FRAMES_PER_SEGMENT,
                                    DEFAULT_META_DATA_SAMPLING_RATE,
                                    System.currentTimeMillis()));
        eventProductionWindowGrowth = new SimpleEvent<>();
        eventAudioRetrieved = new SimpleEvent<>();
        eventNackRetrieved = new SimpleEvent<>();
        eventInterestSkipped = new SimpleEvent<>();
        eventFinalBlockIdLearned = new SimpleEvent<>();
        eventMetaDataFetched = new SimpleEvent<>();
        eventFetchingCompleted = new SimpleEvent<>();
        eventFrameBuffered = new SimpleEvent<>();
        eventFrameSkipped = new SimpleEvent<>();
        eventFinalFrameNumLearned = new SimpleEvent<>();
        eventBufferingCompleted = new SimpleEvent<>();

        handler_ = new Handler(networkThreadInfo.looper) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                super.handleMessage(msg);
                switch (msg.what) {
                    case MSG_DO_SOME_WORK: {
                        doSomeWork();
                        break;
                    }
                    case MSG_FETCH_START: {
                        if (streamFetchStartCalled_) return;
                        Log.d(TAG, "stream fetch started");
                        streamFetchStartCalled_ = true;
                        doSomeWork();
                        break;
                    }
                    case MSG_BUFFER_START: {
                        if (streamPlayStartCalled_) return;
                        streamPlayerBuffer_.setStreamPlayStartTime(System.currentTimeMillis());
                        streamPlayStartCalled_ = true;
                        break;
                    }
                    case MSG_CLOSE_SUCCESS: {
                        close(MSG_CLOSE_SUCCESS);
                        break;
                    }
                    case MSG_CLOSE_META_DATA_TIMEOUT: {
                        close(MSG_CLOSE_META_DATA_TIMEOUT);
                        break;
                    }
                    case MSG_CLOSE_MEDIA_DATA_TIMEOUT: {
                        close(MSG_CLOSE_MEDIA_DATA_TIMEOUT);
                        break;
                    }
                    case MSG_CLOSE_STREAM_RECORDED_TOO_FAR_IN_PAST: {
                        close (MSG_CLOSE_STREAM_RECORDED_TOO_FAR_IN_PAST);
                        break;
                    }
                    default: {
                        throw new IllegalStateException("unexpected msg.what " + msg.what);
                    }
                }
            }
        };

        network_ = new Network(networkThreadInfo.face);
        streamFetcher_ = new StreamFetcher(networkThreadInfo.looper, handler_);
        streamPlayerBuffer_ = new StreamPlayerBuffer(handler_);

        Log.d(TAG, streamName_.toString() + ": " + "Initialized (" +
                "stream meta data " + streamMetaData_.toString() + ", " +
                "options " + options_.toString() +
                ")");
    }

    public void streamFetchStart() {
        handler_.obtainMessage(MSG_FETCH_START).sendToTarget();
    }

    public void streamBufferStart() {
        handler_.obtainMessage(MSG_BUFFER_START).sendToTarget();
    }

    private void close(int close_msg_what) {
        Log.d(TAG, streamName_.toString() + ": " + "close called");
        streamFetcher_.close();
        streamPlayerBuffer_.close();
        handler_.removeCallbacksAndMessages(null);
        switch (close_msg_what) {
            case MSG_CLOSE_SUCCESS: {
                eventBufferingCompleted.trigger(new ProgressEventInfo(progressTrackerId_, streamName_,0, null));
                Logger.logEvent(new Logger.LogEventInfo(Logger.STREAMCONSUMER_BUFFERING_COMPLETE, System.currentTimeMillis(),
                        0, streamName_.toString(), null));
                break;
            }
            case MSG_CLOSE_META_DATA_TIMEOUT: {
                eventFetchingCompleted.trigger(
                        new ProgressEventInfo(
                                progressTrackerId_,
                                streamName_,
                                FETCH_COMPLETE_CODE_META_DATA_TIMEOUT,
                                null));
                Logger.logEvent(new Logger.LogEventInfo(Logger.STREAMCONSUMER_FETCHING_COMPLETE, System.currentTimeMillis(),
                        FETCH_COMPLETE_CODE_META_DATA_TIMEOUT, streamName_.toString(), null));
                break;
            }
            case MSG_CLOSE_MEDIA_DATA_TIMEOUT: {
                eventFetchingCompleted.trigger(
                        new ProgressEventInfo(
                                progressTrackerId_,
                                streamName_,
                                FETCH_COMPLETE_CODE_MEDIA_DATA_TIMEOUT,
                                null));
                Logger.logEvent(new Logger.LogEventInfo(Logger.STREAMCONSUMER_FETCHING_COMPLETE, System.currentTimeMillis(),
                        FETCH_COMPLETE_CODE_MEDIA_DATA_TIMEOUT, streamName_.toString(), null));
                break;
            }
            case MSG_CLOSE_STREAM_RECORDED_TOO_FAR_IN_PAST: {
                eventFetchingCompleted.trigger(
                        new ProgressEventInfo(
                                progressTrackerId_,
                                streamName_,
                                FETCH_COMPLETE_CODE_STREAM_RECORDED_TOO_FAR_IN_PAST,
                                null));
                Logger.logEvent(new Logger.LogEventInfo(Logger.STREAMCONSUMER_FETCHING_COMPLETE, System.currentTimeMillis(),
                        FETCH_COMPLETE_CODE_STREAM_RECORDED_TOO_FAR_IN_PAST, streamName_.toString(), null));
                break;
            }
            default: {
                throw new IllegalStateException("unexpected close_msg_what " + close_msg_what);
            }
        }
        streamConsumerClosed_ = true;
    }

    private void doSomeWork() {
        streamFetcher_.doSomeWork();
        streamPlayerBuffer_.doSomeWork();
        if (!streamConsumerClosed_) {
            scheduleNextWork(SystemClock.uptimeMillis());
        }
    }

    private void scheduleNextWork(long thisOperationStartTimeMs) {
        handler_.removeMessages(MSG_DO_SOME_WORK);
        handler_.sendEmptyMessageAtTime(MSG_DO_SOME_WORK, thisOperationStartTimeMs + PROCESSING_INTERVAL_MS);
    }

    private class Network {

        private final static String TAG = "StreamConsumer_Network";

        private Face face_;
        private HashSet<Name> recvDatas;
        private HashSet<Name> retransmits;

        private Network(Face face) {
            face_ = face;
            recvDatas = new HashSet<>();
            retransmits = new HashSet<>();
        }

        private void sendMetaDataInterest(Interest interest) {
            boolean retransmit = false;
            if (!retransmits.contains(interest.getName())) {
                retransmits.add(interest.getName());
            }
            else {
                retransmit = true;
            }
            Log.d(TAG, streamName_.toString() + ": " + "send meta data interest (" +
                    "retx " + retransmit + ", " +
                    "name " + interest.getName().toString() +
                    ")");
            try {
                face_.expressInterest(interest, (Interest callbackInterest, Data callbackData) -> {
                    long satisfiedTime = System.currentTimeMillis();

                    if (!recvDatas.contains(callbackData.getName())) {
                        recvDatas.add(callbackData.getName());
                    }
                    else {
                        return;
                    }

                    Log.d(TAG, streamName_.toString() + ": " + "meta data received (" +
                            "time " + satisfiedTime + ", " +
                            "retx " + retransmits.contains(interest.getName()) +
                            ")");

                    streamFetcher_.processMetaData(callbackData);
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
            Logger.logEvent(new Logger.LogEventInfo(Logger.STREAMCONSUMER_INTEREST_TRANSMIT, System.currentTimeMillis(), 0, interest.getName().toString(), null));
        }

        private void sendMediaDataInterest(Interest interest) {
            Long segNum = null;
            try {
                segNum = interest.getName().get(-1).toSegment();
            } catch (EncodingException e) {
                e.printStackTrace();
            }
            boolean retransmit = false;
            if (!retransmits.contains(interest.getName())) {
                retransmits.add(interest.getName());
            }
            else {
                retransmit = true;
            }
            Log.d(TAG, streamName_.toString() + ": " + "send media data interest (" +
                    "retx " + retransmit + ", " +
                    "seg num " + segNum + ", " +
                    "name " + interest.getName().toString() +
                    ")");
            try {
                face_.expressInterest(interest, (Interest callbackInterest, Data callbackData) -> {
                    long satisfiedTime = System.currentTimeMillis();

                    if (!recvDatas.contains(callbackData.getName())) {
                        recvDatas.add(callbackData.getName());
                    }
                    else {
                        return;
                    }

                    Long callbackSegNum = null;
                    try {
                        callbackSegNum = callbackData.getName().get(-1).toSegment();
                    } catch (EncodingException e) {
                        e.printStackTrace();
                    }

                    Log.d(TAG, streamName_.toString() + ": " + "media data received (" +
                            "seg num " + callbackSegNum + ", " +
                            "time " + satisfiedTime + ", " +
                            "retx " + retransmits.contains(interest.getName()) +
                            ")");

                    streamFetcher_.processMediaData(callbackData, satisfiedTime);
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
            Logger.logEvent(new Logger.LogEventInfo(Logger.STREAMCONSUMER_INTEREST_TRANSMIT, System.currentTimeMillis(), 0, interest.getName().toString(), null));
        }
    }

    private class StreamFetcher {

        private static final String TAG = "StreamConsumer_Fetcher";

        // Private constants
        private static final int FINAL_BLOCK_ID_UNKNOWN = -1;
        private static final int NO_SEGS_SENT = -1;
        private static final int DEFAULT_INTEREST_LIFETIME_MS = 4000;
        private static final int N_EXPECTED_SAMPLES = 1; // for rtt estimator

        // Packet events
        private static final int PACKET_EVENT_AUDIO_RETRIEVED = 0;
        private static final int PACKET_EVENT_INTEREST_TIMEOUT = 1;
        private static final int PACKET_EVENT_INTEREST_TRANSMIT = 2;
        private static final int PACKET_EVENT_PREMATURE_RTO = 3;
        private static final int PACKET_EVENT_INTEREST_SKIP = 4;
        private static final int PACKET_EVENT_NACK_RETRIEVED = 5;

        private PriorityQueue<Long> retransmissionQueue_;
        private HashMap<Long, Long> segSendTimes_;
        private HashMap<Long, Object> rtoTokens_;
        private CwndCalculator cwndCalculator_;
        private RttEstimator rttEstimator_;
        private boolean closed_ = false;
        private Handler internalHandler_;
        private StreamFetcherState state_;
        private long metaDataFetchDeadline_;
        private boolean metaDataFetched_ = false;
        private boolean fetchingFinished_ = false;
        private Object metaDataRtoToken_;
        private Object metaDataFetchDeadlineToken_;
        private Object successfulDataFetchTimerToken_;
        private Gson jsonSerializer_;
        private Handler streamConsumerHandler_;

        public class StreamFetcherState {
            long msPerSegNum_;
            long recordingStartTime;
            long finalBlockId = FINAL_BLOCK_ID_UNKNOWN;
            long highestSegAnticipated = NO_SEGS_SENT;
            int numInterestsTransmitted = 0;
            int numInterestTimeouts = 0;
            int numDataReceives = 0;
            int numPrematureRtos = 0;
            int numInterestSkips = 0;
            int numNacks = 0;

            public String toString() {
                return streamName_.toString() + ": " + "State of StreamFetcher:" + "\n" +
                        "recordingStartTime " + recordingStartTime + ", " +
                        "msPerSegNum_ " + msPerSegNum_ + ", " +
                        "finalBlockId " +
                        ((finalBlockId == FINAL_BLOCK_ID_UNKNOWN) ? "unknown" : finalBlockId) +
                        ", " +
                        "highestSegAnticipated " +
                        ((highestSegAnticipated == NO_SEGS_SENT) ? "none " : highestSegAnticipated) +
                        "\n" +
                        "numInterestsTransmitted " + numInterestsTransmitted + ", " +
                        "numInterestTimeouts " + numInterestTimeouts + ", " +
                        "numDataReceives " + numDataReceives + "\n" +
                        "numInterestSkips " + numInterestSkips + ", " +
                        "numPrematureRtos " + numPrematureRtos + ", " +
                        "numNacks " + numNacks + "\n" +
                        "retransmissionQueue_ " + retransmissionQueue_ + "\n" +
                        "segSendTimes_ " + segSendTimes_;
            }
        }

        private long getTimeSinceStreamRecordingStart() {
            return System.currentTimeMillis() - state_.recordingStartTime;
        }

        private StreamFetcher(Looper networkThreadLooper, Handler streamConsumerHandler) {
            streamConsumerHandler_ = streamConsumerHandler;
            jsonSerializer_ = new Gson();
            cwndCalculator_ = new CwndCalculator();
            retransmissionQueue_ = new PriorityQueue<>();
            segSendTimes_ = new HashMap<>();
            rtoTokens_ = new HashMap<>();
            long streamPlayerBufferJitterDelay = options_.jitterBufferSize * calculateMsPerFrame(streamMetaData_.producerSamplingRate);
            rttEstimator_ = new RttEstimator(new RttEstimator.Options(streamPlayerBufferJitterDelay, streamPlayerBufferJitterDelay));
            state_ = new StreamFetcherState();
            state_.msPerSegNum_ = calculateMsPerSeg(streamMetaData_.producerSamplingRate, streamMetaData_.framesPerSegment);
            state_.recordingStartTime = streamMetaData_.recordingStartTime;
            internalHandler_ = new Handler(networkThreadLooper);
            startMetaDataFetchDeadlineTimer();
            resetAndStartSuccessfulDataFetchTimer();
            Log.d(TAG, streamName_.toString() + ": " + "Initialized (" +
                    "maxRto / initialRto " + streamPlayerBufferJitterDelay + ", " +
                    "ms per seg num " + state_.msPerSegNum_ +
                    ")");
        }

        private void startMetaDataFetchDeadlineTimer() {
            metaDataFetchDeadlineToken_ = new Object();
            internalHandler_.post(new Runnable() {
                @Override
                public void run() {
                    metaDataFetchDeadline_ = System.currentTimeMillis() + options_.metaDataTimeoutMs;
                    transmitMetaDataInterest(false);
                    internalHandler_.postAtTime(
                            () -> {
                                Log.d(TAG, "closing stream consumer (meta data fetch failed)");
                                streamConsumerHandler_.obtainMessage(MSG_CLOSE_META_DATA_TIMEOUT).sendToTarget();
                            },
                            metaDataFetchDeadlineToken_,
                            SystemClock.uptimeMillis() + options_.metaDataTimeoutMs
                    );
                }
            });
        }

        private void resetAndStartSuccessfulDataFetchTimer() {
            Log.d(TAG, System.currentTimeMillis() + ": " + "resetting and starting successful data fetch timer");
            if (successfulDataFetchTimerToken_ != null) {
                Log.d(TAG, "found that successfulDataFetchTimerRunnable_ wasn't null, remove last data fetch timer from handler");
                internalHandler_.removeCallbacksAndMessages(successfulDataFetchTimerToken_);
            }
            successfulDataFetchTimerToken_ = new Object();
            internalHandler_.post(new Runnable() {
                @Override
                public void run() {
                    internalHandler_.postAtTime(
                            () -> {
                                if (!fetchingFinished_) {
                                    Log.d(TAG, System.currentTimeMillis() + ": " +
                                            "closing stream consumer (successful data fetch time limit reached)");
                                    streamConsumerHandler_.obtainMessage(MSG_CLOSE_MEDIA_DATA_TIMEOUT).sendToTarget();
                                }
                            },
                            successfulDataFetchTimerToken_,
                            SystemClock.uptimeMillis() + options_.mediaDataTimeoutMs
                    );
                }
            });
        }

        private void close() {
            if (closed_) return;
            Log.d(TAG, streamName_.toString() + ": " + "close called, state of fetcher: " + state_.toString());
            internalHandler_.removeCallbacksAndMessages(null);
            if (successfulDataFetchTimerToken_ != null) {
                Log.d(TAG, "found in close that there was an outstanding successful data fetch timer, removing it");
                internalHandler_.removeCallbacksAndMessages(successfulDataFetchTimerToken_);
            }
            eventFetchingCompleted.trigger(new ProgressEventInfo(progressTrackerId_, streamName_,
                    FETCH_COMPLETE_CODE_SUCCESS, null));
            Logger.logEvent(new Logger.LogEventInfo(Logger.STREAMCONSUMER_FETCHING_COMPLETE, System.currentTimeMillis(),
                    FETCH_COMPLETE_CODE_SUCCESS, streamName_.toString(), null));
            fetchingFinished_ = true;
            closed_ = true;
        }

        private void doSomeWork() {

            if (closed_) return;

            while (retransmissionQueue_.size() != 0 && withinCwnd()) {
                if (closed_) return;
                Long segNum = retransmissionQueue_.poll();
                if (segNum == null) continue;
                transmitMediaDataInterest(segNum, true);
            }

            if (closed_) return;

            if (state_.finalBlockId == FINAL_BLOCK_ID_UNKNOWN ||
                    state_.highestSegAnticipated < state_.finalBlockId) {
                while (nextSegShouldBeSent() && withinCwnd()) {
                    if (closed_) return;
                    state_.highestSegAnticipated++;
                    eventProductionWindowGrowth.trigger(new ProgressEventInfo(progressTrackerId_, streamName_,
                            state_.highestSegAnticipated, null));
                    transmitMediaDataInterest(state_.highestSegAnticipated, false);
                }
            }

            if (retransmissionQueue_.size() == 0 && rtoTokens_.size() == 0 &&
                    state_.finalBlockId != FINAL_BLOCK_ID_UNKNOWN &&
                    metaDataFetched_) {
                close();
            }

        }

        private boolean nextSegShouldBeSent() {
            long timeSinceStreamRecordingStart = getTimeSinceStreamRecordingStart();
            boolean nextSegShouldBeSent = false;
            if (timeSinceStreamRecordingStart / state_.msPerSegNum_ > state_.highestSegAnticipated) {
                nextSegShouldBeSent = true;
            }
            return nextSegShouldBeSent;
        }

        private void transmitMetaDataInterest(boolean isRetransmission) {

            Interest interestToSend = new Interest(streamMetaDataName_);
            long rto = (long) rttEstimator_.getEstimatedRto();
            long transmitTime = System.currentTimeMillis();
            long lifetime =  metaDataFetchDeadline_ - transmitTime;

            interestToSend.setInterestLifetimeMilliseconds(lifetime);
            interestToSend.setCanBePrefix(false);
            interestToSend.setMustBeFresh(false);

            metaDataRtoToken_ = new Object();
            internalHandler_.postAtTime(() -> {
                    Log.d(TAG, streamName_.toString() + ": " + getTimeSinceStreamRecordingStart() + ": " + "rto timeout (meta data)");
                        Logger.logEvent(new Logger.LogEventInfo(Logger.STREAMCONSUMER_INTEREST_RTO, System.currentTimeMillis(), 0, streamName_.toString(), null));
                    transmitMetaDataInterest(true);
                    },
                    metaDataRtoToken_,
                    SystemClock.uptimeMillis() + rto);

            network_.sendMetaDataInterest(interestToSend);
            Log.d(TAG, streamName_.toString() + ": " + "meta data interest transmitted (" +
                    "rto " + rto + ", " +
                    "lifetime " + lifetime + ", " +
                    "retx: " + isRetransmission +
                    ")");

        }

        private void transmitMediaDataInterest(final long segNum, boolean isRetransmission) {

            Interest interestToSend = new Interest(streamName_);
            interestToSend.getName().appendSegment(segNum);
            long avgRtt = (long) rttEstimator_.getAvgRtt();
            long rto = (long) rttEstimator_.getEstimatedRto();
            // if playback deadline for first frame of segment is known, set interest lifetime to expire at playback deadline
            long segFirstFrameNum = streamMetaData_.framesPerSegment * segNum;
            long playbackDeadline = streamPlayerBuffer_.getPlaybackDeadline(segFirstFrameNum);
            long transmitTime = System.currentTimeMillis();
            if (playbackDeadline != StreamPlayerBuffer.PLAYBACK_DEADLINE_UNKNOWN && transmitTime + avgRtt > playbackDeadline) {
                Log.d(TAG, streamName_.toString() + ": " + "interest skipped (" +
                        "seg num " + segNum + ", " +
                        "first frame num " + segFirstFrameNum + ", " +
                        "avgRtt " + avgRtt + ", " +
                        "transmit time " + transmitTime + ", " +
                        "playback deadline " + playbackDeadline + ", " +
                        "retx: " + isRetransmission +
                        ")");
                Logger.logEvent(new Logger.LogEventInfo(Logger.STREAMCONSUMER_INTEREST_SKIP, System.currentTimeMillis(), 0, interestToSend.getName().toString(), null));
                recordPacketEvent(segNum, PACKET_EVENT_INTEREST_SKIP);
                eventInterestSkipped.trigger(new ProgressEventInfo(progressTrackerId_, streamName_, segNum, null));
                return;
            }

            long lifetime = (playbackDeadline == StreamPlayerBuffer.PLAYBACK_DEADLINE_UNKNOWN)
                    ? DEFAULT_INTEREST_LIFETIME_MS : playbackDeadline - transmitTime;

            interestToSend.setInterestLifetimeMilliseconds(lifetime);
            interestToSend.setCanBePrefix(false);
            interestToSend.setMustBeFresh(false);

            Object rtoToken = new Object();
            internalHandler_.postAtTime(() -> {
                Log.d(TAG, streamName_.toString() + ": " + getTimeSinceStreamRecordingStart() + ": " + "rto timeout (seg num " + segNum + ")");
                Logger.logEvent(new Logger.LogEventInfo(Logger.STREAMCONSUMER_INTEREST_RTO, System.currentTimeMillis(), 0, streamName_.toString(), null));
                retransmissionQueue_.add(segNum);
                rtoTokens_.remove(segNum);
                recordPacketEvent(segNum, PACKET_EVENT_INTEREST_TIMEOUT);
            }, rtoToken, SystemClock.uptimeMillis() + rto);
            rtoTokens_.put(segNum, rtoToken);

            if (isRetransmission) {
                segSendTimes_.remove(segNum);
            } else {
                segSendTimes_.put(segNum, System.currentTimeMillis());
            }
            network_.sendMediaDataInterest(interestToSend);
            recordPacketEvent(segNum, PACKET_EVENT_INTEREST_TRANSMIT);
            Log.d(TAG, streamName_.toString() + ": " + "interest transmitted (" +
                    "seg num " + segNum + ", " +
                    "first frame num " + segFirstFrameNum + ", " +
                    "rto " + rto + ", " +
                    "lifetime " + lifetime + ", " +
                    "retx: " + isRetransmission + ", " +
                    "numPrematureRtos " + state_.numPrematureRtos +
                    ")");
        }

        private void processMetaData(Data metaDataPacket) {
            if (closed_) return;
            resetAndStartSuccessfulDataFetchTimer();
            Log.d(TAG, "got meta data " + metaDataPacket.getName().toString() + ", " +
                    "content " + metaDataPacket.getContent().toString());
            internalHandler_.removeCallbacksAndMessages(metaDataFetchDeadlineToken_);
            internalHandler_.removeCallbacksAndMessages(metaDataRtoToken_);
            if (metaDataPacket.getMetaInfo().getType() == ContentType.NACK) {
                Log.d(TAG, "got an application nack as response to meta data interest");
            }
            StreamMetaData streamMetaData;
            try {
                streamMetaData = jsonSerializer_.fromJson(
                        metaDataPacket.getContent().toString(), StreamMetaData.class);
            }
            catch (Exception e) {
                throw new IllegalStateException("meta data parsing failed for " + metaDataPacket.getName().toString());
            }

            streamMetaData_ = streamMetaData;
            metaDataFetched_ = true;

            PeerStateTable.PeerState peerState = peerStateTable_.getPeerState(channelUserSession_);
            if (peerState.lastKnownMetaDataSeqNum == NO_META_DATA_SEQ_NUM || peerState.lastKnownMetaDataSeqNum < streamSeqNum_) {
                peerState.lastKnownMetaData = streamMetaData_;
                peerState.lastKnownMetaDataSeqNum = streamSeqNum_;
            }

            if (System.currentTimeMillis() > streamMetaData_.recordingStartTime + options_.maxHistoricalStreamFetchTimeMs) {
                Log.d(TAG, streamName_.toString() + ": " + "stream recorded too far in the past, cancelling stream fetch");
                streamConsumerHandler_.obtainMessage(MSG_CLOSE_STREAM_RECORDED_TOO_FAR_IN_PAST).sendToTarget();
            }

            // recalibrate production window growth rate
            state_.msPerSegNum_ = calculateMsPerSeg(streamMetaData_.producerSamplingRate, streamMetaData_.framesPerSegment);
            state_.recordingStartTime = streamMetaData_.recordingStartTime;

            eventMetaDataFetched.trigger(new ProgressEventInfo(progressTrackerId_, streamName_, 0, streamMetaData_));

            Log.d(TAG, "recalibrated ms per seg and recording start time: " + "\n" +
                    "msPerSegNum " + state_.msPerSegNum_);

        }

        private void processMediaData(Data audioPacket, long receiveTime) {
            if (closed_) return;
            resetAndStartSuccessfulDataFetchTimer();
            long segNum;
            try {
                segNum = audioPacket.getName().get(-1).toSegment();
            } catch (EncodingException e) {
                e.printStackTrace();
                return;
            }

            boolean detectedPrematureRto = retransmissionQueue_.contains(segNum);
            if (detectedPrematureRto) {
                recordPacketEvent(segNum, PACKET_EVENT_PREMATURE_RTO);
            }

            if (segSendTimes_.containsKey(segNum)) {
                long rtt = receiveTime - segSendTimes_.get(segNum);
                Log.d(TAG, streamName_.toString() + ": " + "rtt estimator add measure (rtt " + rtt + ", " +
                        "num outstanding interests " + N_EXPECTED_SAMPLES +
                        ")");
                rttEstimator_.addMeasurement(rtt, N_EXPECTED_SAMPLES);
                Log.d(TAG, streamName_.toString() + ": " + "rto after last measure add: " +
                        rttEstimator_.getEstimatedRto());
                segSendTimes_.remove(segNum);
            }

            long finalBlockId = FINAL_BLOCK_ID_UNKNOWN;
            boolean audioPacketWasAppNack = audioPacket.getMetaInfo().getType() == ContentType.NACK;
            if (audioPacketWasAppNack) {
                finalBlockId = Helpers.bytesToLong(audioPacket.getContent().getImmutableArray());
                Log.d(TAG, streamName_.toString() + ": " + "audioPacketWasAppNack, final block id " + finalBlockId);
                state_.finalBlockId = finalBlockId;
                streamPlayerBuffer_.receiveFinalSegNum(finalBlockId);
            }
            else {
                streamPlayerBuffer_.processAdtsFrames(audioPacket.getContent().getImmutableArray(), segNum);
                Name.Component finalBlockIdComponent = audioPacket.getMetaInfo().getFinalBlockId();
                if (finalBlockIdComponent != null) {
                    try {
                        finalBlockId = finalBlockIdComponent.toSegment();
                        state_.finalBlockId = finalBlockId;
                    }
                    catch (EncodingException ignored) { }
                }
            }
            if (state_.finalBlockId != FINAL_BLOCK_ID_UNKNOWN) {
                eventFinalBlockIdLearned.trigger(new ProgressEventInfo(progressTrackerId_, streamName_,
                        state_.finalBlockId, null));
            }

            Log.d(TAG, streamName_.toString() + ": " + "receive data (" +
                    "name " + audioPacket.getName().toString() + ", " +
                    "seg num " + segNum + ", " +
                    "app nack " + audioPacketWasAppNack + ", " +
                    "premature rto " + detectedPrematureRto +
                    ((finalBlockId == FINAL_BLOCK_ID_UNKNOWN) ? "" : ", final block id " + finalBlockId) +
                    ")");

            if (audioPacketWasAppNack) {
                Logger.logEvent(new Logger.LogEventInfo(Logger.STREAMCONSUMER_NACK_RECEIVE, System.currentTimeMillis(), 0, audioPacket.getName().toString(), null));
                recordPacketEvent(segNum, PACKET_EVENT_NACK_RETRIEVED);
                eventNackRetrieved.trigger(new ProgressEventInfo(progressTrackerId_, streamName_, segNum, null));
            }
            else {
                Logger.logEvent(new Logger.LogEventInfo(Logger.STREAMCONSUMER_AUDIO_DATA_RECEIVE, System.currentTimeMillis(), 0, audioPacket.getName().toString(), null));
                recordPacketEvent(segNum, PACKET_EVENT_AUDIO_RETRIEVED);
                eventAudioRetrieved.trigger(new ProgressEventInfo(progressTrackerId_, streamName_, segNum, null));
            }
            retransmissionQueue_.remove(segNum);
            internalHandler_.removeCallbacksAndMessages(rtoTokens_.get(segNum));
            rtoTokens_.remove(segNum);
        }

        private class CwndCalculator {

            private static final long MAX_CWND = 50; // max # of outstanding interests

            long currentCwnd_;

            private CwndCalculator() {
                currentCwnd_ = MAX_CWND;
            }

            private long getCurrentCwnd() {
                return currentCwnd_;
            }

        }

        private boolean withinCwnd() {
            return rtoTokens_.size() < cwndCalculator_.getCurrentCwnd();
        }

        private void recordPacketEvent(long segNum, int event_code) {
            String eventString = "";
            switch (event_code) {
                case PACKET_EVENT_AUDIO_RETRIEVED:
                    state_.numDataReceives++;
                    eventString = "data_receive";
                    break;
                case PACKET_EVENT_INTEREST_TIMEOUT:
                    state_.numInterestTimeouts++;
                    eventString = "interest_timeout";
                    break;
                case PACKET_EVENT_INTEREST_TRANSMIT:
                    state_.numInterestsTransmitted++;
                    eventString = "interest_transmit";
                    break;
                case PACKET_EVENT_PREMATURE_RTO:
                    state_.numPrematureRtos++;
                    eventString = "premature_rto";
                    break;
                case PACKET_EVENT_INTEREST_SKIP:
                    state_.numInterestSkips++;
                    eventString = "interest_skip";
                    break;
                case PACKET_EVENT_NACK_RETRIEVED:
                    state_.numNacks++;
                    eventString = "nack_receive";
                    break;
            }

            Log.d(TAG, streamName_.toString() + ": " + "recorded packet event (" +
                    "seg num " + segNum + ", " +
                    "event " + eventString + ", " +
                    "num outstanding interests " + rtoTokens_.size() +
                    ")");
        }
    }

    private class StreamPlayerBuffer {

        private final static String TAG = "StreamConsumer_PlayerBuffer";

        // Private constants
        private static final int FINAL_FRAME_NUM_UNKNOWN = -1;
        private static final int STREAM_PLAY_START_TIME_UNKNOWN = -1;
        private static final int FINAL_SEG_NUM_UNKNOWN = -1;
        private static final int PLAYBACK_DEADLINE_UNKNOWN = -1;

        private class Frame implements Comparable<Frame> {
            long frameNum;
            byte[] data;

            private Frame(long frameNum, byte[] data) {
                this.frameNum = frameNum;
                this.data = data;
            }

            @Override
            public int compareTo(Frame frame) {
                return Long.compare(frameNum, frame.frameNum);
            }

            @Override
            public String toString() {
                return Long.toString(frameNum);
            }
        }

        private PriorityQueue<Frame> jitterBuffer_;
        private long jitterBufferDelay_;
        private boolean closed_ = false;
        private Handler streamConsumerHandler_;
        private long highestFrameNumPlayed_ = 0;
        private long highestFrameNumPlayedDeadline_;
        private long finalSegNum_ = FINAL_SEG_NUM_UNKNOWN;
        private long finalFrameNum_ = FINAL_FRAME_NUM_UNKNOWN;
        private long finalFrameNumDeadline_ = PLAYBACK_DEADLINE_UNKNOWN;
        private long streamPlayStartTime_ = STREAM_PLAY_START_TIME_UNKNOWN;
        private long msPerFrame_;
        private boolean firstRealDoSomeWork_ = true;
        private long framesSkipped_ = 0;
        private long framesPlayed_ = 0;

        private void printState() {
            Log.d(TAG, streamName_.toString() + ": " + "State of StreamPlayerBuffer:" + "\n" +
                    "streamPlayStartTime_ " + streamPlayStartTime_ + ", " +
                    "framesPlayed_ " + framesPlayed_ + ", " +
                    "framesSkipped_ " + framesSkipped_ + "\n" +
                    "finalSegNum_ " +
                    ((finalSegNum_ == FINAL_SEG_NUM_UNKNOWN) ?
                            "unknown" : finalSegNum_) + ", " +
                    "finalFrameNum_ " +
                    ((finalFrameNum_ == FINAL_FRAME_NUM_UNKNOWN) ?
                            "unknown" : finalFrameNum_) + ", " +
                    "finalFrameNumDeadline_ " +
                    ((finalFrameNum_ == PLAYBACK_DEADLINE_UNKNOWN) ?
                            "unknown" : finalFrameNumDeadline_) + "\n" +
                    "jitterBuffer_ " + jitterBuffer_);
        }

        private StreamPlayerBuffer(Handler streamConsumerHandler) {
            jitterBuffer_ = new PriorityQueue<>();
            streamConsumerHandler_ = streamConsumerHandler;
            jitterBufferDelay_ = options_.jitterBufferSize * calculateMsPerFrame(streamMetaData_.producerSamplingRate);
            msPerFrame_ = calculateMsPerFrame(streamMetaData_.producerSamplingRate);
            Log.d(TAG, streamName_.toString() + ": " + "Initialized (" +
                    "jitterBufferDelay_ " + jitterBufferDelay_ + ", " +
                    "ms per frame " + msPerFrame_ +
                    ")");
        }

        private void setStreamPlayStartTime(long streamPlayStartTime) {
            streamPlayStartTime_ = streamPlayStartTime;
        }

        private void close() {
            if (closed_) return;
            closed_ = true;
        }

        private void doSomeWork() {
            if (streamPlayStartTime_ == STREAM_PLAY_START_TIME_UNKNOWN) {
                return;
            }
            if (!streamFetcher_.metaDataFetched_) {
                return; // do not start playback until meta data for stream fetched successfully
            }

            if (firstRealDoSomeWork_) {

                Log.d(TAG, "buffering started");
                Logger.logEvent(new Logger.LogEventInfo(Logger.STREAMCONSUMER_BUFFERING_START, System.currentTimeMillis(),
                        0, streamName_.toString(), null));

                highestFrameNumPlayedDeadline_ = streamPlayStartTime_ + jitterBufferDelay_;

                // try to calculate the final frame number's playback deadline, in the case that
                // the stream has already been fully fetched before playback begins
                if (finalFrameNum_ != FINAL_FRAME_NUM_UNKNOWN) {
                    finalFrameNumDeadline_ = getPlaybackDeadline(finalFrameNum_);
                }
                else if (finalSegNum_ != FINAL_SEG_NUM_UNKNOWN) {
                    // calculate the finalFrameNumDeadline_ based on the assumption that the last segment
                    // had framesPerSegment_ frames in it
                    finalFrameNumDeadline_ = getPlaybackDeadline(
                            (finalSegNum_ * streamMetaData_.framesPerSegment) +
                                    streamMetaData_.framesPerSegment);
                }

                firstRealDoSomeWork_ = false;
            }

            if (closed_) return;

            long currentTime = System.currentTimeMillis();
            if (currentTime > highestFrameNumPlayedDeadline_) {
                Log.d(TAG, streamName_.toString() + ": " + "reached playback deadline for frame " + highestFrameNumPlayed_ + " (" +
                        "playback deadline " + highestFrameNumPlayedDeadline_ +
                        ")"
                );

                Frame nextFrame = jitterBuffer_.peek();
                boolean gotExpectedFrame = nextFrame != null && nextFrame.frameNum == highestFrameNumPlayed_;
                Log.d(TAG, streamName_.toString() + ": " + "next frame was " + ((gotExpectedFrame) ? "" : "not ") + "expected frame (" +
                        "expected frame num " + highestFrameNumPlayed_ + ", " +
                        "frame num " + ((nextFrame != null) ? nextFrame.frameNum : "unknown (null frame)") +
                        ")");
                if (gotExpectedFrame) {
                    framesPlayed_++;
                    audioOutputSource_.write(nextFrame.data,
                            (finalFrameNumDeadline_ != PLAYBACK_DEADLINE_UNKNOWN &&
                                    currentTime > finalFrameNumDeadline_));
                    jitterBuffer_.poll();
                    eventFrameBuffered.trigger(new ProgressEventInfo(progressTrackerId_, streamName_, highestFrameNumPlayed_, null));
                }
                else {
                    if (nextFrame == null || nextFrame.frameNum > highestFrameNumPlayed_) {
                        framesSkipped_++;
                        audioOutputSource_.write(getSilentFrame(),
                                (finalFrameNumDeadline_ != PLAYBACK_DEADLINE_UNKNOWN &&
                                        currentTime > finalFrameNumDeadline_));
                        eventFrameSkipped.trigger(new ProgressEventInfo(progressTrackerId_, streamName_, highestFrameNumPlayed_, null));
                    }
                }

                highestFrameNumPlayed_++;
                highestFrameNumPlayedDeadline_ += msPerFrame_;
            }

            if (finalFrameNumDeadline_ == PLAYBACK_DEADLINE_UNKNOWN) {
                Log.d(TAG, streamName_.toString() + ": " + "final frame num deadline unknown");
                return;
            }

            if (currentTime > finalFrameNumDeadline_) {
                Log.d(TAG, streamName_.toString() + ": " + "finished playing all frames (" +
                        "final seg num " +
                        ((finalSegNum_ == FINAL_SEG_NUM_UNKNOWN) ?
                                "unknown" : finalSegNum_) + ", " +
                        "final frame num " +
                        ((finalFrameNum_ == FINAL_FRAME_NUM_UNKNOWN) ?
                                "unknown" : finalFrameNum_) + ", " +
                        "playback deadline " + finalFrameNumDeadline_ +
                        ")");
                audioOutputSource_.write(getSilentFrame(), true);
                printState();
                // close the entire stream consumer, now that playback is done
                Log.d(TAG, "closing stream consumer");
                streamConsumerHandler_.obtainMessage(MSG_CLOSE_SUCCESS).sendToTarget();
            }
        }

        private void processAdtsFrames(byte[] frames, long segNum) {
            Log.d(TAG, streamName_.toString() + ": " + "processing adts frames (" +
                    "length " + frames.length + ", " +
                    "seg num " + segNum +
                    ")");
            ArrayList<byte[]> parsedFrames = parseAdtsFrames(frames);
            int parsedFramesLength = parsedFrames.size();
            for (int i = 0; i < parsedFramesLength; i++) {
                byte[] frameData = parsedFrames.get(i);
                long frameNum = (segNum * streamMetaData_.framesPerSegment) + i;
                Log.d(TAG, streamName_.toString() + ": " + "got frame " + frameNum);
                jitterBuffer_.add(new Frame(frameNum, frameData));
            }
            // to detect end of stream, assume that every batch of frames besides the batch of
            // frames associated with the final segment of a stream will have exactly framesPerSegment_
            // frames in it
            if (parsedFrames.size() < streamMetaData_.framesPerSegment) {
                finalFrameNum_ = (segNum * streamMetaData_.framesPerSegment) + parsedFrames.size() - 1;
                eventFinalFrameNumLearned.trigger(new ProgressEventInfo(progressTrackerId_, streamName_, finalFrameNum_, null));
                Log.d(TAG, streamName_.toString() + ": " + "detected end of stream (" +
                        "final seg num " + segNum + ", " +
                        "final frame num " + finalFrameNum_ +
                        ")");
                finalFrameNumDeadline_ = getPlaybackDeadline(finalFrameNum_);
            }
        }

        private void receiveFinalSegNum(long finalSegNum) {
            if (finalFrameNum_ != FINAL_FRAME_NUM_UNKNOWN) return;
            if (finalSegNum_ != FINAL_SEG_NUM_UNKNOWN) return;

            Log.d(TAG, streamName_.toString() + ": " + "receiveFinalSegNum: " + finalSegNum);

            finalSegNum_ = finalSegNum;
            // calculate the finalFrameNumDeadline_ based on the assumption that the last segment
            // had framesPerSegment_ frames in it
            finalFrameNumDeadline_ = getPlaybackDeadline(
                    (finalSegNum * streamMetaData_.framesPerSegment) +
                            streamMetaData_.framesPerSegment);
        }

        private ArrayList<byte[]> parseAdtsFrames(byte[] frames) {
            ArrayList<byte[]> parsedFrames = new ArrayList<>();
            for (int i = 0; i < frames.length;) {
                int frameLength = (frames[i+3]&0x03) << 11 |
                        (frames[i+4]&0xFF) << 3 |
                        (frames[i+5]&0xFF) >> 5 ;
                byte[] frame = Arrays.copyOfRange(frames, i, i + frameLength);
                parsedFrames.add(frame);
                i+= frameLength;
            }
            return parsedFrames;
        }

        private long getPlaybackDeadline(long frameNum) {
            if (streamPlayStartTime_ == STREAM_PLAY_START_TIME_UNKNOWN) {
                return PLAYBACK_DEADLINE_UNKNOWN;
            }
            long framePlayTimeOffset =
                    (Constants.SAMPLES_PER_ADTS_FRAME * Constants.MILLISECONDS_PER_SECOND * frameNum) /
                            streamMetaData_.producerSamplingRate;
            long deadline = streamPlayStartTime_ + jitterBufferDelay_ + framePlayTimeOffset;
            Log.d(TAG, streamName_.toString() + ": " + "calculated deadline (" +
                    "frame num " + frameNum + ", " +
                    "framePlayTimeOffset " + framePlayTimeOffset + ", " +
                    "jitterBufferDelay " + jitterBufferDelay_ + ", " +
                    "deadline " + deadline +
                    ")");
            return deadline;
        }

        private byte[] getSilentFrame() {
            return new byte[] {
                    (byte)0xff, (byte)0xf1, (byte)0x6c, (byte)0x40, (byte)0x0b, (byte)0x7f, (byte)0xfc, // 7 byte header
                    (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, // empty payload, silence
                    (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                    (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                    (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                    (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                    (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                    (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                    (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                    (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                    (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                    (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                    (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
            };
        }

    }

    /**
     * @param producerSamplingRate Audio sampling rate of producer (samples per second).
     */
    private long calculateMsPerFrame(long producerSamplingRate) {
        return (Constants.SAMPLES_PER_ADTS_FRAME *
                Constants.MILLISECONDS_PER_SECOND) / producerSamplingRate;
    }

    /**
     * @param producerSamplingRate Audio sampling rate of producer (samples per second).
     * @param framesPerSegment ADTS frames per segment.
     */
    private long calculateMsPerSeg(long producerSamplingRate, long framesPerSegment) {
        return (framesPerSegment * Constants.SAMPLES_PER_ADTS_FRAME *
                Constants.MILLISECONDS_PER_SECOND) / producerSamplingRate;
    }

}