package com.ginkage.bpmdetect;

import static com.google.common.base.Preconditions.checkNotNull;

class BpmDetect {

    private static final String TAG = "BpmDetect";

    interface BpmCallback {
        void onCreate(float[] xAxis);
        void onProcess(float[] yAxis, float bpm, long lastRead, int shift);
    }

    private final long nativeBpmDetectPtr;
    private final BpmCallback callback;
    private final CircularBuffer samples;
    private final float[] values;

    private long lastRead;

    static {
        System.loadLibrary("bpm_detect_jni");
    }

    public BpmDetect(CircularBuffer samples, int windowSize, BpmCallback callback) {
        this.samples = samples;
        this.values = new float[windowSize];
        this.callback = checkNotNull(callback);
        this.nativeBpmDetectPtr = nativeInit(CaptureThread.SAMPLE_RATE, windowSize);
    }

    synchronized void processSamples() {
        if (samples.getLatest() >= values.length) {
            lastRead = System.nanoTime();
            samples.read(values, values.length);
            nativeProcess(nativeBpmDetectPtr, values);
        }
    }

    void destroy() {
        nativeDestroy(nativeBpmDetectPtr);
    }

    void onCreate(float[] xAxis) {
        callback.onCreate(xAxis);
    }

    void onProcess(float[] yAxis, float bpm, int shift) {
        callback.onProcess(yAxis, bpm, lastRead, shift);
    }

    private native long nativeInit(int sampleRate, int windowSize);

    private native void nativeProcess(long nativeBpmDetectPtr, float[] data);

    private native void nativeDestroy(long nativeBpmDetectPtr);
}
