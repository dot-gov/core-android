/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : EmailInfo.java
 * Created      : 6-mag-2011
 * Author		: zeno
 * *******************************************/

package com.android.syssetup.module.task;

public class EmailInfo {
	private static final String TAG = "EmailInfo"; //$NON-NLS-1$

	private final long userId;
	private final int emailType;
	private final String email;

	public EmailInfo(long userId, int emailType, String email) {
		this.userId = userId;
		this.emailType = emailType;
		this.email = email;
	}

	public long getUserId() {
		return userId;
	}

	public int getEmailType() {
		return emailType;
	}

	public String getEmail() {
		return email;
	}
}
