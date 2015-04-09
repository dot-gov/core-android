default:
	cd jni && ndk-build
	cd -

clean:
	cd jni && ndk-build clean
	cd -

push:
	adb push libs/armeabi/libsysutils.so /data/local/tmp && adb shell chmod 777 /data/local/tmp/libsysutils.so
	@echo "substitute your target app to wrap.com.tencent.mm, i.e. wrap.<target_app>"
	@echo adb shell su -c "setprop wrap.com.tencent.mm LD_PRELOAD=/data/local/tmp/libsysutils.so"
