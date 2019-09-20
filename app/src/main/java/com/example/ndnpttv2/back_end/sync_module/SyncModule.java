package com.example.ndnpttv2.back_end.sync_module;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.ndnpttv2.back_end.structs.ChannelUserSession;
import com.example.ndnpttv2.back_end.shared_state.PeerStateTable;
import com.example.ndnpttv2.back_end.structs.SyncStreamInfo;
import com.pploder.events.Event;
import com.pploder.events.SimpleEvent;

import net.named_data.jndn.Face;
import net.named_data.jndn.Name;
import net.named_data.jndn.OnRegisterFailed;
import net.named_data.jndn.encoding.EncodingException;
import net.named_data.jndn.security.KeyChain;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.security.identity.IdentityManager;
import net.named_data.jndn.security.identity.MemoryIdentityStorage;
import net.named_data.jndn.security.identity.MemoryPrivateKeyStorage;
import net.named_data.jndn.security.policy.SelfVerifyPolicyManager;
import net.named_data.jndn.sync.ChronoSync2013;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.LinkedTransferQueue;

import static com.example.ndnpttv2.back_end.shared_state.PeerStateTable.NO_SEQ_NUMS;

public class SyncModule {

    private static final String TAG = "SyncModule";

    // Private constants
    private static final int DEFAULT_SYNC_INTEREST_LIFETIME_MS = 2000;
    private static final int PROCESSING_INTERVAL_MS = 50;

    // Messages
    private static final int MSG_DO_SOME_WORK = 0;
    private static final int MSG_INITIALIZE_SYNC = 1;
    private static final int MSG_NEW_STREAM_PRODUCING = 2;

    // Events
    public Event<Object> eventInitialized;
    public Event<SyncStreamInfo> eventNewStreamAvailable;

    private Network network_;
    private Name applicationBroadcastPrefix_;
    private Name applicationDataPrefix_;
    private long sessionId_;
    private Handler handler_;

