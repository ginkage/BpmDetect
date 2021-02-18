#pragma once

#include <vector>

struct FreqData {
    float bpm;
    int shift;
    std::vector<float> wx;
    std::vector<float> wy;
    void *callbacks;
};
