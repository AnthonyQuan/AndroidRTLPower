package com.sdrtouch.rtlsdr;

/**
 * Created by Anthony on 5/03/2017.
 */

public interface OnRecordCompleted {
    void beginSpectrumUpload();
    void recordSpectrumFailed();
}
