#include <android/log.h>
#include <jni.h>
#include "wavelet_bpm_detector.h"

#define JNI_METHOD(return_type, method_name) \
  JNIEXPORT return_type JNICALL              \
      Java_com_ginkage_bpmdetect_BpmDetect_##method_name

namespace {

    inline jlong jptr(WaveletBPMDetector *native_app) {
        return reinterpret_cast<intptr_t>(native_app);
    }

    inline WaveletBPMDetector *native(jlong ptr) {
        return reinterpret_cast<WaveletBPMDetector *>(ptr);
    }

    class JNIThreadCallbacks {
    public:
        JNIThreadCallbacks(JNIEnv *env, jobject obj, int n) {
            env_ = env;
            size_ = n;
            obj_ = env->NewGlobalRef(obj);
            dst_wx_ = reinterpret_cast<jfloatArray>(env->NewGlobalRef(env->NewFloatArray(n)));
            dst_wy_ = reinterpret_cast<jfloatArray>(env->NewGlobalRef(env->NewFloatArray(n)));

            jclass clazz = env->FindClass("com/ginkage/bpmdetect/BpmDetect");
            method_on_create_ = env->GetMethodID(clazz, "onCreate", "([F)V");
            method_on_process_ = env->GetMethodID(clazz, "onProcess", "([FF)V");
        }

        void onCreate(float *wx) {
            env_->SetFloatArrayRegion(dst_wx_, 0, size_, reinterpret_cast<const jfloat *>(wx));
            env_->CallVoidMethod(obj_, method_on_create_, dst_wx_);
        }

        void onProcess(float *wy, float bpm) {
            env_->SetFloatArrayRegion(dst_wy_, 0, size_, reinterpret_cast<const jfloat *>(wy));
            env_->CallVoidMethod(obj_, method_on_process_, dst_wy_, bpm);
        }

        void onDestroy() {
            env_->DeleteGlobalRef(dst_wx_);
            env_->DeleteGlobalRef(dst_wy_);
            env_->DeleteGlobalRef(obj_);
        }

    private:
        int size_;
        JNIEnv *env_ = nullptr;
        jfloatArray dst_wx_;
        jfloatArray dst_wy_;
        jmethodID method_on_create_;
        jmethodID method_on_process_;
        jobject obj_;
    };

}  // anonymous namespace

extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    return JNI_VERSION_1_6;
}

JNI_METHOD(jlong, nativeInit)
(JNIEnv *env, jobject obj, jint sample_rate, jint window_size) {
    auto *detector = new WaveletBPMDetector(sample_rate, window_size);
    FreqData *data = detector->getData();
    auto *callbacks = new JNIThreadCallbacks(env, obj, data->wx.size());
    data->callbacks = callbacks;
    callbacks->onCreate(data->wx.data());
    return jptr(detector);
}

JNI_METHOD(void, nativeProcess)
(JNIEnv *env, jobject obj, jlong native_app, jfloatArray data) {
    WaveletBPMDetector *detector = native(native_app);
    jfloat *elements = env->GetFloatArrayElements(data, nullptr);
    FreqData *output = detector->computeWindowBpm(elements);
    env->ReleaseFloatArrayElements(data, elements, 0);
    auto *callbacks = reinterpret_cast<JNIThreadCallbacks *>(output->callbacks);
    callbacks->onProcess(output->wy.data(), output->bpm);
}

JNI_METHOD(void, nativeDestroy)
(JNIEnv *env, jobject obj, jlong native_app) {
    WaveletBPMDetector *detector = native(native_app);
    FreqData *data = detector->getData();
    auto *callbacks = reinterpret_cast<JNIThreadCallbacks *>(data->callbacks);
    callbacks->onDestroy();
    delete callbacks;
    delete detector;
}

}  // extern "C"
