#pragma once

#include <vector>

typedef std::pair<std::vector<float>, std::vector<float>> decomposition;

class Wavelet {
public:
    Wavelet(int size, int maxLevel);

    // 1-D forward transforms from time domain to all possible Hilbert domains
    std::vector<decomposition>& decompose(const float* data);

protected:
    // 1-D forward transform from time domain to Hilbert domain
    void forward(const float* data, decomposition& out);

private:
    unsigned int length;
    unsigned int levels;
    std::vector<decomposition> decomp;

    float scalingDecom[8] { -0.010597401784997278, 0.032883011666982945, 0.030841381835986965,
                            -0.18703481171888114, -0.02798376941698385, 0.6308807679295904, 0.7148465705525415,
                            0.23037781330885523 };

    float waveletDecom[8] { 0.23037781330885523, -0.7148465705525415, 0.6308807679295904,
                            0.02798376941698385, -0.18703481171888114, -0.030841381835986965, 0.032883011666982945,
                            0.010597401784997278 };
};
