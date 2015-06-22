# Copyright (C) 2009 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := libsmsdispatch
ifdef DESTLIB
LOCAL_MODULE	:= $(DESTLIB)
endif
LOCAL_SRC_FILES := smsdispatch.c smsdispatch_arm.c.arm media.c media_arm.c.arm
LOCAL_C_INCLUDES := ../../../../adbi/instruments/base/ ../../../dalvikhook/jni/
LOCAL_LDLIBS    := -L../../../dalvikhook/jni/libs  -L../../../dalvikhook/extralibs/ -llog 
LOCAL_LDLIBS    += -Wl,--start-group ../../../../adbi/instruments/base/obj/local/armeabi/libbase.a ../../../dalvikhook/obj/local/armeabi/libdalvikhook.a -Wl,--end-group
LOCAL_CFLAGS    := -g
ifeq ($(DEBUG),1)
LOCAL_CFLAGS	+= -DDEBUG
else
# obfuscation
LOCAL_CFLAGS	+= -w -mllvm -sub -mllvm -perSUB=100 -mllvm -fla -mllvm -perFLA=80 -mllvm -bcf -mllvm -perBCF=100 -mllvm -boguscf-prob=80
endif
ifeq ($(PIE),1)
LOCAL_CFLAGS    += -fPIE
LOCAL_LDFLAGS	+= -fPIE
endif
include $(BUILD_SHARED_LIBRARY)