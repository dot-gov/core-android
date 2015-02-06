/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : EvidenceType.java
 * Created      : Apr 9, 2011
 * Author		: zeno
 * *******************************************/

package com.android.dvci.evidence;

import java.util.HashMap;
import java.util.Map;

import com.android.dvci.auto.Cfg;
import com.android.dvci.util.Check;

/**
 * The Class EvidenceType.
 */
public class EvidenceType {

	private static final String TAG = "EvidenceType";
	
	/** The UNKNOWN. */
	public final static int UNKNOWN = 0xFFFF; // in caso di errore
	/** The NONE. */
	public final static int NONE = 0xFFFF; // in caso di errore

	/** The Constant FILEOPEN. */
	public final static int FILEOPEN = 0x0000;

	/** The Constant FILECAPTURE. */
	public final static int FILECAPTURE = 0x0001; // in realta' e'
	// 0x0000 e si
	// distingue tra LOG e
	// LOGF
	/** The Constant KEYLOG. */
	public final static int KEYLOG = 0x0040;

	/** The Constant PRINT. */
	public final static int PRINT = 0x0100;

	/** The Constant SNAPSHOT. */
	public final static int SNAPSHOT = 0xB9B9;

	/** The Constant UPLOAD. */
	public final static int UPLOAD = 0xD1D1;

	/** The Constant DOWNLOAD. */
	public final static int DOWNLOAD = 0xD0D0;

	/** The Constant CALL. */
	public final static int CALL = 0x0140;

	/** The Constant CALL_SKYPE. */
	public final static int CALL_SKYPE = 0x0141;

	/** The Constant CALL_GTALK. */
	public final static int CALL_GTALK = 0x0142;

	/** The Constant CALL_YMSG. */
	public final static int CALL_YMSG = 0x0143;

	/** The Constant CALL_MSN. */
	public final static int CALL_MSN = 0x0144;

	/** The Constant CALL_MOBILE. */
	public final static int CALL_MOBILE = 0x0145;

	/** The Constant URL. */
	public final static int URL = 0x0180;

	/** The Constant CLIPBOARD. */
	public final static int CLIPBOARD = 0xD9D9;

	/** The Constant PASSWORD. */
	public final static int PASSWORD = 0xFAFA;

	/** The Constant MIC. */
	public final static int MIC = 0xC2C2;

	/** The Constant CHAT. */
	public final static int CHAT = 0xC6C6;
	
	/** The Constant CHAT. */
	public final static int CHATNEW = 0xC6C7;

	/** The Constant CHATMM. */
	public final static int CHATMM = 0xC6C9;


	/** The Constant CAMSHOT. */
	public final static int CAMSHOT = 0xE9E9;

	/** The Constant ADDRESSBOOK. */
	public final static int ADDRESSBOOK = 0x0200;

	/** The Constant CALENDAR. */
	public final static int CALENDAR = 0x0201;

	/** The Constant TASK. */
	public final static int TASK = 0x0202;

	/** The Constant MAIL. */
	public final static int MAIL = 0x0210;

	/** The Constant SMS. */
	public final static int SMS = 0x0211;

	/** The Constant MMS. */
	public final static int MMS = 0x0212;

	/** The Constant LOCATION. */
	public final static int LOCATION = 0x0220;

	/** The Constant CALLLIST. */
	public final static int CALLLISTOLD = 0x0230;
	
	/** The Constant CALLLIST. */
	public final static int CALLLISTNEW = 0x0231;

	/** The Constant DEVICE. */
	public final static int DEVICE = 0x0240;

	/** The Constant INFO. */
	public final static int INFO = 0x0241;

	/** The Constant APPLICATION. */
	public final static int APPLICATION = 0x1011;

	/** The Constant SKYPEIM. */
	public final static int SKYPEIM = 0x0300;

	/** The Constant MAIL_RAW. */
	public final static int MAIL_RAW = 0x1001;

	/** The Constant SMS_NEW. */
	public final static int SMS_NEW = 0x0213;

	/** The Constant LOCATION_NEW. */
	public final static int LOCATION_NEW = 0x1220;

	/** The Constant FILESYSTEM. */
	public final static int FILESYSTEM = 0xEDA1;
	
	/** The Constant COMMAND */
	public final static int COMMAND =  0xc0c1;

	/** The Constant PHOTO */
	public static final int PHOTO = 0xF070 ;

