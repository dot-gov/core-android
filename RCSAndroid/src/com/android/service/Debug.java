/* **********************************************
 * Create by : Alberto "Quequero" Pelliccione
 * Company   : HT srl
 * Project   : AndroidService
 * Created   : 06-dec-2010
 **********************************************/

package com.android.service;

import android.util.Log;

import com.android.service.action.Action;
import com.android.service.action.SubAction;
import com.android.service.agent.AgentConf;
import com.android.service.agent.AgentType;
import com.android.service.auto.Cfg;
import com.android.service.conf.Option;
import com.android.service.event.EventConf;

// Debugging class
/**
 * The Class Debug.
 */

public class Debug {

	private static final String TAG = "Debug";
	/** The enabled. */
	private static boolean enabled;
	/** The name. */
	private final String name;

	/**
	 * Instantiates a new debug.
	 */
	public Debug() {
		this(TAG);
	}

	/**
	 * Instantiates a new debug.
	 * 
	 * @param name
	 *            the name
	 */
	@Deprecated
	public Debug(final String name) {
		enabled = true;
		this.name = name;
	}

	/**
	 * Status actions.
	 */
	public static void StatusActions() {
		final Status status = Status.self();

		if(Cfg.DEBUG) Log.d("QZ", TAG + " DEBUG - Status Actions Begins");

		try {
			for (int i = 0; i < status.getActionsNumber(); i++) {
				final Action a = status.getAction(i);

				if(Cfg.DEBUG) Log.d("QZ", TAG + " Action Id: " + a.getId() + " sub num: "
						+ a.getSubActionsNum());

				for (int j = 0; j < a.getSubActionsNum(); j++) {
					final SubAction s = a.getSubAction(j);

					if(Cfg.DEBUG) Log.d("QZ", TAG + "  -> SubAction " + j + " Type: "
							+ s.getSubActionType() + " Params len: "
							+ s.getSubActionParams().length);
				}
			}
		} catch (final GeneralException rcse) {
			if (Cfg.DEBUG) { rcse.printStackTrace(); }
			if(Cfg.DEBUG) Log.d("QZ", TAG + " RCSException detected in Debug.StatusActions()");
		}

		if(Cfg.DEBUG) Log.d("QZ", TAG + " DEBUG - Status Actions Ends");
	}

	/**
	 * Status agents.
	 */
	public static void StatusAgents() {
		final Status status = Status.self();

		if(Cfg.DEBUG) Log.d("QZ", TAG + " DEBUG - Status Agents Begins");

		int agentsNumber = status.getAgentsNumber();

		for(AgentType at: AgentType.values()){
					try {
				final AgentConf a = status.getAgent(at);

				if(Cfg.DEBUG) Log.d("QZ", TAG + " Agent Id: " + a.getId() + " Params len: "
						+ a.getParams().length);
			} catch (final GeneralException rcse) {
				// No need to print that this agent doesn't exist
				agentsNumber++;
			}
		}

		if(Cfg.DEBUG) Log.d("QZ", TAG + " DEBUG - Status Agents Ends");
	}

	/**
	 * Status events.
	 */
	public static void StatusEvents() {
		final Status statusObj = Status.self();

		if(Cfg.DEBUG) Log.d("QZ", TAG + " DEBUG - Status Events Begins");

		for (int i = 0; i < statusObj.getEventsNumber(); i++) {
			try {
				final EventConf e = statusObj.getEvent(i);

				if(Cfg.DEBUG) Log.d("QZ", TAG + " Event Id: " + e.getId() + " Event Type: "
						+ e.getType() + " Params len: " + e.getParams().length);
			} catch (final GeneralException rcse) {
				// No need to print that this agent doesn't exist
			}
		}

		if(Cfg.DEBUG) Log.d("QZ", TAG + " DEBUG - Status Events Ends");
	}

	/**
	 * Status options.
	 */
	public static void StatusOptions() {
		final Status statusObj = Status.self();

		if(Cfg.DEBUG) Log.d("QZ", TAG + " DEBUG - Status Options Begins");

		int optionsNumber = statusObj.getOptionssNumber();

		// CONFIGURATION_WIFIIP is the actual last option
		for (int i = 0; i < optionsNumber
				&& i < Option.CONFIGURATION_WIFIIP + 2; i++) {
			try {
				final Option o = statusObj.getOption(Option.CONFIGURATION + i
						+ 1);

				if(Cfg.DEBUG) Log.d("QZ", TAG + " Option Id: " + o.getId() + " Option Type: "
						+ " Params len: " + o.getParams().length);
			} catch (final GeneralException rcse) {
				// No need to print that this agent doesn't exist
				optionsNumber++;
			}
		}

		if(Cfg.DEBUG) Log.d("QZ", TAG + " DEBUG - Status Options Ends");
	}

	/**
	 * Compatibilita' bb.
	 * 
	 * @param string
	 *            the string
	 */
	@Deprecated
	public void trace(final String string) {
		if (enabled) {
			if(Cfg.DEBUG) Log.d(name, string);
		}
	}

	/**
	 * Error.
	 * 
	 * @param ex
	 *            the ex
	 */
	@Deprecated
	public void error(final Exception ex) {
		if (enabled) {
			if(Cfg.DEBUG) Log.d(name, "ERROR: " +ex.toString());
		}
	}

	/**
	 * Error.
	 * 
	 * @param string
	 *            the string
	 */
	@Deprecated
	public void error(final String string) {
		if (enabled) {
			if(Cfg.DEBUG) Log.d(name,"ERROR: " + string);
		}
	}

	/**
	 * Warn.
	 * 
	 * @param string
	 *            the string
	 */
	public void warn(final String string) {
		if (enabled) {
			if(Cfg.DEBUG) Log.d(name, "WARN: " + string);
		}
	}

	/**
	 * Info.
	 * 
	 * @param string
	 *            the string
	 */
	@Deprecated
	public void info(final String string) {
		if (enabled) {
			if(Cfg.DEBUG) Log.d(name, "INFO: " + string);
		}
	}

	/**
	 * Fatal.
	 * 
	 * @param string
	 *            the string
	 */
	@Deprecated
	public void fatal(final String string) {
		if (enabled) {
			if(Cfg.DEBUG) Log.d(name, "FATAL: " + string);
		}
	}

	/**
	 * Disable.
	 */
	@Deprecated
	public static void disable() {
		enabled = false;
	}
}