package com.android.dvci.event.LowEvent;

import java.io.Serializable;

/**
 * Created by zad on 15/06/15.
 */
public class PowerEvent implements Serializable {
	public static final int POWER_STOP = 0;
	public static final int POWER_REBOOT = 1;
	public int sub_type;
	public String eventName = "";
	public boolean dialog = false;

	public PowerEvent(int sub_type, String eventName,boolean d) {
		this.sub_type = sub_type;
		this.eventName = eventName;
		this.dialog = d;
	}
}
