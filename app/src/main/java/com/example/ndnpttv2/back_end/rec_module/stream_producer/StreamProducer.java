package com.example.ndnpttv2.back_end.rec_module.stream_producer;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.LinkedTransferQueue;

import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.ndnpttv2.back_end.ProgressEventInfo;
import com.example.ndnpttv2.back_end.threads.NetworkThread;
import com.example.ndnpttv2.util.Helpers;
import com.example.ndnpttv2.util.Pipe;
import com.pploder.events.Event;
import com.pploder.events.SimpleEvent;

import net.named_data.jndn.ContentType;
import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.MetaInfo;
import net.named_data.jndn.Name;
import net.named_data.jndn.encoding.EncodingException;
import net.named_data.jndn.security.KeyChain;
import net.named_data.jndn.util.Blob;
import net.named_data.jndn.util.MemoryContentCache;

public class StreamProducer {

    private final static String TAG = "StreamProducer";

    // Private constants
    private static final int MAX_READ_SIZE = 2000;
    private static final int PROCESSING_INTERVAL_MS = 50;
    private static final int FINAL_BLOCK_ID_UNKNOWN = -1;

    // Messages
    private static final int MSG_DO_SOME_WORK = 0;
    private static final int MSG_RECORD_START = 1;
    private static final int MSG_RECORD_STOP = 2;

    // Events
    public Event<ProgressEventInfo> eventSegmentPublished;
    public Event<ProgressEventInfo> eventFinalSegmentPublished;

    private MediaRecorder mediaRecorder_;
    private Network network_;
    private FrameProcessor frameProcessor_;
    private Name streamName_;
    private Options options_;
    private Handler handler_;
    private boolean streamProducerClosed_ = false;
    private boolean recordingFinished_ = false;
    private boolean frameProcessingFinished_ = false;
    private long finalBlockId_ = FINAL_BLOCK_ID_UNKNOWN;

    public static class Options {
        public Options(long framesPerSegment, int producerSamplingRate, int bundleSize) {
            this.framesPerSegment = framesPerSegment;
            this.producerSamplingRate = producerSamplingRate;
            this.bundleSize = bundleSize;
        }
        long framesPerSegment;
        int producerSamplingRate;
        int bundleSize;
    }

