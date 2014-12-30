/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : WChar.java
 * Created      : Apr 9, 2011
 * Author		: zeno
 * *******************************************/

package com.android.syssetup.util;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import com.android.syssetup.auto.Cfg;
import com.android.mm.M;

// TODO: Auto-generated Javadoc
/**
 * The Class WChar.
 */
public final class WChar {
	/** The debug. */
	private static final String TAG = "WChar"; //$NON-NLS-1$

	/**
	 * Gets the bytes, non zero terminated
	 * 
	 * @param string
	 *            the string
	 * @return the bytes
	 */
	public static byte[] getBytes(final String string) {
		return getBytes(string, false);
		
	}

	/**
	 * Gets the bytes.
	 * 
	 * @param string
	 *            the string
	 * @param endzero
	 *            the endzero
	 * @return the bytes
	 */
	public static byte[] getBytes( String string, final boolean endzero) {
		byte[] encoded = null;
		if(string==null){
			string="";
		}
		try {
			//
			encoded = string.getBytes("UTF-16LE"); // UTF-16LE //$NON-NLS-1$
		} catch (final UnsupportedEncodingException e) {
			if (Cfg.EXCEPTION) {
				Check.log(e);
			}

			if (Cfg.DEBUG) {
				Check.log(TAG + " Error: UnsupportedEncodingException");//$NON-NLS-1$
			}

			try {
				// 34.0=UnicodeLittleUnmarked
				String encoding = M.e("UnicodeLittleUnmarked");
				encoded = string.getBytes(encoding);
			} catch (UnsupportedEncodingException e1) {
				if (Cfg.EXCEPTION) {
					Check.log(e1);
				}

				if (Cfg.DEBUG) {
					Check.log(TAG + " Error: UnsupportedEncodingException");//$NON-NLS-1$
				}
				return null;
			}
		}

		if (endzero) {
			final byte[] zeroencoded = new byte[encoded.length + 2];
			System.arraycopy(encoded, 0, zeroencoded, 0, encoded.length);
			encoded = zeroencoded;
		}

		return encoded;
	}

	/**
	 * Pascalize.
	 * 
	 * @param message
	 *            the message
	 * @return the byte[]
	 */
	public static byte[] pascalize(final byte[] message) {

		int len = message.length;

		if (len < 2 || message[message.length - 2] != 0 || message[message.length - 1] != 0) {
			len += 2; // aggiunge lo spazio per lo zero
		}

		final byte[] pascalzeroencoded = new byte[len + 4];
		System.arraycopy(ByteArray.intToByteArray(len), 0, pascalzeroencoded, 0, 4);
		System.arraycopy(message, 0, pascalzeroencoded, 4, message.length);
		if (Cfg.DEBUG) {
			Check.ensures(pascalzeroencoded[len - 1] == 0, "pascalize not null"); //$NON-NLS-1$
		}
		return pascalzeroencoded;
	}

	/**
	 * Gets the string.
	 * 
	 * @param message
	 *            the message
	 * @param endzero
	 *            the endzero
	 * @return the string
	 */
	public static String getString(final byte[] message, final boolean endzero) {
		return getString(message, 0, message.length, endzero);
	}

	/**
	 * Gets the string.
	 * 
	 * @param message
	 *            the message
	 * @param offset
	 *            the offset
	 * @param length
	 *            the length
	 * @param endzero
	 *            the endzero
	 * @return the string
	 */
	public static String getString(final byte[] message, final int offset, final int length, final boolean endzero) {
		String decoded = ""; //$NON-NLS-1$

		try {
			//
			decoded = new String(message, offset, length, "UTF-16LE"); // UTF-16LE //$NON-NLS-1$
		} catch (final UnsupportedEncodingException ex) {

			try {
				// 34.0=UnicodeLittleUnmarked
				decoded = new String(message, offset, length, M.e("UnicodeLittleUnmarked")); //$NON-NLS-1$

			} catch (final UnsupportedEncodingException e) {
				if (Cfg.EXCEPTION) {
					Check.log(e);
				}

				if (Cfg.DEBUG) {
					Check.log(TAG + " Error: UnsupportedEncodingException");//$NON-NLS-1$
				}
			}
		}

		if (endzero) {
			final int lastPos = decoded.indexOf('\0');
			if (lastPos > -1) {
				decoded = decoded.substring(0, lastPos);
			}
		}

		return decoded;
	}

	/**
	 * Instantiates a new w char.
	 */
	private WChar() {
	}

	/**
	 * Read pascal.
	 * 
	 * @param dataBuffer
	 *            the data buffer
	 * @return the string
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	public static String readPascal(final DataBuffer dataBuffer) throws IOException {
		final int len = dataBuffer.readInt();
		if (len < 0 || len > 65536) {
			return null;
		}

		final byte[] payload = new byte[len];
		dataBuffer.read(payload);
		return WChar.getString(payload, true);
	}

	/**
	 * Pascalize.
	 * 
	 * @param string
	 *            the string
	 * @return the byte[]
	 */
	public static byte[] pascalize(final String string) {
		return pascalize(WChar.getBytes(string));
	}

}
