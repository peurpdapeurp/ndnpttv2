
JNI_FOLDER_PATH := $(call my-dir)

# Build my test file
include $(CLEAR_VARS)
LOCAL_PATH := $(JNI_FOLDER_PATH)
LOCAL_SHARED_LIBRARIES := \
    boost_system_shared boost_thread_shared boost_log_shared boost_stacktrace_basic_shared \
    ndn_cpp_shared ndnrtc_shared webrtc_static
LOCAL_MODULE := test
LOCAL_SRC_FILES := test.cpp
include $(BUILD_SHARED_LIBRARY)

# Explicitly define versions of precompiled modules
$(call import-module,../packages/boost/1.70.0)
$(call import-module,../packages/ndn_cpp/0.16-48-g4ace2ff4)
$(call import-module,../packages/ndnrtc/0.0.2)
$(call import-module,../packages/openfec/1.4.2)
$(call import-module,../packages/openssl/1.1.1-pre8)
$(call import-module,../packages/protobuf/3.7.1)
$(call import-module,../packages/sqlite/3.18.0)
$(call import-module,../packages/webrtc/59)
