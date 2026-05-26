#include <jni.h>
#include <string>
#include <unistd.h>
#include <sys/ptrace.h>
#include <sys/uio.h>
#include <fcntl.h>
#include <elf.h>
#include <dlfcn.h>
#include <android/log.h>
#include <stdio.h>
#include <inttypes.h>
#include <array>
#include <sys/prctl.h>
#include <linux/capability.h>
#include <sstream>
#include <sys/wait.h>
#include <sys/capability.h>
#include "dirent.h"
#include <stdlib.h>
#include <string.h>

#define LOG_TAG "Test"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

void grant_ptrace_capability() {
    prctl(PR_SET_KEEPCAPS, 1);
    __android_log_print(ANDROID_LOG_DEBUG, "Test", "Attempting to set capabilities...");
}

std::string execCommand(const char* cmd) {
    std::array<char, 128> buffer;
    std::string result;
    FILE* pipe = popen(cmd, "r");
    if (!pipe) throw std::runtime_error("popen() failed!");
    while (fgets(buffer.data(), buffer.size(), pipe) != nullptr) {
        result += buffer.data();
    }
    pclose(pipe);
    return result;
}

std::string getMapsContent(pid_t pid) {
    char cmd[256];
    snprintf(cmd, sizeof(cmd), "su -c cat /proc/%d/maps 2>/dev/null", pid);
    return execCommand(cmd);
}

uintptr_t getModuleBaseAddress(pid_t pid, const char* moduleName) {
    __android_log_print(ANDROID_LOG_DEBUG,"UID","uid=%d",getuid());
    std::string mapsContent = getMapsContent(pid);
    if (mapsContent.empty()) {
        LOGI("无法获取 maps 内容，可能权限不足或进程不存在");
        return 0;
    }
    std::istringstream stream(mapsContent);
    std::string line;
    while (std::getline(stream, line)) {
        if (line.find(moduleName) != std::string::npos) {
            uintptr_t base = 0;
            sscanf(line.c_str(), "%" PRIxPTR "-%*lx", &base);
            LOGI("找到模块: %s, 基址: 0x%lx", moduleName, base);
            //return base;
        }
    }
    LOGI("未找到模块: %s", moduleName);
    return 0;
}
extern "C" {

JNIEXPORT jlong JNICALL
Java_com_storm_coldwind_SecondActivity_getModuleBase(
        JNIEnv* env, jobject thiz, jint pid, jstring moduleName) {
    const char* module = env->GetStringUTFChars(moduleName, nullptr);
    uintptr_t base = getModuleBaseAddress(pid, module);
    env->ReleaseStringUTFChars(moduleName, module);
    return (jlong)base;
}

}