package com.ginkage.bpmdetect;

import org.jtransforms.fft.FloatFFT_1D;

public class FftData {
    private final int size; // Number of values (2 * samples) to analyze
    private final CircularBuffer buffer;
    private final float[] input;
    private final FloatFFT_1D fft;

    FftData(int n, CircularBuffer buf) {
        size = n * 2;
        buffer = buf;
        input = new float[size];
        fft = new FloatFFT_1D(n);
    }

    /**
     * Processes input data taken from a buffer provided during construction, returns data as pairs
     * of floats corresponding to the real and imaginary parts of output.
     */
    public float[] execute() {
        buffer.read(input, size);
        fft.complexForward(input);
        return input;
    }
}
