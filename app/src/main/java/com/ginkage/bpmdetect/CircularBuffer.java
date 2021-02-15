package com.ginkage.bpmdetect;

// Simple lock-free circular buffer implementation.
public class CircularBuffer {
    private final float[] buffer; // Backing array
    private final int size; // Maximum number of frames to store
    private int pos = 0; // Position just after the last added value
    private long overwritten = 0;
    private long total_written = 0;

    CircularBuffer(final int size) {
        this.size = size;
        buffer = new float[size];
    }

    // Replace oldest samples in the circular buffer with input values
    void write(float[] values, int n)
    {
        // Write {n} values to the buffer, *then* change the current position
        for (int k, j = 0; j < n; j += k) {
            k = Math.min(pos + (n - j), size) - pos;
            System.arraycopy(values, j, buffer, pos, k);
            pos = (pos + k) % size;
        }

        total_written += n;

        if (total_written > size) {
            overwritten = total_written - size;
        }
    }

    // Retrieve latest samples in the circular buffer
    long read(float[] values, int n) {
        return readAt(total_written - n, values, n);
    }

    // Retrieve samples at the specified position
    long readAt(long from, float[] values, int n)
    {
        from = Math.max(from, overwritten);

        // Read the current position, *then* read values
        long first = pos - (total_written - from);
        while (first < 0)
            first += size;
        int start = (int) first;

        for (int k, j = 0; j < n; j += k) {
            k = Math.min(start + (n - j), size) - start;
            System.arraycopy(buffer, start, values, j, k);
            start = (start + k) % size;
        }

        return from + n;
    }

    long getLatest() { return total_written; }
}