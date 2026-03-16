/*
 * Copyright 2019 Odnoklassniki Ltd, Mail.Ru Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Original source: https://github.com/odnoklassniki/jvmti-tools#vmtrace

#include <jvmti.h>
#include <stdarg.h>
#include <string.h>
#include <stdio.h>

static FILE* out;
static jrawMonitorID vmtrace_lock;
static jlong start_time;

static void trace(jvmtiEnv* jvmti, const char* fmt, ...) {
    jlong current_time;
    jvmti->GetTime(&current_time);

    char buf[1024];
    va_list args;
    va_start(args, fmt);
    vsnprintf(buf, sizeof(buf), fmt, args);
    va_end(args);

    jvmti->RawMonitorEnter(vmtrace_lock);

    fprintf(out, "[%.5f] %s\n", (current_time - start_time) / 1000000000.0, buf);
    
    jvmti->RawMonitorExit(vmtrace_lock);
}

static char* fix_class_name(char* class_name) {
    // Strip 'L' and ';' from class signature
    class_name[strlen(class_name) - 1] = 0;
    return class_name + 1;
}

class ClassName {
  private:
    jvmtiEnv* _jvmti;
    char* _name;

  public:
    ClassName(jvmtiEnv* jvmti, jclass klass) : _jvmti(jvmti), _name(NULL) {
        _jvmti->GetClassSignature(klass, &_name, NULL);
    }

    ~ClassName() {
        _jvmti->Deallocate((unsigned char*) _name);
    }

    char* name() {
        return _name == NULL ? NULL : fix_class_name(_name);
    }
};

void JNICALL ClassFileLoadHook(jvmtiEnv* jvmti, JNIEnv* env,
                               jclass class_being_redefined, jobject loader,
                               const char* name, jobject protection_domain,
                               jint data_len, const unsigned char* data,
                               jint* new_data_len, unsigned char** new_data) {
    trace(jvmti, "Loading class: %s (%d bytes)", name, data_len);
}

void JNICALL ClassPrepare(jvmtiEnv* jvmti, JNIEnv* env,
                          jthread thread, jclass klass) {
    ClassName cn(jvmti, klass);
    trace(jvmti, "Class prepared: %s", cn.name());
}

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM* vm, char* options, void* reserved) {
    if (options == NULL || !options[0]) {
        out = stderr;
    } else if ((out = fopen(options, "w")) == NULL) {
        fprintf(stderr, "Cannot open output file: %s\n", options);
        return 1;
    }

    jvmtiEnv* jvmti;
    vm->GetEnv((void**) &jvmti, JVMTI_VERSION_1_0);

    jvmti->CreateRawMonitor("vmtrace_lock", &vmtrace_lock);
    jvmti->GetTime(&start_time);

    trace(jvmti, "VMTrace started");

    jvmtiCapabilities capabilities = {0};
    capabilities.can_generate_all_class_hook_events = 1;
    jvmti->AddCapabilities(&capabilities);

    jvmtiEventCallbacks callbacks = {0};
    callbacks.ClassFileLoadHook = ClassFileLoadHook;
    callbacks.ClassPrepare = ClassPrepare;
    jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks));

    jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, NULL);
    jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_CLASS_PREPARE, NULL);

    return 0;
}

JNIEXPORT void JNICALL Agent_OnUnload(JavaVM* vm) {
    if (out != NULL && out != stderr) {
        fclose(out);
    }
}
