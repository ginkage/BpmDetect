package com.ginkage.bpmdetect;

import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.util.Log;

import static android.media.AudioAttributes.USAGE_MEDIA;
import static android.media.AudioAttributes.USAGE_UNKNOWN;
import static android.media.AudioFormat.CHANNEL_IN_STEREO;
import static android.media.AudioFormat.ENCODING_PCM_FLOAT;

public class CaptureThread extends Thread {
    private static final String TAG = "CaptureThread";

    private static final int BUFFER_SIZE = 524288;
    static final int BPM_BUFFER_SIZE = 131072;
    static final int SAMPLE_RATE = 48000;
    private static final int CHANNEL_CONFIG = CHANNEL_IN_STEREO;
    private static final int AUDIO_FORMAT = ENCODING_PCM_FLOAT;

    private boolean isRunning;
    private final Object runLock = new Object();
    private AudioRecord audioRecord;
    private final float[] buffer = new float[512];
    private final CircularBuffer stereoBuffer = new CircularBuffer(2 * BUFFER_SIZE);
    private final CircularBuffer ampBuffer = new CircularBuffer(BUFFER_SIZE);
    private BpmThread bpmThread;

    @Override
    public void run() {
        while (isRunning) {
            synchronized (runLock) {
                if (isRunning && audioRecord != null) {
                    int samples = audioRecord.read(
                            buffer, 0, buffer.length, AudioRecord.READ_BLOCKING);
                    if (samples > 0) {
                        stereoBuffer.write(buffer, samples);

                        int frames = samples / 2;
                        for (int i = 0, t = 0; i < frames; i++) {
                            // Read two elements, then write one back.
                            // Using the same array for input and output is not very safe,
                            // but should be fine in this case.
                            buffer[i] = (float) Math.hypot(buffer[t++], buffer[t++]);
                        }

                        ampBuffer.write(buffer, frames);
                    }
                }
            }
        }

        if (bpmThread != null) {
            bpmThread.shutdown();
        }
    }

    CircularBuffer getBuffer(BpmDetect.BpmCallback callback) {
        bpmThread = new BpmThread(ampBuffer, BPM_BUFFER_SIZE, callback);
        bpmThread.start();
        return stereoBuffer;
    }

    void startCapture(MediaProjection mediaProjection) {
        AudioPlaybackCaptureConfiguration configuration =
                new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                        .addMatchingUsage(USAGE_MEDIA)
                        .addMatchingUsage(USAGE_UNKNOWN)
                        .build();

        int minSize = Math.max(BUFFER_SIZE * 4 * 2,
                AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT));

        AudioRecord record = new AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(configuration)
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG)
                        .setEncoding(AUDIO_FORMAT)
                        .build())
                .setBufferSizeInBytes(minSize)
                .build();
        record.startRecording();

        synchronized(runLock) {
            audioRecord = record;
            isRunning = true;
            start();
        }
    }

    void stopCapture() {
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

        synchronized (runLock) {
            if (audioRecord != null) {
                audioRecord.stop();
                audioRecord = null;
            }
        }
    }
}
