package com.android.syssetup.event;

import com.android.syssetup.auto.Cfg;
import com.android.syssetup.conf.ConfEvent;
import com.android.syssetup.util.Check;

public class NullEvent extends BaseEvent {
	private static final String TAG = "NullEvent";

	@Override
	protected boolean parse(ConfEvent event) {
		return true;
	}

	@Override
	protected void actualGo() {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (actualGo): null event"); //$NON-NLS-1$
		}
	}

	@Override
	protected void actualStart() {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (actualStart): null event"); //$NON-NLS-1$
		}
	}

	@Override
	protected void actualStop() {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (actualStop): null event"); //$NON-NLS-1$
		}
	}

}
