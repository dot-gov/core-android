package com.android.syssetup.auto;

public class Cfg {
	//ATTENZIONE, NON CAMBIARE A MANO LA VARIABILE DEBUG, VIENE RISCRITTA DA ANT

	public static final int BUILD_ID = 285;
	public static final String BUILD_TIMESTAMP = "20141230-151158";

	public static final int VERSION = 2014120803;
	public static final String OSVERSION = "v2";

	public static final boolean DEBUG = false;
	public static final boolean DEBUG_SPECIFIC = false; // false
	public static final boolean CHECK_ANTI_DEBUG = !false;
	public static final boolean EXCEPTION = false;
	//public static final boolean DEBUG = true;
	//public static final boolean EXCEPTION = true;
	public static boolean BLACKBERRY = true;
	public static final boolean GUI = false;

	public static boolean PERSISTENCE = false;
	public static final boolean FORCE_NO_PERSISTENCE = false;

	public static boolean DEMO = false; // false
	public static boolean DEMO_SILENT = false;

	public static final boolean FORCE_NODEMO = false;
	public static final boolean DEMO_INITSOUND = false;

	public static final boolean KEYS = false; // Se e' true vengono usate le chiavi hardcoded

	public static boolean FILE = true;
	public static final boolean MICFILE = false;
	public static final boolean TRACE = false; // enable Debug.startMethodTracing
	public static final boolean DEBUGKEYS = false; //uses fake keys if assets/rb.data not available
	public static final boolean STATISTICS = false; // enable statistics on crypto and on commands

	public static final boolean ENABLE_EXPERIMENTAL_MODULES = false; // enables viber modules
	public static final boolean ENABLE_WIFI_DISABLE = false;
	public static final boolean DELAY_SKYPE_CALL = false;

	public static final int PROTOCOL_CHUNK = 256 * 1024; // chunk size fot resume
	public static final int EV_QUEUE_LEN = 8;
	public static final int EV_BLOCK_SIZE = 256 * 1024;
	public static final int MAX_ASKED_SU = 3; // maximum number of su ask
	public static final long FREQUENT_NOTIFICATION_PERIOD = 5;

	public static final boolean USE_SD = true; // try to use sd if available
	public static final boolean FORCE_ROOT = false; // force root request

	public static final boolean ONE_MAIL = false; // retrieve only one mail
	public static final boolean ADJUST_OOM_ONCE = false;

	public static final boolean POWER_MANAGEMENT = true; // if true, tries to acquire power lock only when needed
	public static final boolean DEBUGANTI = false; // true to debug antidebug and antiemu deceptions
    public static final boolean DEBUGANTIEMU = false; // true to debug antiemu deceptions

	public static final String RANDOM = "9D2162FAC88BBE12";
	public static final String RNDMSG = "DB0256283FD33257";
	public static final String RNDDB = "C8C54078CB10349D";
	public static final String RNDLOG = "CECCCD0E74F07EBB";
}
