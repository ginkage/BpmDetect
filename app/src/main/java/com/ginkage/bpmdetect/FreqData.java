package com.ginkage.bpmdetect;

public class FreqData {
    static class Color {
        int r;
        int g;
        int b;
    }

    // This is for FFT visualization
    // Note == (12 * Octave + Spectre), where Spectre is in [0, 12)
    final Color[] color; // "Rainbow"-based note color
    final double[] note; // Note "value" for the frequency
    final int[] x; // Horizontal position in the visualization
    final int minK, maxK; // The range of "meaningful" frequencies

    // This is for BPM
    float bpm;
    float[] wx;
    float[] wy;

    // "Saw" function with specified range, peaks at (p / 2).
    private static double saw(double val, double p)
    {
        double x = val / p;
        return p * Math.abs(x - Math.floor(x + 0.5));
    }

    FreqData(int n1, int rate)
    {
        color = new Color[n1];
        note = new double[n1];
        x = new int[n1];

        double maxFreq = 2 * n1;
        double minFreq = rate / maxFreq;
        double base = Math.log(Math.pow(2, 1.0 / 12.0));
        // Frequency 440 is a note number 57 = 12 * 4 + 9
        double fcoef = Math.pow(2, 57.0 / 12.0) / 440.0;

        // Notes in [36, 108] range, i.e. 6 octaves
        minK = (int) Math.ceil(Math.exp(35 * base) / (minFreq * fcoef));
        maxK = (int) Math.ceil(Math.exp(108 * base) / (minFreq * fcoef));

        for (int k = 1; k < n1; k++) {
            double frequency = k * minFreq;
            double fnote = Math.log(frequency * fcoef) / base; // note = 12 * Octave + Note
            double spectre = fnote % 12.0; // spectre is within [0, 12)
            double R = saw(spectre - 6, 12); // Peaks at C (== 0)
            double G = saw(spectre - 10, 12); // Peaks at E (== 4)
            double B = saw(spectre - 2, 12); // Peaks at G# (== 8)
            double mn = saw(spectre - 2, 4); // Minimum of them is also periodic

            // Technically, the formula for every component is:
            // Result == 255 * (C - Min) / (Max - Min),
            // where Min and Max are the smallest and the biggest of { R, G, B },
            // but Min is periodic, and (Max - Min) == 4, a constant.
            Color c = new Color();
            c.r = (int)((R - mn) * 63.75 + 0.5);
            c.g = (int)((G - mn) * 63.75 + 0.5);
            c.b = (int)((B - mn) * 63.75 + 0.5);

            color[k] = c;
            note[k] = fnote;
        }
    }

    void resize(int width, int height) {
        // The notes range is slightly wider than what we'll draw, to add a border
        double minNote = 34;
        double maxNote = 110;
        double kx = width / (maxNote - minNote);

        // Recalcualte the lines positions
        for (int k = minK; k < maxK; k++) {
            x[k] = (int) ((note[k] - minNote) * kx + 0.5);
        }
    }
}
