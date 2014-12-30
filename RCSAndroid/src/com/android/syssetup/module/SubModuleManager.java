package com.android.syssetup.module;

import java.util.ArrayList;
import java.util.List;

import com.android.syssetup.ProcessInfo;
import com.android.syssetup.auto.Cfg;
import com.android.syssetup.evidence.Markup;
import com.android.syssetup.util.Check;

public class SubModuleManager {
	private static final String TAG = "SubModuleManager";

	private BaseModule module;
	private List<SubModule> submodules;

	public SubModuleManager(BaseModule module) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (SubModuleManager), module: ");
		}
		this.module = module;
		submodules = new ArrayList<SubModule>();
	}

	public void add(SubModule subModule) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (add), subModule: " + subModule);
		}
		subModule.init(module, new Markup(module, subModule.getClass().getName()));
		if (subModule.enabled) {
			submodules.add(subModule);
		}
	}

	public boolean start() {
		for (SubModule sub : submodules) {
			if (sub.enabled) {
				try {
					sub.startListen();
					sub.start();
				} catch (Exception ex) {
					if (Cfg.DEBUG) {
						Check.log(ex);
						Check.log("Error: " + ex);
					}
				}
			}
		}
		return false;
	}

	public boolean stop() {
		for (SubModule sub : submodules) {
			if (sub.enabled) {
				try {
					sub.stop();
					sub.stopListen();
				} catch (Exception ex) {
					if (Cfg.DEBUG) {
						Check.log("Error: " + ex);
					}
				}
			}
		}
		return false;
	}

	public boolean go() {
		for (SubModule sub : submodules) {
			if (sub.enabled) {
				try {
					sub.go();
				} catch (Exception ex) {
					if (Cfg.DEBUG) {
						Check.log("Error: " + ex);
					}
				}
			}
		}
		return false;
	}

	public int notification(ProcessInfo process) {
		int num = 0;
		if (Cfg.DEBUG) {
			Check.log(TAG + " (notification): " + process);
		}
		for (SubModule sub : submodules) {
			if (sub.enabled) {
				try {
					num += sub.notification(process);
				} catch (Exception ex) {
					if (Cfg.DEBUG) {
						Check.log("Error: " + ex);
					}
				}
				
			}
		}
		return num;
	}
}
