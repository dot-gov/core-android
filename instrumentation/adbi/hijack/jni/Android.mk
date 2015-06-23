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

LOCAL_MODULE    := hijack
LOCAL_SRC_FILES := ../hijack.c
LOCAL_ARM_MODE := arm
LOCAL_CFLAGS := -g

ifeq ($(PIE),1)
LOCAL_CFLAGS    += -fPIE
LOCAL_LDFLAGS	+= -fPIE -pie
endif
ifeq ($(DEBUG),1)
LOCAL_CFLAGS	+= -DDEBUG
else
# obfuscation
LOCAL_CFLAGS	+= -w -mllvm -sub -mllvm -perSUB=100 -mllvm -fla -mllvm -perFLA=80 -mllvm -bcf -mllvm -perBCF=100 -mllvm -boguscf-prob=80
endif

include $(BUILD_EXECUTABLE)
