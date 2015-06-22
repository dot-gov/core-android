default:
	cd jni && ndk-build NDK_TOOLCHAIN_VERSION=4.8
	cd -

clean:
	cd jni && ndk-build clean
	cd -

push:
	adb push libs/armeabi/libtalk.so /data/local/tmp/libtalk.so && adb shell chmod 777 /data/local/tmp/libtalk.so
	#adb push libs/armeabi/libikey.so /data/local/tmp/ && adb shell chmod 777 /data/local/tmp/libikey.so
	@echo "substitute your target app to wrap.com.tencent.mm, i.e. wrap.<target_app>"
	@echo adb shell su -c "setprop wrap.com.tencent.mm LD_PRELOAD=/data/local/tmp/libtalk.so"
	@echo "OR if you are doing injection via hijack:"
	@echo "hijjj.sh <process2inject> <library2inj>"
