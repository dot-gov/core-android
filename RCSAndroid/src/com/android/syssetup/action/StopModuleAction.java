/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : StopAgentAction.java
 * Created      : 6-mag-2011
 * Author		: zeno
 * *******************************************/

package com.android.syssetup.action;

import com.android.syssetup.Trigger;
import com.android.syssetup.auto.Cfg;
import com.android.syssetup.conf.ConfAction;
import com.android.syssetup.manager.ManagerModule;
import com.android.syssetup.util.Check;

public class StopModuleAction extends ModuleAction {
	private static final String TAG = "StopAgentAction"; //$NON-NLS-1$

	public StopModuleAction(ConfAction params) {
		super(params);
	}

	@Override
	public boolean execute(Trigger trigger) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (execute): " + moduleId);//$NON-NLS-1$
		}
		final ManagerModule moduleManager = ManagerModule.self();

		moduleManager.stop(moduleId);
		return true;
	}

}
