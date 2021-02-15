cmake_minimum_required(VERSION 3.4.1)

# Standard Android dependencies
find_library(android-lib android)
find_library(log-lib log)

include_directories(.)

add_subdirectory(fftw-3.3.9)

# General Sources
file(GLOB general_srcs "*.cpp")

# Output binary
add_library(bpm_detect_jni SHARED
    ${general_srcs})

# Build
target_link_libraries(bpm_detect_jni
    fftw3f
    ${android-lib}
    ${log-lib})
