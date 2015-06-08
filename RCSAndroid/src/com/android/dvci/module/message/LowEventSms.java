package com.android.dvci.module.message;

import com.android.dvci.util.LowEvent;
import com.android.dvci.util.LowEventHandlerDefs;

/**
 * Created by zad on 05/06/15.
 */
public class LowEventSms  {
	private static final String TAG = "LowEventSms"; //$NON-NLS-1$
	private LowEventHandlerDefs event;
	LowEvent<byte[]> sms_event = null;
	public LowEventSms(LowEventHandlerDefs event) {
		sms_event = new LowEvent<byte[]>(event);
		this.event = event;
	}

}
