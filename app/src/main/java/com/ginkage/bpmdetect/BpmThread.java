package com.ginkage.bpmdetect;

import android.util.Log;

public class BpmThread extends Thread {
    private static final String TAG = "BpmThread";
    private boolean isRunning = true;
    private final Object runLock = new Object();
    private final CircularBuffer samples;
    private final int size;
    private final BpmDetect.BpmCallback callback;

    BpmThread(CircularBuffer circularBuffer, int size, BpmDetect.BpmCallback callback) {
        this.samples = circularBuffer;
        this.size = size;
        this.callback = callback;
    }

    @Override
    public void run() {
        BpmDetect bpmDetect = new BpmDetect(samples, size, callback);

        while (isRunning) {
            synchronized (runLock) {
                if (isRunning) {
                    bpmDetect.processSamples();
                }
            }
        }

        bpmDetect.destroy();
    }

    void shutdown() {
        synchronized (runLock) {
            isRunning = false;
        }

        boolean retry = true;
        while (retry) {
            try {
                join();
                retry = false;
            } catch (InterruptedException e) {
                Log.w(TAG, "Exception while stopping capture thread", e);
            }
        }
    }
}
