#pragma once

#include <fftw-3.3.9/api/fftw3.h>
#include <vector>

class Correlate
{
public:
    Correlate(int size);

    int correlate(std::vector<float>& x, std::vector<float>& y);

private:
    int n;
    int corrSize;
    std::vector<float> corrX;
    std::vector<float> corrY;
    float *inX;
    float *inY;
    fftwf_complex* outX;
    fftwf_complex* outY;
    fftwf_plan plan_forward_x;
    fftwf_plan plan_forward_y;
    fftwf_plan plan_back_x;
};