#pragma once

#include <vector>

struct FreqData {
    float bpm;
    std::vector<float> wx;
    std::vector<float> wy;
    void *callbacks;
};
