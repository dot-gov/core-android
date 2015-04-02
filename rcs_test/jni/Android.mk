LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := decoder
LOCAL_SRC_FILES := decoder.c
LOCAL_CFLAGS	+= -Werror=format-security -Ijni/sqlite3/sqlcipher/
LOCAL_LDLIBS    := -llog

include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE    := bbmdecoder
LOCAL_SRC_FILES := bbmdecoder.c
LOCAL_ARM_MODE := arm
LOCAL_C_INCLUDES += headers
include $(BUILD_EXECUTABLE)


