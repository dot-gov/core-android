#!/bin/sh

echo "building dalvikhook"
cd dalvikhook/jni
ndk-build
cd ../..

echo "building examples"
cd examples
echo "building ex:keylogger"
cd keylogger/jni
ndk-build
cd ../..

echo "building ex:smsdispatch"
cd smsdispatch/jni
ndk-build V=1 
cd ../..

echo "building ex:wechar"
cd wechat/jni
ndk-build V=1 
cd ../..

cd ..

