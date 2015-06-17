package com.android.dvci.event.LowEvent;

import java.io.Serializable;
import java.lang.ref.WeakReference;

/**
 * Created by zad on 15/06/15.
 */
public class AudioEvent implements Serializable {
	public static final int AUDIO_REC_STOP = 0;
	public static final int AUDIO_REC_INIT = 1;
	public static final int AUDIO_REC_START = 2;
	public int sub_type;
	public String className = "";

	public AudioEvent(int sub_type, String className) {
		this.sub_type = sub_type;
		this.className = className;
	}
}
