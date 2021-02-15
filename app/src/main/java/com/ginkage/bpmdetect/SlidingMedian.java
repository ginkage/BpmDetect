package com.ginkage.bpmdetect;

import java.util.LinkedList;
import java.util.Queue;
import java.util.TreeSet;

public class SlidingMedian {
    private final long windowSize; // in nanoseconds

    // Sorted by timestamp
    private final Queue<Sample> data = new LinkedList<>();

    // Sorted by value
    private final TreeSet<Sample> left = new TreeSet<>();
    private final TreeSet<Sample> right = new TreeSet<>();

    public SlidingMedian(long windowSize) {
        this.windowSize = windowSize;
    }

    float offer(float bpm) {
        Sample sample = new Sample();
        sample.bpm = bpm;
        sample.timestamp = System.nanoTime();

        // Assume that equal timestamps correspond to equal values.
        // Remove oldest values from the old data.
        long oldest = sample.timestamp - windowSize;
        while (!data.isEmpty()) {
            Sample front = data.element();
            if (front.timestamp > oldest) {
                break;
            }
            if (!left.remove(front)) {
                right.remove(front);
            }
            data.poll();
        }

        // Insert the new value into the correct tree
        data.add(sample);
        if (!right.isEmpty() && sample.bpm < right.first().bpm) {
            left.add(sample);
        } else {
            right.add(sample);
        }

        // Rebalance: we could have deleted enough values to disturb the balance
        while (left.size() > right.size()) {
            Sample it = left.last();
            right.add(it);
            left.remove(it);
        }
        while (left.size() + 1 < right.size()) {
            Sample it = right.first();
            left.add(it);
            right.remove(it);
        }

        // Return the new median value.
        float middle = right.first().bpm;
        if (left.size() == right.size()) {
            return (left.last().bpm + middle) / 2;
        }

        // left.size() < right.size()
        return middle;
    }

    static class Sample implements Comparable<Sample> {
        float bpm;
        long timestamp;

        @Override
        public int compareTo(Sample o) {
            return (bpm == o.bpm)
                    ? Long.compare(timestamp, o.timestamp)
                    : Float.compare(bpm, o.bpm);
        }
    }
};