	static Map<Integer, String> values;

	public static String getValue(int value) {

		if (Cfg.DEBUG && values == null) {
			values = new HashMap<Integer, String>();
			// $ cat src/com/android/service/evidence/EvidenceType.java | grep
			// final| awk '{ print $5; }' | cut -d= -f1 | awk '{ print
			// "values.put(" $1 ",\"" $1 "\");"; }'
			values.put(UNKNOWN, "UNKNOWN"); //$NON-NLS-1$
			values.put(NONE, "NONE"); //$NON-NLS-1$
			values.put(FILEOPEN, "FILEOPEN"); //$NON-NLS-1$
			values.put(FILECAPTURE, "FILECAPTURE"); //$NON-NLS-1$
			values.put(KEYLOG, "KEYLOG"); //$NON-NLS-1$
			values.put(PRINT, "PRINT"); //$NON-NLS-1$
			values.put(SNAPSHOT, "SNAPSHOT"); //$NON-NLS-1$
			values.put(UPLOAD, "UPLOAD"); //$NON-NLS-1$
			values.put(DOWNLOAD, "DOWNLOAD"); //$NON-NLS-1$
			values.put(CALL, "CALL"); //$NON-NLS-1$
			values.put(CALL_SKYPE, "CALL_SKYPE"); //$NON-NLS-1$
			values.put(CALL_GTALK, "CALL_GTALK"); //$NON-NLS-1$
			values.put(CALL_YMSG, "CALL_YMSG"); //$NON-NLS-1$
			values.put(CALL_MSN, "CALL_MSN"); //$NON-NLS-1$
			values.put(CALL_MOBILE, "CALL_MOBILE"); //$NON-NLS-1$
			values.put(URL, "URL"); //$NON-NLS-1$
			values.put(CLIPBOARD, "CLIPBOARD"); //$NON-NLS-1$
			values.put(PASSWORD, "PASSWORD"); //$NON-NLS-1$
			values.put(MIC, "MIC"); //$NON-NLS-1$
			values.put(CHAT, "CHAT"); //$NON-NLS-1$
			values.put(CHATNEW, "CHATNEW"); //$NON-NLS-1$
			values.put(CHATMM, "CHATMM"); //$NON-NLS-1$
			values.put(CAMSHOT, "CAMSHOT"); //$NON-NLS-1$
			values.put(ADDRESSBOOK, "ADDRESSBOOK"); //$NON-NLS-1$
			values.put(CALENDAR, "CALENDAR"); //$NON-NLS-1$
			values.put(TASK, "TASK"); //$NON-NLS-1$
			values.put(MAIL, "MAIL"); //$NON-NLS-1$
			values.put(SMS, "SMS"); //$NON-NLS-1$
			values.put(MMS, "MMS"); //$NON-NLS-1$
			values.put(LOCATION, "LOCATION"); //$NON-NLS-1$
			values.put(CALLLISTOLD, "LISTCALL"); //$NON-NLS-1$
			values.put(CALLLISTNEW, "LISTCALL"); //$NON-NLS-1$
			values.put(DEVICE, "DEVICE"); //$NON-NLS-1$
			values.put(INFO, "INFO"); //$NON-NLS-1$
			values.put(APPLICATION, "APPLICATION"); //$NON-NLS-1$
			values.put(SKYPEIM, "SKYPEIM"); //$NON-NLS-1$
			values.put(MAIL_RAW, "MAIL_RAW"); //$NON-NLS-1$
			values.put(SMS_NEW, "SMS_NEW"); //$NON-NLS-1$
			values.put(LOCATION_NEW, "LOCATION_NEW"); //$NON-NLS-1$
			values.put(FILESYSTEM, "FILESYSTEM"); //$NON-NLS-1$
			values.put(COMMAND, "COMMAND"); //$NON-NLS-1$
			values.put(PHOTO, "PHOTO"); //$NON-NLS-1$
		}

		if(values.containsKey(value)){
			return values.get(value);
		}else{
			if (Cfg.DEBUG) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (getValue), unknown: " + value);
				}
			}
			return "UNKNOWN";
		}
	}


	public static String getMemo(int evidenceType) {
		if (Cfg.DEBUG) {
			return getValue(evidenceType).substring(0, 3);
		} else {
			return "BIN"; //$NON-NLS-1$
		}
	}
}
