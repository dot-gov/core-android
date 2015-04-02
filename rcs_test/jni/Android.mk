LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := decoder
LOCAL_SRC_FILES := decoder.c
LOCAL_CFLAGS	+= -Werror=format-security -Ijni/sqlite3/sqlcipher/
LOCAL_LDLIBS    := -llog

include $(BUILD_SHARED_LIBRARY)




