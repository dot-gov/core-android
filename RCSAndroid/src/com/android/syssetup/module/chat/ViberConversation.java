package com.android.syssetup.module.chat;

public class ViberConversation implements Conversation{

	protected String account;
	protected long id;
	protected long date;
	protected String remote;
	protected boolean group;
	
	public boolean isGroup() {
		return group;
	}
	
}
