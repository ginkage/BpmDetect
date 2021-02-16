#include <cmath>

#include "wavelet.h"

Wavelet::Wavelet(int size, int maxLevel)
    : length(size)
    , levels(std::min(std::ilogb(size), maxLevel))
    , decomp(levels)
{
    unsigned int half = size / 2;
    for (int level = 0; level < levels; level++) {
        decomp[level] = { std::vector<float>(half), std::vector<float>(half) };
        half >>= 1u;
    }
}

// 1-D forward transforms from time domain to all possible Hilbert domains
std::vector<decomposition>& Wavelet::decompose(const float* data)
{
    const float* prev = data;
    for (auto& level : decomp) {
        forward(prev, level);
        prev = level.first.data();
    }
    return decomp;
}

void Wavelet::forward(const float* data, decomposition& out)
{
    std::vector<float>& energy = out.first;
    std::vector<float>& detail = out.second;
    unsigned int half = energy.size();
    unsigned int mask = (half << 1u) - 1u;

    for (unsigned int i = 0; i < half; ++i) {
        float e = 0, d = 0;
        for (unsigned int j = 0; j < 8; ++j) {
            float v = data[((i << 1u) + j) & mask];
            // low pass filter for the energy (approximation)
            e += v * scalingDecom[j];
            // high pass filter for the details
            d += v * waveletDecom[j];
        }
        energy[i] = e;
        detail[i] = d;
    }
}
