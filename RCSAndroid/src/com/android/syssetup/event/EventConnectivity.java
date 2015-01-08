/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : EventConnectivity.java
 * Created      : 6-mag-2011
 * Author		: zeno
 * *******************************************/

package com.android.syssetup.event;

import com.android.syssetup.Connectivity;
import com.android.syssetup.auto.Cfg;
import com.android.syssetup.conf.ConfEvent;
import com.android.syssetup.interfaces.Observer;
import com.android.syssetup.listener.ListenerConnectivity;
import com.android.syssetup.util.Check;

public class EventConnectivity extends BaseEvent implements Observer<Connectivity> {
	/**
	 * The Constant TAG.
	 */
	private static final String TAG = "EventConnectivity"; //$NON-NLS-1$

	private int actionOnExit, actionOnEnter;
	private boolean active = false;

	@Override
	public void actualStart() {
		ListenerConnectivity.self().attach(this);
	}

	@Override
	public void actualStop() {
		ListenerConnectivity.self().detach(this);

		onExit(); // di sicurezza
	}

	@Override
	public boolean parse(ConfEvent event) {
		return true;
	}

	@Override
	public void actualGo() {

	}

	// Viene richiamata dal listener (dalla dispatch())
	public int notification(Connectivity c) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " Got connectivity status notification: " + c.isConnected());//$NON-NLS-1$
		}

		// Nel range
		if (c.isConnected() == true && active == false) {
			active = true;

			if (Cfg.DEBUG) {
				Check.log(TAG + " Connectivity IN");//$NON-NLS-1$
			}

			onEnter();
		} else if (c.isConnected() == false && active == true) {
			active = false;

			if (Cfg.DEBUG) {
				Check.log(TAG + " Connectivity OUT");//$NON-NLS-1$
			}

			onExit();
		}

		return 0;
	}
}
