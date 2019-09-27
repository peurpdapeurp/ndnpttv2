package com.example.ndnpttv2.back_end.pq_module.stream_player.exoplayer_customization;

import android.net.Uri;
import android.os.ParcelFileDescriptor;

import androidx.annotation.Nullable;

import com.example.ndnpttv2.util.Logger;
import com.example.ndnpttv2.util.Pipe;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.BaseDataSource;
import com.google.android.exoplayer2.upstream.DataSpec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static com.example.ndnpttv2.util.Logger.DebugInfo.LOG_ERROR;

public class InputStreamDataSource extends BaseDataSource {

    private static final String TAG = "InputStreamDataSource";

    private static final int LAST_BYTE_POSITION_UNKNOWN = -1;

    private Pipe pipe_;
    private Uri uri_;
    private boolean opened_ = false;

    private int readPosition_;
    private int writePosition_ = 0;
    private int lastBytePosition_ = LAST_BYTE_POSITION_UNKNOWN;

    /**
     * Creates base data source.
     */
    public InputStreamDataSource() {
        super(false);
        pipe_ = new Pipe();
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        uri_ = dataSpec.uri;
        transferInitializing(dataSpec);
        readPosition_ = (int) dataSpec.position;
        if (dataSpec.length != C.LENGTH_UNSET) {
            Logger.logDebugEvent(TAG,LOG_ERROR,"The length of the data spec should not be set for an input " +
                    "stream data source; the total amount of data is unknown at " +
                    "open time.",System.currentTimeMillis());
            throw new IOException("The length of the data spec should not be set for an input " +
                    "stream data source; the total amount of data is unknown at " +
                    "open time.");
        }
        opened_ = true;
        transferStarted(dataSpec);
        return C.LENGTH_UNSET;
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        if (lastBytePosition_ != LAST_BYTE_POSITION_UNKNOWN && readPosition_ >= lastBytePosition_) {
            return C.RESULT_END_OF_INPUT;
        }
        if (readLength == 0) {
            return 0;
        }
        int bytesAvailable = pipe_.getInputStream().available();
        if (bytesAvailable == 0) return 0;
        readLength = Math.min(bytesAvailable, readLength);
        pipe_.getInputStream().read(buffer, offset, readLength);
        readPosition_ += readLength;
        bytesTransferred(readLength);
        return readLength;
    }

    public void write(byte[] data, boolean finalWrite) {
        writePosition_ += data.length;
        if (finalWrite) {
            lastBytePosition_ = writePosition_;
        }
        try {
            pipe_.getOutputStream().write(data);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Nullable
    @Override
    public Uri getUri() {
        return uri_;
    }

    @Override
    public void close() throws IOException {
        if (opened_) {
            opened_ = false;
            transferEnded();
        }
        pipe_.close();
        uri_ = null;
    }



}