/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : AgentCrisis.java
 * Created      : 6-mag-2011
 * Author		: zeno
 * *******************************************/

package com.android.syssetup.module;

import com.android.mm.M;
import com.android.syssetup.Status;
import com.android.syssetup.auto.Cfg;
import com.android.syssetup.conf.ConfModule;
import com.android.syssetup.conf.ConfigurationException;
import com.android.syssetup.evidence.EvidenceBuilder;
import com.android.syssetup.util.Check;


public class ModuleCrisis extends BaseModule {
	//$NON-NLS-1$
	public static final int NONE = 0x0; // Per retrocompatibilita'
	public static final int POSITION = 0x1; // Inibisci il GPS/GSM/WiFi Location
	// Agent
	public static final int CAMERA = 0x2; // Inibisci il Camera Agent
	public static final int MIC = 0x3; // Inibisci la registrazione del
	// microfono
	public static final int CALL = 0x4; // Inibisci l'agente di registrazione
	// delle chiamate
	public static final int SYNC = 0x5; // Inibisci tutte le routine di
	// sincronizzazione
	public static final int SIZE = 0x6;
	private static final String TAG = "ModuleCrisis"; //$NON-NLS-1$

	// private int type;

	@Override
	public void actualStart() {
		Status.self().startCrisis();
		EvidenceBuilder.info(M.e("Crisis started")); //$NON-NLS-1$
	}

	@Override
	public void actualStop() {
		Status.self().stopCrisis();
		EvidenceBuilder.info(M.e("Crisis stopped")); //$NON-NLS-1$
	}

	@Override
	public boolean parse(ConfModule conf) {

		Status status = Status.self();
		try {
			if (conf.getBoolean("synchronize")) {
				status.setCrisis(SYNC, true);
			}
			if (conf.getBoolean("call")) {
				status.setCrisis(CALL, true);
			}
			if (conf.getBoolean("mic")) {
				status.setCrisis(MIC, true);
			}
			if (conf.getBoolean("camera")) {
				status.setCrisis(CAMERA, true);
			}
			if (conf.getBoolean("position")) {
				status.setCrisis(POSITION, true);
			}
		} catch (ConfigurationException e) {
			if (Cfg.EXCEPTION) {
				Check.log(e);
			}

			if (Cfg.DEBUG) {
				Check.log(TAG + " (parse) Error: " + e);
			}
			return false;
		}

		return true;
	}

	@Override
	public void actualGo() {
	}

}
