#!/bin/sh

cd hijack/jni
ndk-build
cd ../..

cd instruments
cd base/jni
ndk-build
cd ../..


cd binder_call/jni
ndk-build
cd ../..


cd ..

