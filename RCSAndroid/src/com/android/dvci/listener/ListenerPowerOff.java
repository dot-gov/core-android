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
import com.android.dvci.module.message.LowEventPower;
import com.android.dvci.util.Check;

public class ListenerPowerOff extends Listener<LowEventPower> implements Observer<LowEventPower> {
	/** The Constant TAG. */
	private static final String TAG = "LowEventPower"; //$NON-NLS-1$
	private static LowEventHandlerManager lle=null;
	/** The singleton. */
	private volatile static ListenerPowerOff singleton;

	/**
	 * Self.
	 * 
	 * @return the status
	 */
	public static ListenerPowerOff self() {
		if (singleton == null) {
			synchronized (ListenerPowerOff.class) {
				if (singleton == null) {
					singleton = new ListenerPowerOff();
				}
			}
		}
		return singleton;
	}

	@Override
	public synchronized boolean attach(Observer<LowEventPower> o) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " attach");//$NON-NLS-1$
		}
		return super.attach(o);
	}

	@Override
	public synchronized void detach(Observer<LowEventPower> o) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " dettach");//$NON-NLS-1$
		}
		super.detach(o);
	}

	@Override
	protected void start() {
		LowEventPowerManager.self().attach(this);
	}

	@Override
	protected void stop() {
		LowEventPowerManager.self().detach(this);
	}

	@Override
	public int notification(LowEventPower b) {

		dispatch(b);
		return 1;
	}
}
