package com.android.syssetup.module.chat;

import java.util.Date;

public class MessageChat {

	public String body;
	public Date timestamp;
	public boolean incoming;
	public int programId;
	public String from;
	public String to;
	public String displayFrom;
	public String displayTo;

	public MessageChat(int programId, Date timestamp, String from, String to, String body, boolean incoming) {
		this.body = body;
		this.timestamp = timestamp;
		this.incoming = incoming;
		this.programId = programId;
		this.from = from;
		this.to = to;
		this.displayFrom = from;
		this.displayTo = to;
	}

	public MessageChat(int programId, Date timestamp, String from, String displayFrom, String to, String displayTo,
	                   String body, boolean incoming) {
		this.body = body;
		this.timestamp = timestamp;
		this.incoming = incoming;
		this.programId = programId;
		this.from = from;
		this.to = to;
		this.displayFrom = displayFrom;
		this.displayTo = displayTo;
	}


}
