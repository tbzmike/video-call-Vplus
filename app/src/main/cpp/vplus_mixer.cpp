#include <jni.h>
#include <dlfcn.h>
#include <android/log.h>
#include <string>
#include <sstream>
#include <vector>
#include <algorithm>
#include <cctype>

#define LOG_TAG "VPlusMixer"
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

enum mixer_ctl_type { MIXER_CTL_TYPE_BOOL, MIXER_CTL_TYPE_INT, MIXER_CTL_TYPE_ENUM, MIXER_CTL_TYPE_BYTE, MIXER_CTL_TYPE_IEC958, MIXER_CTL_TYPE_INT64, MIXER_CTL_TYPE_UNKNOWN };
struct mixer;
struct mixer_ctl;

struct TinyApi {
    void* handle = nullptr;
    mixer* (*mixer_open)(unsigned int) = nullptr;
    void (*mixer_close)(mixer*) = nullptr;
    unsigned int (*mixer_get_num_ctls)(const mixer*) = nullptr;
    mixer_ctl* (*mixer_get_ctl)(mixer*, unsigned int) = nullptr;
    const char* (*mixer_ctl_get_name)(const mixer_ctl*) = nullptr;
    mixer_ctl_type (*mixer_ctl_get_type)(const mixer_ctl*) = nullptr;
    unsigned int (*mixer_ctl_get_num_values)(const mixer_ctl*) = nullptr;
    int (*mixer_ctl_get_value)(const mixer_ctl*, unsigned int) = nullptr;
    int (*mixer_ctl_set_value)(mixer_ctl*, unsigned int, int) = nullptr;
    int (*mixer_ctl_get_range_min)(const mixer_ctl*) = nullptr;
    int (*mixer_ctl_get_range_max)(const mixer_ctl*) = nullptr;

    bool load() {
        if (handle) return true;
        const char* paths[] = {"/vendor/lib64/libtinyalsa.so", "/system/lib64/libtinyalsa.so", "/vendor/lib/libtinyalsa.so", "/system/lib/libtinyalsa.so"};
        for (const char* p : paths) {
            handle = dlopen(p, RTLD_NOW);
            if (handle) break;
        }
        if (!handle) return false;
#define LOAD(name) do { name = reinterpret_cast<decltype(name)>(dlsym(handle, #name)); if (!name) return false; } while (0)
        LOAD(mixer_open); LOAD(mixer_close); LOAD(mixer_get_num_ctls); LOAD(mixer_get_ctl);
        LOAD(mixer_ctl_get_name); LOAD(mixer_ctl_get_type); LOAD(mixer_ctl_get_num_values);
        LOAD(mixer_ctl_get_value); LOAD(mixer_ctl_set_value); LOAD(mixer_ctl_get_range_min); LOAD(mixer_ctl_get_range_max);
#undef LOAD
        return true;
    }
};

static TinyApi api;

static std::string lower(std::string s) {
    std::transform(s.begin(), s.end(), s.begin(), [](unsigned char c){ return static_cast<char>(std::tolower(c)); });
    return s;
}

static bool isCandidate(const std::string& name) {
    const std::string n = lower(name);
    if (n.find("tx") != std::string::npos || n.find("capture") != std::string::npos || n.find("mic") != std::string::npos || n.find("adc") != std::string::npos) return false;
    if (n.find("rx") == std::string::npos) return false;
    return n.find("digital volume") != std::string::npos || n.find("rx volume") != std::string::npos || n.find("rx gain") != std::string::npos;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_tbzmike_vplus_PrivilegedAudioBackend_nativeScanMixer(JNIEnv* env, jobject) {
    if (!api.load()) return env->NewStringUTF("TinyALSA library unavailable");
    mixer* m = api.mixer_open(0);
    if (!m) return env->NewStringUTF("mixer_open(0) failed");
    std::ostringstream out;
    out << "card=0\n";
    const unsigned int count = api.mixer_get_num_ctls(m);
    out << "controls=" << count << "\n";
    unsigned int candidates = 0;
    for (unsigned int i = 0; i < count; ++i) {
        mixer_ctl* c = api.mixer_get_ctl(m, i);
        if (!c) continue;
        const char* raw = api.mixer_ctl_get_name(c);
        if (!raw) continue;
        std::string name(raw);
        if (!isCandidate(name)) continue;
        if (api.mixer_ctl_get_type(c) != MIXER_CTL_TYPE_INT) continue;
        const unsigned int values = api.mixer_ctl_get_num_values(c);
        const int min = api.mixer_ctl_get_range_min(c);
        const int max = api.mixer_ctl_get_range_max(c);
        out << "candidate=" << name << " values=" << values << " range=" << min << ".." << max;
        if (values > 0) out << " current=" << api.mixer_ctl_get_value(c, 0);
        out << "\n";
        ++candidates;
    }
    out << "candidates=" << candidates;
    api.mixer_close(m);
    return env->NewStringUTF(out.str().c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_tbzmike_vplus_PrivilegedAudioBackend_nativeSetSafeBoost(JNIEnv* env, jobject, jint percent) {
    const int p = std::max(100, std::min(200, static_cast<int>(percent)));
    if (!api.load()) return env->NewStringUTF("TinyALSA library unavailable");
    mixer* m = api.mixer_open(0);
    if (!m) return env->NewStringUTF("mixer_open(0) failed");
    std::ostringstream out;
    const unsigned int count = api.mixer_get_num_ctls(m);
    unsigned int changed = 0;
    for (unsigned int i = 0; i < count; ++i) {
        mixer_ctl* c = api.mixer_get_ctl(m, i);
        if (!c) continue;
        const char* raw = api.mixer_ctl_get_name(c);
        if (!raw || !isCandidate(raw) || api.mixer_ctl_get_type(c) != MIXER_CTL_TYPE_INT) continue;
        const unsigned int values = api.mixer_ctl_get_num_values(c);
        if (values == 0) continue;
        const int min = api.mixer_ctl_get_range_min(c);
        const int max = api.mixer_ctl_get_range_max(c);
        if (max <= min) continue;
        // Hardware mixer units are device-specific. Treat 100% as the current
        // value and use only 10% of the remaining range at the 200% endpoint.
        // This is deliberately conservative until the exact control semantics
        // are confirmed on the device; never jump straight to the raw maximum.
        const int current = api.mixer_ctl_get_value(c, 0);
        const int headroom = max - current;
        const int target = std::min(max, current + (headroom * (p - 100)) / 1000);
        if (api.mixer_ctl_set_value(c, 0, target) == 0) {
            ++changed;
            out << raw << ":" << current << "->" << target << "\n";
        }
    }
    out << "changed=" << changed;
    api.mixer_close(m);
    return env->NewStringUTF(out.str().c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_tbzmike_vplus_PrivilegedAudioBackend_nativeRestoreSnapshot(JNIEnv* env, jobject, jstring snapshot) {
    // Restoration is intentionally kept out of the first native revision.
    // The Kotlin layer records a scan before enabling hardware boost, and the
    // exact control/value snapshot will be wired once the device controls are
    // confirmed. Returning a truthful status is safer than guessing controls.
    (void)snapshot;
    return env->NewStringUTF("restore-not-yet-applied");
}
