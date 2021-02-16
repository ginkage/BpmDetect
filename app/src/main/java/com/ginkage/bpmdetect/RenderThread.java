package com.ginkage.bpmdetect;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.icu.util.TimeUnit;
import android.util.Log;
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;

import java.time.Duration;
import java.util.Locale;

public class RenderThread extends Thread implements SurfaceHolder.Callback, BpmDetect.BpmCallback {
    private static final String TAG = "RenderThread";

    private static final int WINDOW_SIZE = 2048;

    private boolean isRunning;
    private final Object runLock = new Object();
    private final Object bpmLock = new Object();
    private int width;
    private int height;
    private FftData fft;
    private CircularBuffer circularBuffer;
    private SurfaceHolder surfaceHolder;
    private final FreqData freq = new FreqData(WINDOW_SIZE, CaptureThread.SAMPLE_RATE);
    private final SlidingMedian slidingMedian = new SlidingMedian(Duration.ofSeconds(10).toNanos());

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        setIsRunning(true);
        start();
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        this.width = width;
        this.height = height;
        this.surfaceHolder = holder;
        freq.resize(width, height);
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        boolean retry = true;
        setIsRunning(false);
        while (retry) {
            try {
                join();
                retry = false;
            } catch (InterruptedException e) {
                Log.w(TAG, "Exception while stopping render thread", e);
            }
        }
    }

    @Override
    public void run() {
        while (isRunning) {
            synchronized (runLock) {
                // Check if we have enough data to process
                if (isRunning && surfaceHolder != null && fft != null && circularBuffer != null
                        && circularBuffer.getLatest() >= WINDOW_SIZE * 2) {
                    // Critical section. Do not allow isRunning to be set false until
                    // we are sure all canvas draw operations are complete.
                    //
                    // If isRunning has been toggled false, inhibit canvas operations.
                    Canvas canvas = null;
                    try {
                        canvas = surfaceHolder.lockCanvas(null);
                        if (canvas != null) {
                            draw(canvas);
                        }
                    } finally {
                        // do this in a finally so that if an exception is thrown
                        // during the above, we don't leave the Surface in an
                        // inconsistent state
                        if (canvas != null) {
                            surfaceHolder.unlockCanvasAndPost(canvas);
                        }
                    }
                }
            }
        }
    }

    private void draw(Canvas canvas)
    {
        float[] data = fft.execute();

        double ky = height / 64.0;
        double prevAmp = 0;
        int lastx = -1;
        int baseY = height;

        // Clear with black
        canvas.drawRGB(0, 0, 0);
        Paint paint = new Paint();

        // Draw the lines
        for (int k = freq.minK, t = k * 2; k < freq.maxK; k++) {
            double amp = Math.hypot(data[t++], data[t++]);
            prevAmp = Math.max(prevAmp, amp);
            int x = freq.x[k];
            if (lastx < x) {
                lastx = x; // + 3; // Leave some space between the lines
                int y = (int) (prevAmp * ky + 0.5);
                prevAmp = 0;

                FreqData.Color c = freq.color[k];
                paint.setARGB(255, c.r, c.g, c.b);
                canvas.drawLine(x, baseY, x, baseY - y, paint);
            }
        }

        synchronized (bpmLock) {
            if (freq.bpm > 0) {
                int half = height / 2;
                int size = freq.wx.length;
                paint.setARGB(255, 255, 255, 255);
                lastx = width;
                int lasty = half, miny = half;
                for (int i = 0; i < size; i++) {
                    int x = (int) Math.floor(freq.wx[i] * width + 0.5);
                    int y = (int) (half - half * freq.wy[i]);
                    if (x == lastx) {
                        miny = Math.min(y, miny);
                    } else {
                        canvas.drawLine(lastx, lasty, x, miny, paint);
                        lastx = x;
                        lasty = miny;
                        miny = y;
                    }
                }

                paint.setTextSize(96);
                paint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
                String text = String.format(Locale.getDefault(), "%.1f", freq.bpm);
                Rect textBounds = new Rect();
                paint.getTextBounds(text, 0, text.length(), textBounds);
                canvas.drawText(text, 48, textBounds.height() + 48, paint);
            }
        }
    }

    void setIsRunning(boolean running) {
        synchronized (runLock) {
            isRunning = running;
        }
    }

    void setDataSource(CaptureThread captureThread) {
        synchronized (runLock) {
            circularBuffer = captureThread.getBuffer(this);
            fft = new FftData(WINDOW_SIZE, circularBuffer);
        }
    }

    @Override
    public void onCreate(float[] xAxis) {
        synchronized (bpmLock) {
            freq.wx = new float[xAxis.length];
            freq.wy = new float[xAxis.length];
            System.arraycopy(xAxis, 0, freq.wx, 0, xAxis.length);
        }
    }

    @Override
    public void onProcess(float[] yAxis, float bpm) {
        synchronized (bpmLock) {
            // Log.i(TAG, "Update BPM");
            System.arraycopy(yAxis, 0, freq.wy, 0, yAxis.length);
            freq.bpm = slidingMedian.offer(bpm);
        }
    }
}