    public StreamProducer(Name applicationDataPrefix, long streamSeqNum, NetworkThread.Info networkThreadInfo, Options options) {

        streamName_ = new Name(applicationDataPrefix).appendSequenceNumber(streamSeqNum);
        options_ = options;
        Pipe audioDataPipe = new Pipe();

        eventSegmentPublished = new SimpleEvent<>();
        eventFinalSegmentPublished = new SimpleEvent<>();

        handler_ = new Handler(networkThreadInfo.looper) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                switch (msg.what) {
                    case MSG_DO_SOME_WORK: {
                        doSomeWork();
                        break;
                    }
                    case MSG_RECORD_START: {
                        mediaRecorder_.start();
                        doSomeWork();
                        break;
                    }
                    case MSG_RECORD_STOP: {
                        Log.d(TAG, "Got MSG_RECORD_STOP");
                        mediaRecorder_.stopRecording();
                        break;
                    }
                    default: {
                        throw new IllegalStateException("unexpected msg.what " + msg.what);
                    }
                }
            }
        };

        network_ = new Network(networkThreadInfo.face);
        frameProcessor_ = new FrameProcessor(audioDataPipe.getInputStream());
        mediaRecorder_ = new MediaRecorder(audioDataPipe.getOutputFileDescriptor());

        Log.d(TAG, System.currentTimeMillis() + ": " +
                "Initialized (" +
                "framesPerSegment " + options_.framesPerSegment + ", " +
                "producerSamplingRate " + options_.producerSamplingRate +
                ")");
    }

    public void recordStart() {
        handler_.obtainMessage(MSG_RECORD_START).sendToTarget();
    }

    public void recordStop() {
        handler_.obtainMessage(MSG_RECORD_STOP).sendToTarget();
    }

    private void doSomeWork() {
        network_.doSomeWork();
        frameProcessor_.doSomeWork();
        if (!streamProducerClosed_) {
            scheduleNextWork(SystemClock.uptimeMillis());
        }
    }

    private void scheduleNextWork(long thisOperationStartTimeMs) {
        handler_.removeMessages(MSG_DO_SOME_WORK);
        handler_.sendEmptyMessageAtTime(MSG_DO_SOME_WORK, thisOperationStartTimeMs + PROCESSING_INTERVAL_MS);
    }

    private void close() {
        handler_.removeCallbacksAndMessages(null);
        streamProducerClosed_ = true;
        Log.d(TAG, "Closed.");
    }

    private class FrameProcessor {

        private static final String TAG = "StreamProducer_FrameProcessor";

        private ADTSFrameReadingState readingState_;
        private FrameBundler bundler_;
        private FramePacketizer packetizer_;
        private InputStream inputStream_;
        private long currentSegmentNum_ = 0;
        private boolean closed_ = false;

        // reference for ADTS header format: https://wiki.multimedia.cx/index.php/ADTS
        private class ADTSFrameReadingState {
            byte[] buffer = new byte[MAX_READ_SIZE];
            short current_frame_length = Short.MAX_VALUE; // includes ADTS header length
            int current_bytes_read = 0;
        }

        private FrameProcessor(InputStream inputStream) {
            inputStream_ = inputStream;

            // set up frame processing state
            readingState_ = new ADTSFrameReadingState();
            bundler_ = new FrameBundler(options_.framesPerSegment);
            packetizer_ = new FramePacketizer();
        }

        private void doSomeWork() {

            if (closed_) return;

            if (!recordingFinished_) {

                try {
                    byte[] final_adts_frame_buffer;

                    int read_size = inputStream_.available();
                    if (read_size > MAX_READ_SIZE) {
                        read_size = MAX_READ_SIZE;
                    }
                    if (read_size <= 0) {
                        return;
                    }

                    Log.d(TAG, "Attempting to read " + read_size + " bytes from input stream.");
                    try {
                        int ret = inputStream_.read(readingState_.buffer,
                                readingState_.current_bytes_read,
                                read_size);
                        if (ret == -1) {
                            return;
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    Log.d(TAG, "Current contents of reading state buffer: " +
                            Helpers.bytesToHex(readingState_.buffer));

                    readingState_.current_bytes_read += read_size;
                    Log.d(TAG, "Current bytes read: " + readingState_.current_bytes_read);

                    // we've finished reading enough of the header to get the frame length
                    if (readingState_.current_bytes_read >= 5) {

                        readingState_.current_frame_length =
                                (short) ((((readingState_.buffer[3] & 0x02) << 3 | (readingState_.buffer[4] & 0xE0) >> 5) << 8) +
                                        ((readingState_.buffer[4] & 0x1F) << 3 | (readingState_.buffer[5] & 0xE0) >> 5));
                        Log.d(TAG, "Length of current ADTS frame: " +
                                readingState_.current_frame_length);

                    }

                    // we've read the entirety of the current ADTS frame, deliver the full adts frame for
                    // processing and set the reading state properly
                    if (readingState_.current_bytes_read >= readingState_.current_frame_length) {

                        Log.d(TAG, "Detected that we read a full ADTS frame. Length of current ADTS frame: " +
                                readingState_.current_frame_length);

                        final_adts_frame_buffer = Arrays.copyOf(readingState_.buffer, readingState_.current_frame_length);
                        Log.d(TAG, "ADTS frame read from MediaRecorder stream: " +
                                Helpers.bytesToHex(final_adts_frame_buffer));

                        bundler_.addFrame(final_adts_frame_buffer);

                        // check if there is a full bundle of audio data in the bundler yet; if there is,
                        // deliver it to the FramePacketizer
                        if (bundler_.hasFullBundle()) {

                            Log.d(TAG, "Bundler did have a full bundle, packetizing full audio bundle...");

                            byte[] audioBundle = bundler_.getCurrentBundle();

                            Log.d(TAG, "Contents of full audio bundle: " + Helpers.bytesToHex(audioBundle));

                            Data audioPacket = packetizer_.generateAudioDataPacket(audioBundle, false, currentSegmentNum_);

                            currentSegmentNum_++;

                            network_.sendDataPacket(audioPacket);

                        } else {
                            Log.d(TAG, "Bundler did not yet have full bundle, extracting next ADTS frame...");
                        }

                        // we did not read past the end of the current ADTS frame
                        if (readingState_.current_bytes_read == readingState_.current_frame_length) {

                            readingState_.current_bytes_read = 0;
                            readingState_.current_frame_length = Short.MAX_VALUE;

                        } else { // we did read past the end of the current ADTS frame

                            int read_over_length = readingState_.current_bytes_read - readingState_.current_frame_length;
                            System.arraycopy(readingState_.buffer, readingState_.current_frame_length,
                                    readingState_.buffer, 0,
                                    read_over_length);

                            readingState_.current_bytes_read = read_over_length;
                            readingState_.current_frame_length = Short.MAX_VALUE;

                        }

                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            else {

                Log.d(TAG, "StreamProducer frame processor detected that recording finished; checking for last audio bundle...");

                if (bundler_.getCurrentBundleSize() > 0) {

                    Log.d(TAG, "Detected a leftover audio bundle after recording ended with " + bundler_.getCurrentBundleSize() + " frames," +
                            " publishing a partially filled end of stream data packet (segment number " + currentSegmentNum_ + ").");
                    byte[] audioBundle = bundler_.getCurrentBundle();

                    Log.d(TAG, "Contents of last full audio bundle: " + Helpers.bytesToHex(audioBundle));

                    Data endOfStreamPacket = packetizer_.generateAudioDataPacket(audioBundle, true, currentSegmentNum_);

                    network_.sendDataPacket(endOfStreamPacket);

                } else {

                    Log.d(TAG, "Detected no leftover audio bundle after recording ended, publishing empty end of stream data packet " +
                            "(segment number " + currentSegmentNum_ + ").");

                    Data endOfStreamPacket = packetizer_.generateAudioDataPacket(new byte[]{}, true, currentSegmentNum_);

                    network_.sendDataPacket(endOfStreamPacket);

                }

                eventSegmentPublished.trigger(new ProgressEventInfo(streamName_, currentSegmentNum_));

                finalBlockId_ = currentSegmentNum_;

                closed_ = true;

                frameProcessingFinished_ = true;

            }
        }

        private class FrameBundler {

            private final static String TAG = "StreamProducer_FrameBundler";

            private long maxBundleSize_; // number of frames per audio bundle
            private ArrayList<byte[]> bundle_;
            private int current_bundle_size_; // number of frames in current bundle

            FrameBundler(long maxBundleSize) {
                maxBundleSize_ = maxBundleSize;
                bundle_ = new ArrayList<>();
                current_bundle_size_ = 0;
            }

            private int getCurrentBundleSize() {
                return current_bundle_size_;
            }

            private void addFrame(byte[] frame) {
                if (current_bundle_size_ == maxBundleSize_)
                    return;

                bundle_.add(frame);
                current_bundle_size_++;
            }

            private boolean hasFullBundle() {
                return (current_bundle_size_ == maxBundleSize_);
            }

            private byte[] getCurrentBundle() {
                int bundleLength = 0;
                for (byte[] frame : bundle_) {
                    bundleLength += frame.length;
                }
                Log.d(TAG, "Length of audio bundle: " + bundleLength);
                byte[] byte_array_bundle = new byte[bundleLength];
                int current_index = 0;
                for (byte[] frame : bundle_) {
                    System.arraycopy(frame, 0, byte_array_bundle, current_index, frame.length);
                    current_index += frame.length;
                }

                bundle_.clear();
                current_bundle_size_ = 0;

                return byte_array_bundle;
            }

        }

        private class FramePacketizer {

            private Data generateAudioDataPacket(byte[] audioBundle, boolean final_block, long seg_num) {

                Name dataName = new Name(streamName_);
                dataName.appendSegment(seg_num); // set the segment ID

                // generate the audio packet and fill it with the audio bundle data
                Data data = new Data(dataName);
                data.setContent(new Blob(audioBundle));
                // TODO: need to add real signing of data packet
                KeyChain.signWithHmacWithSha256(data, new Blob(Helpers.temp_key));

                // set the final block id, if this is the last audio packet of its stream
                MetaInfo metaInfo = new MetaInfo();
                if (final_block) {
                    metaInfo.setFinalBlockId(Name.Component.fromSegment(seg_num));
                    data.setMetaInfo(metaInfo);
                }

                return data;

            }

        }
    }

    private class MediaRecorder {

        private static final String TAG = "StreamProducer_MediaRecorder";

        private FileDescriptor ofs_;
        private android.media.MediaRecorder recorder_;

        private MediaRecorder(FileDescriptor ofs) {
            ofs_ = ofs;
        }

        private void start() {
            // configure the recorder
            recorder_ = new android.media.MediaRecorder();
            recorder_.setAudioSource(android.media.MediaRecorder.AudioSource.CAMCORDER);
            recorder_.setOutputFormat(android.media.MediaRecorder.OutputFormat.AAC_ADTS);
            recorder_.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC);
            recorder_.setAudioChannels(1);
            recorder_.setAudioSamplingRate(options_.producerSamplingRate);
            recorder_.setAudioEncodingBitRate(10000);
            recorder_.setOutputFile(ofs_);

            Log.d(TAG, "Recording started...");
            try {
                recorder_.prepare();
                recorder_.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void stopRecording() {
            // delay reporting that recording is finished for 500 ms, so that the frame processor
            // can finish processing the last part of the stream
            handler_
                    .postAtTime(() -> {
                        stop();
                        recordingFinished_ = true;
                    }, 500);
        }

        private void stop() {
            try {
                if (recorder_ != null) {
                    recorder_.stop();
                }
                Log.d(TAG, "Recording stopped.");
            } catch (IllegalStateException e) {
                e.printStackTrace();
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
            if (recorder_ != null) {
                recorder_.release();
            }
            recorder_ = null;
        }
    }

    private class Network {

        private final static String TAG = "StreamProducer_Network";
        private Face face_;
        private MemoryContentCache mcc_;
        private LinkedTransferQueue<Data> audioPacketTransferQueue_;

        private Network(Face face) {

            audioPacketTransferQueue_ = new LinkedTransferQueue<>();

            face_ = face;

            // set up memory content cache
            mcc_ = new MemoryContentCache(face_);
            mcc_.setInterestFilter(streamName_,
                    (prefix, interest, face1, interestFilterId, filter) -> {
                        Log.d(TAG, "No data in MCC found for interest " + interest.getName().toUri());
                        Name streamPrefix = interest.getName().getPrefix(-1);

                        if (finalBlockId_ == FINAL_BLOCK_ID_UNKNOWN) {
                            Log.d(TAG, "Final block id unknown for stream " + streamPrefix.toUri());
                            mcc_.storePendingInterest(interest, face1);
                        }
                        else {
                            Log.d(TAG, "Final block id " + finalBlockId_ + " known for stream " + streamPrefix.toUri());
                            Data appNack = new Data();
                            appNack.setName(interest.getName());
                            MetaInfo metaInfo = new MetaInfo();
                            metaInfo.setType(ContentType.NACK);
                            metaInfo.setFreshnessPeriod(1.0);
                            appNack.setMetaInfo(metaInfo);
                            appNack.setContent(new Blob(Helpers.longToBytes(finalBlockId_)));
                            Log.d(TAG, "Putting application nack with name " + interest.getName().toUri() + " in mcc.");
                            mcc_.add(appNack);
                            try {
                                face1.putData(appNack);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }

                    }
            );

        }

        private void doSomeWork() {
            while (audioPacketTransferQueue_.size() != 0) {

                Data data = audioPacketTransferQueue_.poll();
                if (data == null) continue;
                Log.d(TAG, "NetworkThread received data packet." + "\n" +
                        "Name: " + data.getName() + "\n" +
                        "FinalBlockId: " + data.getMetaInfo().getFinalBlockId().getValue().toHex());
                try {
                    eventSegmentPublished.trigger(new ProgressEventInfo(streamName_, data.getName().get(-1).toSegment()));
                } catch (EncodingException e) {
                    e.printStackTrace();
                    throw new IllegalStateException("failed to get segment number from " + data.getName());
                }
                mcc_.add(data);

            }

            // if frame processing is finished, that means we will get no more audio data packets to publish,
            // so just close the StreamProducer
            if (frameProcessingFinished_) {
                close();
                eventFinalSegmentPublished.trigger(new ProgressEventInfo(streamName_, 0));
            }
        }

        private void sendDataPacket(Data data) {
            audioPacketTransferQueue_.put(data);
        }

    }

}

