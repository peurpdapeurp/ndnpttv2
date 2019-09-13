//package com.example.ndnpttv2.back_end.rec_module.stream_producer;
//
//import java.io.FileDescriptor;
//import java.io.IOException;
//import java.io.InputStream;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.LinkedTransferQueue;
//
//import android.content.Context;
//import android.media.MediaRecorder;
//import android.os.Handler;
//import android.os.ParcelFileDescriptor;
//import android.os.SystemClock;
//import android.util.Log;
//
//import com.example.ndnpttv2.R;
//import com.example.ndnpttv2.util.Helpers;
//
//import net.named_data.jndn.ContentType;
//import net.named_data.jndn.Data;
//import net.named_data.jndn.Face;
//import net.named_data.jndn.Interest;
//import net.named_data.jndn.InterestFilter;
//import net.named_data.jndn.MetaInfo;
//import net.named_data.jndn.Name;
//import net.named_data.jndn.OnInterestCallback;
//import net.named_data.jndn.OnRegisterFailed;
//import net.named_data.jndn.OnRegisterSuccess;
//import net.named_data.jndn.encoding.EncodingException;
//import net.named_data.jndn.security.KeyChain;
//import net.named_data.jndn.security.SecurityException;
//import net.named_data.jndn.security.identity.IdentityManager;
//import net.named_data.jndn.security.identity.MemoryIdentityStorage;
//import net.named_data.jndn.security.identity.MemoryPrivateKeyStorage;
//import net.named_data.jndn.security.policy.SelfVerifyPolicyManager;
//import net.named_data.jndn.util.Blob;
//import net.named_data.jndn.util.MemoryContentCache;
//
//public class StreamProducer {
//
//    private final static String TAG = "StreamProducer";
//
//    // Private constants
//    private final static int MAX_READ_SIZE = 2000;
//
//    // Events for stream statistics and ui notifications
//    private static final int EVENT_SEGMENT_PUBLISHED = 0;
//    private static final int EVENT_FINAL_SEGMENT_RECORDED = 1;
//
//    private Thread t_;
//    private MediaRecorderThread mediaRecorderThread_;
//    private ParcelFileDescriptor[] mediaRecorderPfs_;
//    private ParcelFileDescriptor mediaRecorderReadPfs_, mediaRecorderWritePfs_;
//    private InputStream mediaRecorderInputStream_;
//    private ADTSFrameReadingState readingState_;
//    private FrameBundler bundler_;
//    private FramePacketizer packetizer_;
//    private long currentStreamID_ = 0;
//    private long currentSegmentNum_ = 0;
//    private Name currentStreamPrefix_;
//    private ConcurrentHashMap<Name, Long> streamToFinalBlockId_; // records final block id of stream names
//    private LinkedTransferQueue<Data> audioPacketTransferQueue_;
//    private NetworkThread networkThread_;
//    private Context ctx_;
//    private Options options_;
//    private Handler uiHandler_;
//
//    public static class Options {
//        public Options(long framesPerSegment, int producerSamplingRate) {
//            this.framesPerSegment = framesPerSegment;
//            this.producerSamplingRate = producerSamplingRate;
//        }
//        long framesPerSegment;
//        int producerSamplingRate;
//    }
//
//    // reference for ADTS header format: https://wiki.multimedia.cx/index.php/ADTS
//    private static class ADTSFrameReadingState {
//        byte[] buffer = new byte[MAX_READ_SIZE];
//        short current_frame_length = Short.MAX_VALUE; // includes ADTS header length
//        int current_bytes_read = 0;
//    }
//
//    public StreamProducer(Context ctx, Options options) {
//        options_ = options;
//        ctx_ = ctx;
//        // set up necessary state
//        readingState_ = new ADTSFrameReadingState();
//        bundler_ = new FrameBundler(options_.framesPerSegment);
//        packetizer_ = new FramePacketizer();
//        audioPacketTransferQueue_ = new LinkedTransferQueue<>();
//
//        // set up file descriptors to read stream from MediaRecorderThread
//        try {
//            mediaRecorderPfs_ = ParcelFileDescriptor.createPipe();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        mediaRecorderReadPfs_ = new ParcelFileDescriptor(mediaRecorderPfs_[0]);
//        mediaRecorderWritePfs_ = new ParcelFileDescriptor(mediaRecorderPfs_[1]);
//
//        mediaRecorderThread_ = new MediaRecorderThread(mediaRecorderWritePfs_.getFileDescriptor());
//        mediaRecorderInputStream_ = new ParcelFileDescriptor.AutoCloseInputStream(mediaRecorderReadPfs_);
//
//        streamToFinalBlockId_ = new ConcurrentHashMap<>();
//
//        networkThread_ = new NetworkThread(new Name(ctx_.getString(R.string.network_prefix)), uiHandler_);
//
//        Log.d(TAG, System.currentTimeMillis() + ": " +
//                "Initialized (" +
//                "framesPerSegment " + options_.framesPerSegment + ", " +
//                "producerSamplingRate " + options_.producerSamplingRate +
//                ")");
//    }
//
//    public Long getFinalBlockIdOfStream(Name streamPrefix) {
//        return streamToFinalBlockId_.get(streamPrefix);
//    }
//
//    public void start(Name streamPrefix, int bundleSize) {
//        currentStreamPrefix_ = streamPrefix;
//        bundler_.setMaxBundleSize(bundleSize);
//        if (t_ == null) {
//            t_ = new Thread(this);
//            t_.start();
//        }
//    }
//
//    public void stop() {
//        if (t_ != null) {
//            mediaRecorderThread_.stop();
//            t_.interrupt();
//            try {
//                t_.join();
//            } catch (InterruptedException e) {}
//            t_ = null;
//        }
//        currentStreamID_++; // increment stream ID by one for next recording
//    }
//
//    public void run() {
//
//        Log.d(TAG,"StreamProducer frame processor started.");
//
//        byte[] final_adts_frame_buffer;
//
//        try {
//
//            networkThread_.start();
//            mediaRecorderThread_.start();
//
//            while (!Thread.interrupted()) {
//
//                int read_size = mediaRecorderInputStream_.available();
//                if (read_size > MAX_READ_SIZE) {
//                    read_size = MAX_READ_SIZE;
//                }
//                if (read_size <= 0) { continue; }
//
//                Log.d(TAG, "Attempting to read " + read_size + " bytes from input stream.");
//                try {
//                    int ret = mediaRecorderInputStream_.read(readingState_.buffer,
//                            readingState_.current_bytes_read,
//                            read_size);
//                    if (ret == -1) { break; }
//
//                }
//                catch (IOException e) { e.printStackTrace(); }
//
//                Log.d(TAG, "Current contents of reading state buffer: " +
//                        Helpers.bytesToHex(readingState_.buffer));
//
//                readingState_.current_bytes_read += read_size;
//                Log.d(TAG, "Current bytes read: " + readingState_.current_bytes_read);
//
//                // we've finished reading enough of the header to get the frame length
//                if (readingState_.current_bytes_read >= 5) {
//                    readingState_.current_frame_length =
//                            (short) ((((readingState_.buffer[3]&0x02) << 3 | (readingState_.buffer[4]&0xE0) >> 5) << 8) +
//                                    ((readingState_.buffer[4]&0x1F) << 3 | (readingState_.buffer[5]&0xE0) >> 5));
//                    Log.d(TAG, "Length of current ADTS frame: " +
//                            readingState_.current_frame_length);
//                }
//
//                // we've read the entirety of the current ADTS frame, deliver the full adts frame for
//                // processing and set the reading state properly
//                if (readingState_.current_bytes_read >= readingState_.current_frame_length) {
//
//                    Log.d(TAG, "Detected that we read a full ADTS frame. Length of current ADTS frame: " +
//                            readingState_.current_frame_length);
//
//                    final_adts_frame_buffer = Arrays.copyOf(readingState_.buffer, readingState_.current_frame_length);
//                    Log.d(TAG, "ADTS frame read from MediaRecorder stream: " +
//                            Helpers.bytesToHex(final_adts_frame_buffer));
//
//                    bundler_.addFrame(final_adts_frame_buffer);
//
//                    // check if there is a full bundle of audio data in the bundler yet; if there is,
//                    // deliver it to the FramePacketizer
//                    if (bundler_.hasFullBundle()) {
//                        Log.d(TAG, "Bundler did have a full bundle, packetizing full audio bundle...");
//
//                        byte[] audioBundle = bundler_.getCurrentBundle();
//
//                        Log.d(TAG, "Contents of full audio bundle: " + Helpers.bytesToHex(audioBundle));
//
//                        Data audioPacket = packetizer_.generateAudioDataPacket(audioBundle, false, currentSegmentNum_);
//
//                        currentSegmentNum_++;
//
//                        audioPacketTransferQueue_.add(audioPacket);
//                    }
//                    else {
//                        Log.d(TAG, "Bundler did not yet have full bundle, extracting next ADTS frame...");
//                    }
//
//                    // we did not read past the end of the current ADTS frame
//                    if (readingState_.current_bytes_read == readingState_.current_frame_length) {
//                        readingState_.current_bytes_read = 0;
//                        readingState_.current_frame_length = Short.MAX_VALUE;
//                    }
//                    else { // we did read past the end of the current ADTS frame
//                        int read_over_length = readingState_.current_bytes_read - readingState_.current_frame_length;
//                        System.arraycopy(readingState_.buffer, readingState_.current_frame_length,
//                                readingState_.buffer, 0,
//                                read_over_length);
//
//                        readingState_.current_bytes_read = read_over_length;
//                        readingState_.current_frame_length = Short.MAX_VALUE;
//                    }
//
//                }
//
//                SystemClock.sleep(100); // sleep to reduce battery usage
//
//            }
//
//            Log.d(TAG, "StreamProducer frame processor was interrupted; checking for last audio bundle...");
//            if (bundler_.getCurrentBundleSize() > 0) {
//                Log.d(TAG, "Detected a leftover audio bundle after recording ended with " + bundler_.getCurrentBundleSize() + " frames," +
//                        " publishing a partially filled end of stream data packet (segment number " + currentSegmentNum_ + ").");
//                byte[] audioBundle = bundler_.getCurrentBundle();
//
//                Log.d(TAG, "Contents of last full audio bundle: " + Helpers.bytesToHex(audioBundle));
//
//                Data endOfStreamPacket = packetizer_.generateAudioDataPacket(audioBundle, true, currentSegmentNum_);
//
//                streamToFinalBlockId_.put(currentStreamPrefix_, currentSegmentNum_);
//
//                audioPacketTransferQueue_.add(endOfStreamPacket);
//            }
//            else {
//                Log.d(TAG, "Detected no leftover audio bundle after recording ended, publishing empty end of stream data packet " +
//                        "(segment number " + currentSegmentNum_ + ").");
//
//                Data endOfStreamPacket = packetizer_.generateAudioDataPacket(new byte[] {}, true, currentSegmentNum_);
//
//                streamToFinalBlockId_.put(currentStreamPrefix_, currentSegmentNum_);
//
//                audioPacketTransferQueue_.add(endOfStreamPacket);
//            }
//
//            notifyUiEvent(EVENT_FINAL_SEGMENT_RECORDED, currentSegmentNum_);
//
//            // reset the segment number
//            currentSegmentNum_ = 0;
//
//        } catch (IOException e) {
//            e.printStackTrace();
//        } catch (ArrayIndexOutOfBoundsException e) {
//            e.printStackTrace();
//        }
//
//        Log.d(TAG,"StreamProducer frame processor stopped.");
//
//    }
//
//    private class MediaRecorderThread implements Runnable {
//
//        private static final String TAG = "AudioStreamer_MediaRecorderThread";
//
//        private FileDescriptor ofs_;
//        private Thread t_;
//        private MediaRecorder recorder_;
//
//        public MediaRecorderThread(FileDescriptor ofs) {
//            ofs_ = ofs;
//        }
//
//        public void start() {
//            if (t_ == null) {
//                t_ = new Thread(this);
//                t_.start();
//            }
//        }
//
//        public void stop() {
//            // introduce a half a second delay for the end of stream recording, so that MediaRecorder
//            // doesn't cut off end of stream
//            SystemClock.sleep(500);
//
//            try {
//                if (recorder_ != null) {
//                    recorder_.stop();
//                }
//                Log.d(TAG, "Recording stopped.");
//                if (t_ != null) {
//
//                    t_.interrupt();
//                    t_.join();
//
//                }
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            } catch (IllegalStateException e) {
//                e.printStackTrace();
//            } catch (RuntimeException e) {
//                e.printStackTrace();
//            }
//            if (recorder_ != null) {
//                recorder_.release();
//            }
//            recorder_ = null;
//            t_ = null;
//        }
//
//        @Override
//        public void run() {
//
//            // configure the recorder
//            recorder_ = new MediaRecorder();
//            recorder_.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
//            recorder_.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS);
//            recorder_.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
//            recorder_.setAudioChannels(1);
//            recorder_.setAudioSamplingRate(options_.producerSamplingRate);
//            recorder_.setAudioEncodingBitRate(10000);
//            recorder_.setOutputFile(ofs_);
//
//            Log.d(TAG, "Recording started...");
//            try {
//                recorder_.prepare();
//                recorder_.start();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//
//        }
//    }
//
//    private class FrameBundler {
//
//        private final static String TAG = "AudioStreamer_FrameBundler";
//
//        private long maxBundleSize_ = 10; // number of frames per audio bundle
//        private ArrayList<byte[]> bundle_;
//        private int current_bundle_size_; // number of frames in current bundle
//
//        FrameBundler(long maxBundleSize) {
//            maxBundleSize = maxBundleSize_;
//            bundle_ = new ArrayList<byte[]>();
//            current_bundle_size_ = 0;
//        }
//
//        public int getCurrentBundleSize() {
//            return current_bundle_size_;
//        }
//
//        public boolean addFrame(byte[] frame) {
//            if (current_bundle_size_ == maxBundleSize_)
//                return false;
//
//            bundle_.add(frame);
//            current_bundle_size_++;
//
//            return true;
//        }
//
//        public boolean hasFullBundle() {
//            return (current_bundle_size_ == maxBundleSize_);
//        }
//
//        public byte[] getCurrentBundle() {
//            int bundleLength = 0;
//            for (byte[] frame : bundle_) {
//                bundleLength += frame.length;
//            }
//            Log.d(TAG, "Length of audio bundle: " + bundleLength);
//            byte[] byte_array_bundle = new byte[bundleLength];
//            int current_index = 0;
//            for (byte[] frame : bundle_) {
//                System.arraycopy(frame, 0, byte_array_bundle, current_index, frame.length);
//                current_index += frame.length;
//            }
//
//            bundle_.clear();
//            current_bundle_size_ = 0;
//
//            return byte_array_bundle;
//        }
//
//        public void setMaxBundleSize(int bundleSize) {
//            maxBundleSize_ = bundleSize;
//        }
//
//    }
//
//    private class FramePacketizer {
//
//        private static final String TAG = "AudioStreamer_FramePacketizer";
//
//        public Data generateAudioDataPacket(byte[] audioBundle, boolean final_block, long seg_num) {
//
//            // generate the audio packet's name
//            Name dataName = new Name(currentStreamPrefix_);
//            dataName.appendSegment(seg_num); // set the segment ID
//
//            // generate the audio packet and fill it with the audio bundle data
//            Data data = new Data(dataName);
//            data.setContent(new Blob(audioBundle));
//            // TODO: need to add real signing of data packet
//            KeyChain.signWithHmacWithSha256(data, new Blob(Helpers.temp_key));
//
//            // set the final block id, if this is the last audio packet of its stream
//            MetaInfo metaInfo = new MetaInfo();
//            if (final_block) {
//                metaInfo.setFinalBlockId(Name.Component.fromSegment(seg_num));
//                data.setMetaInfo(metaInfo);
//            }
//
//            return data;
//
//        }
//
//    }
//
//    private class Network {
//
//        private final static String TAG = "StreamProducer_Network";
//        private Face face_;
//        private MemoryContentCache mcc_;
//        private Name networkPrefix_;
//
//        public Network(Name networkPrefix, Face face, MemoryContentCache mcc) {
//            networkPrefix_ = networkPrefix;
//            face_ = face;
//            mcc_ = mcc;
//        }
//
//        public void run() {
//
//            Log.d(TAG,"NetworkThread started.");
//
//            try {
//
//                // set up keychain
//                KeyChain keyChain = configureKeyChain();
//
//                // set up face
//                face_ = new Face();
//                try {
//                    face_.setCommandSigningInfo(keyChain, keyChain.getDefaultCertificateName());
//                } catch (SecurityException e) {
//                    e.printStackTrace();
//                }
//
//                // set up memory content cache
//                mcc_ = new MemoryContentCache(face_);
//                mcc_.registerPrefix(networkPrefix_,
//                        new OnRegisterFailed() {
//                            @Override
//                            public void onRegisterFailed(Name prefix) {
//                                Log.d(TAG, "Prefix registration for " + prefix + " failed.");
//                            }
//                        },
//                        new OnRegisterSuccess() {
//                            @Override
//                            public void onRegisterSuccess(Name prefix, long registeredPrefixId) {
//                                Log.d(TAG, "Prefix registration for " + prefix + " succeeded.");
//                            }
//                        },
//                        new OnInterestCallback() {
//                            @Override
//                            public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId, InterestFilter filter) {
//                                Log.d(TAG, "No data in MCC found for interest " + interest.getName().toUri());
//                                Name streamPrefix = interest.getName().getPrefix(-1);
//                                Long finalBlockId = streamToFinalBlockId_.get(streamPrefix);
//
//                                if (finalBlockId == null) {
//                                    Log.d(TAG, "Did not find final block id for stream with name " + streamPrefix.toUri());
//                                    mcc_.storePendingInterest(interest, face);
//                                }
//                                else {
//                                    Log.d(TAG, "Found final block id " + finalBlockId + " for stream with name " + streamPrefix.toUri());
//                                    Data appNack = new Data();
//                                    appNack.setName(interest.getName());
//                                    MetaInfo metaInfo = new MetaInfo();
//                                    metaInfo.setType(ContentType.NACK);
//                                    metaInfo.setFreshnessPeriod(1.0);
//                                    appNack.setMetaInfo(metaInfo);
//                                    appNack.setContent(new Blob(Helpers.longToBytes(finalBlockId)));
//                                    Log.d(TAG, "Putting application nack with name " + interest.getName().toUri() + " in mcc.");
//                                    mcc_.add(appNack);
//                                    try {
//                                        face.putData(appNack);
//                                    } catch (IOException e) {
//                                        e.printStackTrace();
//                                    }
//                                }
//
//                            }
//                        }
//                );
//
//                while (!Thread.interrupted()) {
//                    if (audioPacketTransferQueue_.size() != 0) {
//
//                        Data data = (Data) audioPacketTransferQueue_.poll();
//                        if (data == null) continue;
//                        Log.d(TAG, "NetworkThread received data packet." + "\n" +
//                                "Name: " + data.getName() + "\n" +
//                                "FinalBlockId: " + data.getMetaInfo().getFinalBlockId().getValue().toHex());
//                        notifyUiEvent(EVENT_SEGMENT_PUBLISHED, data.getName().get(-1).toSegment());
//                        mcc_.add(data);
//
//                    }
//                    face_.processEvents();
//
//                    SystemClock.sleep(100); // sleep to reduce battery usage
//                }
//
//            } catch (ArrayIndexOutOfBoundsException e) {
//                e.printStackTrace();
//            } catch (IOException e) {
//                e.printStackTrace();
//            } catch (EncodingException e) {
//                e.printStackTrace();
//            } catch (SecurityException e) {
//                e.printStackTrace();
//            }
//
//            Log.d(TAG,"NetworkThread stopped.");
//
//        }
//
//    }
//
//}
//
