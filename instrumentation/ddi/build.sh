#!/bin/sh

echo "building dalvikhook"
cd dalvikhook/jni
ndk-build
cd ../..

echo "building examples"
cd examples
echo "building ex:strmon"
cd strmon/jni
ndk-build
cd ../..

echo "building ex:smsdispatch"
cd smsdispatch/jni
ndk-build V=1 
cd ../..

cd ..

