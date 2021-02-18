#include "wavelet_bpm_detector.h"

#include <algorithm>
#include <cfloat>
#include <cmath>
#include <complex>
#include <cstring>
#include <numeric>
#include <utility>

WaveletBPMDetector::WaveletBPMDetector(int rate, int size)
    : sampleRate(rate)
    , windowSize(size)
    , levels(4)
    , maxPace(1 << (levels - 1))
    , corrSize(size / maxPace)
    , corr(corrSize)
    , wavelet(size, levels)
    , dCMinLength(corrSize / 2)
    , dC(dCMinLength)
    , dCSum(dCMinLength)
    , minute(sampleRate * 60.0f / maxPace)
    , minIndex(minute / 220.0f)
    , maxIndex(minute / 40.0f)
    , in(corr.data())
    , out(fftwf_alloc_complex(corrSize / 2 + 1))
    , plan_forward(fftwf_plan_dft_r2c_1d(corrSize, in, out, FFTW_MEASURE))
    , plan_back(fftwf_plan_dft_c2r_1d(corrSize, out, in, FFTW_MEASURE))
    , slidingMedian(std::chrono::seconds(5))
    , correlate(size)
    , rect(size)
    , comb(size)
{
    maxIndex = std::min(maxIndex, dCMinLength);
    freq.wx = std::vector<float>(maxIndex - minIndex);
    freq.wy = std::vector<float>(maxIndex - minIndex);
    float nom = 1.0f / (1.0f / minIndex - 1.0f / maxIndex);
    float start = nom / maxIndex;
    for (int i = minIndex; i < maxIndex; ++i) {
        freq.wx[i - minIndex] = nom / i - start;
    }
}

WaveletBPMDetector::~WaveletBPMDetector()
{
    fftwf_destroy_plan(plan_forward);
    fftwf_destroy_plan(plan_back);
    fftwf_free(out);
}

/**
 * Identifies the location of data with the maximum absolute
 * value (either positive or negative). If multiple data
 * have the same absolute value the last positive is taken
 * @param data the input array from which to identify the maximum
 * @return the index of the maximum value in the array
 **/
int WaveletBPMDetector::detectPeak(std::vector<float>& data)
{
    float max = FLT_MIN, maxP = FLT_MIN;

    // Straighten the curve
    for (int i = minIndex; i < maxIndex; ++i) {
        data[i] /= float(windowSize - i);
    }

    for (int i = minIndex; i < maxIndex; ++i) {
        max = std::max(max, std::fabs(data[i]));
        maxP = std::max(maxP, data[i]);
    }

    float scale = 1.0f / max;
    for (int i = minIndex; i < maxIndex; ++i) {
        freq.wy[i - minIndex] = data[i] * scale;
    }

    for (int i = minIndex; i < maxIndex; ++i) {
        if (data[i] == maxP) {
            return i;
        }
    }

    return -1;
}

static void undersample(std::vector<float>& data, unsigned int pace, std::vector<float>& out)
{
    unsigned int length = data.size();
    for (unsigned int i = 0, j = 0; j < length; ++i, j += pace) {
        out[i] = data[j];
    }
}

void WaveletBPMDetector::recombine(std::vector<float>& data)
{
    float mean = std::accumulate(data.begin(), data.end(), 0.0f) / (float)data.size();

    for (int i = 0; i < dCMinLength; ++i) {
        dCSum[i] += data[i] - mean;
    }
}

std::vector<float> WaveletBPMDetector::autocorrelate(std::vector<float>& data)
{
    int n = data.size();
    memcpy(in, data.data(), n * sizeof(float));
    memset(in + n, 0, n * sizeof(float));

    fftwf_execute(plan_forward);

    auto* cplx = (std::complex<float>*)out;
    for (int i = 0; i <= n; i++) {
        cplx[i] *= std::conj(cplx[i]);
    }

    fftwf_execute(plan_back);

    float scale = 1.0f / corrSize;
    for (int i = 0; i < n; i++) {
        data[i] = corr[i] * scale;
    }

    return data;
}

FreqData *WaveletBPMDetector::computeWindowBpm(const float* data)
{
    // Apply DWT
    std::vector<decomposition>& decomp = wavelet.decompose(data);
    std::fill(dCSum.begin(), dCSum.end(), 0);

    // 4 Level DWT
    for (unsigned int loop = 0, pace = maxPace; loop < levels; ++loop, pace >>= 1) {
        // Extract envelope from detail coefficients
        //  1) Undersample
        //  2) Absolute value (actually, input array already contains absolute values)
        //  3) Subtract mean
        undersample(decomp[loop].second, pace, dC);

        // Recombine detail coeffients
        recombine(dC);
    }

    // Add the last approximated data
    recombine(decomp[levels - 1].first);

    // Autocorrelation
    autocorrelate(dCSum);

    // Detect peak in correlated data
    int location = detectPeak(dCSum);

    // Compute window BPM given the peak
    float tmp_bpm = minute / location;

    // Convert it to sliding window median BPM
    freq.bpm = slidingMedian.offer(std::make_pair(tmp_bpm, std::chrono::steady_clock::now()));

    // Comb filter with target BPM pattern
    memcpy(rect.data(), data, windowSize * sizeof(float));
    std::fill(comb.begin(), comb.end(), 0);
    float step = freq.bpm / (sampleRate * 60.0f);
    float accum = 1;

    // Fill in periodical peaks, trying to stay as close to the period as possible
    for (int i = 0; i < windowSize; i++) {
        if (accum >= 1) {
            comb[i] = 1;
            accum -= 1;
        }
        accum += step;
    }

    // Correlate the two arrays, find the shift in samples.
    // We don't care if it's not the first beat.
    freq.shift = correlate.correlate(rect, comb);

    return &freq;
}

FreqData *WaveletBPMDetector::getData() {
    return &freq;
}
