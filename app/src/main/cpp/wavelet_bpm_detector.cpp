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
    , maxPace(1u << (levels - 1))
    , corrSize(size / maxPace)
    , corr(corrSize)
    , wavelet(size, levels)
    , dCMinLength(corrSize / 2)
    , dC(dCMinLength)
    , dCSum(dCMinLength)
    , minute(sampleRate * 60.0f / maxPace)
    , minIndex(minute / 220.0f)
    , maxIndex(minute / std::max(40.0f, sampleRate * 180.0f / windowSize))
    , in(corr.data())
    , out(fftwf_alloc_complex(corrSize / 2 + 1))
    , plan_forward(fftwf_plan_dft_r2c_1d(corrSize, in, out, FFTW_MEASURE))
    , plan_back(fftwf_plan_dft_c2r_1d(corrSize, out, in, FFTW_MEASURE))
    , slidingMedian(std::chrono::seconds(5))
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
    for (int i = minIndex; i < maxIndex; ++i) {
        max = std::max(max, std::fabs(data[i]));
        maxP = std::max(maxP, data[i]);
    }

    float scale = 1.0f / max;
    int k = -1;
    for (int i = minIndex; i < maxIndex; ++i) {
        freq.wy[i - minIndex] = data[i] * scale;
        if (data[i] == maxP && k < 0) {
            k = i;
        }
    }

    return k;
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
    for (float& value : data) {
        value = std::fabs(value);
    }

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
        //  2) Absolute value
        //  3) Subtract mean
        undersample(decomp[loop].second, pace, dC);

        // Recombine detail coefficients
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

    return &freq;
}

FreqData *WaveletBPMDetector::getData() {
    return &freq;
}