    public SyncModule(Name applicationBroadcastPrefix, Name applicationDataPrefix,
                      long sessionId, Looper networkThreadLooper,
                      PeerStateTable peerStateTable) {

        applicationBroadcastPrefix_ = applicationBroadcastPrefix;
        applicationDataPrefix_ = applicationDataPrefix;
        Log.d(TAG, "SyncModule initialized (" +
                "applicationBroadcastPrefix " + applicationBroadcastPrefix + ", " +
                "applicationDataPrefix " + applicationDataPrefix +
                ")");

        sessionId_ = sessionId;

        eventInitialized = new SimpleEvent<>();
        eventNewStreamAvailable = new SimpleEvent<>();

        handler_ = new Handler(networkThreadLooper) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                super.handleMessage(msg);
                switch (msg.what) {
                    case MSG_DO_SOME_WORK: {
                        doSomeWork();
                        break;
                    }
                    case MSG_INITIALIZE_SYNC: {
                        network_.initializeSync();
                        handler_.obtainMessage(MSG_DO_SOME_WORK).sendToTarget();
                        break;
                    }
                    case MSG_NEW_STREAM_PRODUCING: {
                        Long seqNum = (Long) msg.obj;
                        Log.d(TAG, "new stream being produced, seq num " + seqNum);
                        network_.newStreamProductionNotifications_.add(seqNum);
                        break;
                    }
                    default:
                        throw new IllegalStateException("unexpected msg.what: " + msg.what);
                }
            }
        };

        network_ = new Network(peerStateTable);

        handler_.obtainMessage(MSG_INITIALIZE_SYNC).sendToTarget();
    }

    public void close() {
        network_.close();
        handler_.removeCallbacksAndMessages(null);
    }

    private void doSomeWork() {
        network_.doSomeWork();
        scheduleNextWork(SystemClock.uptimeMillis());
    }

    private void scheduleNextWork(long thisOperationStartTimeMs) {
        handler_.removeMessages(MSG_DO_SOME_WORK);
        handler_.sendEmptyMessageAtTime(MSG_DO_SOME_WORK, thisOperationStartTimeMs + PROCESSING_INTERVAL_MS);
    }

    public void notifyNewStreamProducing(long seqNum) {
        handler_
                .obtainMessage(MSG_NEW_STREAM_PRODUCING, Long.valueOf(seqNum))
                .sendToTarget();
    }

    private class Network {

        private final static String TAG = "SyncModule_Network";

        private LinkedTransferQueue<Long> newStreamProductionNotifications_;
        private Face face_;
        private KeyChain keyChain_;
        private boolean closed_ = false;
        private ChronoSync2013 sync_;
        private HashMap<ChannelUserSession, HashSet<Long>> recvdSeqNums_;
        private PeerStateTable peerStateTable_;

        private Network(PeerStateTable peerStateTable) {

            peerStateTable_ = peerStateTable;
            newStreamProductionNotifications_ = new LinkedTransferQueue<>();
            recvdSeqNums_ = new HashMap<>();

            // set up keychain
            keyChain_ = configureKeyChain();

            // set up face / sync
            face_ = new Face();
            try {
                face_.setCommandSigningInfo(keyChain_, keyChain_.getDefaultCertificateName());
            } catch (SecurityException e) {
                e.printStackTrace();
            }

        }

        private void initializeSync() {
            try {
                sync_ = new ChronoSync2013(onReceivedSyncState, onInitialized, applicationDataPrefix_, applicationBroadcastPrefix_,
                        sessionId_, face_, keyChain_, keyChain_.getDefaultCertificateName(), DEFAULT_SYNC_INTEREST_LIFETIME_MS,
                        onRegisterFailed);
            } catch (SecurityException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        private void close() {
            if (closed_) return;
            closed_ = true;
        }

        private void doSomeWork() {

            if (closed_) return;

            while (newStreamProductionNotifications_.size() != 0) {
                Long seqNum = newStreamProductionNotifications_.poll();
                if (seqNum == null) continue;
                try {
                    if (seqNum != sync_.getSequenceNo() + 1) {
                        throw new IllegalStateException("got unexpected stream seq num (expected " +
                                (sync_.getSequenceNo() + 1) + ", got " + seqNum + ")");
                    }
                    sync_.publishNextSequenceNo();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (SecurityException e) {
                    e.printStackTrace();
                }
            }

            try {
                face_.processEvents();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (EncodingException e) {
                e.printStackTrace();
            }

        }

        ChronoSync2013.OnReceivedSyncState onReceivedSyncState = new ChronoSync2013.OnReceivedSyncState() {
            @Override
            public void onReceivedSyncState(List syncStates, boolean isRecovery) {
                for (Object o : syncStates) {

                    ChronoSync2013.SyncState syncState = (ChronoSync2013.SyncState) o;
                    Name dataPrefixName = new Name(syncState.getDataPrefix());
                    long sessionId = syncState.getSessionNo();
                    long seqNum = syncState.getSequenceNo();
                    String userName = dataPrefixName.get(-2).toEscapedString();
                    String channelName = dataPrefixName.get(-3).toEscapedString();
                    ChannelUserSession channelUserSessionKey = new ChannelUserSession(channelName, userName, sessionId);

                    Log.d(TAG, "sync state data prefix: " + dataPrefixName.toString() + ", " +
                            "our appDataPrefix; " + applicationDataPrefix_.toString());

                    if (dataPrefixName.equals(applicationDataPrefix_)) {
                        Log.d(TAG, "got sync state for own user");
                        continue;
                    }

                    if (isDuplicateSeqNum(channelUserSessionKey, seqNum))
                        continue;

                    Log.d(TAG, "\n" + "got sync state (" +
                            "sessionId " + sessionId + ", " +
                            "seqNum " + seqNum + ", " +
                            "dataPrefix " + dataPrefixName.toString() + ", " +
                            "userName " + userName + ", " +
                            "isRecovery " + isRecovery +
                            ")");

                    PeerStateTable.PeerState peerState = peerStateTable_.getPeerState(channelUserSessionKey);
                    if (peerState.highestSeqNum == NO_SEQ_NUMS) {
                        peerState.highestSeqNum = seqNum;
                    }
                    long lastSeqNum = peerState.highestSeqNum;

                    if (lastSeqNum < seqNum) {
                        for (int i = 0; i < seqNum - lastSeqNum; i++) {
                            eventNewStreamAvailable.trigger(
                                    new SyncStreamInfo(
                                            channelName,
                                            userName,
                                            sessionId,
                                            lastSeqNum + i + 1));
                        }
                    }

                    peerState.highestSeqNum = seqNum;

                }

            }
        };

        // returns true if seq num was duplicate, false if seq num was not duplicate
        private boolean isDuplicateSeqNum(ChannelUserSession channelUserSessionKey, long seqNum) {
            if (!recvdSeqNums_.containsKey(channelUserSessionKey)) {
                recvdSeqNums_.put(channelUserSessionKey, new HashSet<Long>());
                recvdSeqNums_.get(channelUserSessionKey).add(seqNum);
                return false;
            }

            HashSet<Long> seqNums = recvdSeqNums_.get(channelUserSessionKey);
            if (seqNums.contains(seqNum)) {
                Log.d(TAG, "duplicate seq num " + seqNum + " from " + channelUserSessionKey);
                return true;
            }
            seqNums.add(seqNum);
            return false;
        }

        ChronoSync2013.OnInitialized onInitialized = new ChronoSync2013.OnInitialized() {
            @Override
            public void onInitialized() {
                Log.d(TAG, "sync initialized, initial seq num " + sync_.getSequenceNo());
                eventInitialized.trigger();
            }
        };

        OnRegisterFailed onRegisterFailed = new OnRegisterFailed() {
            @Override
            public void onRegisterFailed(Name prefix) {
                Log.e(TAG, "registration failed for " + prefix.toString());
            }
        };

        // taken from https://github.com/named-data-mobile/NFD-android/blob/4a20a88fb288403c6776f81c1d117cfc7fced122/app/src/main/java/net/named_data/nfd/utils/NfdcHelper.java
        private KeyChain configureKeyChain() {

            final MemoryIdentityStorage identityStorage = new MemoryIdentityStorage();
            final MemoryPrivateKeyStorage privateKeyStorage = new MemoryPrivateKeyStorage();
            final KeyChain keyChain = new KeyChain(new IdentityManager(identityStorage, privateKeyStorage),
                    new SelfVerifyPolicyManager(identityStorage));

            Name name = new Name("/tmp-identity");

            try {
                // create keys, certs if necessary
                if (!identityStorage.doesIdentityExist(name)) {
                    keyChain.createIdentityAndCertificate(name);

                    // set default identity
                    keyChain.getIdentityManager().setDefaultIdentity(name);
                }
            }
            catch (SecurityException e){
                e.printStackTrace();
            }

            return keyChain;

        }
    }

}