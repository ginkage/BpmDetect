#include "correlate.h"

#include <cstdio>
#include <cstring>
#include <complex>

Correlate::Correlate(int size)
    : n(size)
    , corrSize(n * 2)
    , corrX(corrSize)
    , corrY(corrSize)
    , inX(corrX.data())
    , inY(corrY.data())
    , outX(fftwf_alloc_complex(n + 1))
    , outY(fftwf_alloc_complex(n + 1))
    , plan_forward_x(fftwf_plan_dft_r2c_1d(corrSize, inX, outX, FFTW_MEASURE))
    , plan_forward_y(fftwf_plan_dft_r2c_1d(corrSize, inY, outY, FFTW_MEASURE))
    , plan_back_x(fftwf_plan_dft_c2r_1d(corrSize, outX, inX,FFTW_MEASURE))
{}

int Correlate::correlate(std::vector<float> &x, std::vector<float> &y) {
    memcpy(inX, x.data(), n * sizeof(float));
    memcpy(inY, y.data(), n * sizeof(float));
    memset(inX + n, 0, n * sizeof(float));
    memset(inY + n, 0, n * sizeof(float));

    fftwf_execute(plan_forward_x);
    fftwf_execute(plan_forward_y);

    auto* cplxX = (std::complex<float>*)outX;
    auto* cplxY = (std::complex<float>*)outY;
    for (int i = 0; i <= n; i++) {
        cplxX[i] *= std::conj(cplxY[i]);
    }

    fftwf_execute(plan_back_x);

    float scale = 1.0f / n;
    for (int i = 0; i < n; i++) {
        x[i] = corrX[i] * scale;
    }

    return std::max_element(x.begin(), x.end()) - x.begin();
}

