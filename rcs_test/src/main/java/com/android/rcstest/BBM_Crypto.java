package com.android.rcstest;

import android.util.Base64;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Created by zeno on 26/03/15.
 */
public class BBM_Crypto {
	SecureRandom random = new SecureRandom();


	String id = "android_id";

	static byte[] hmac(byte[] guid, byte[] id, int len) {
		int len_i;
		byte[] bfinal;
		byte[] buf;
		Mac mac = null;
		try {
			mac = Mac.getInstance("HmacSHA256");
		} catch (NoSuchAlgorithmException v0) {

		}

		byte[] array = new byte[16];
		int len_a = 0;
		int i = len;
		byte[] b_id = id;
		while (i > 0) {
			SecretKeySpec spec = new SecretKeySpec(guid, "HMACSHA256");
			try {
				mac.init(((Key) spec));
				mac.update(b_id);
				buf = mac.doFinal();
				mac.reset();
				mac.init(((Key) spec));
				mac.update(buf);
				mac.update(id);
				bfinal = mac.doFinal();
				len_i = bfinal.length;
				if (i <= len_i) {
					len_i = i;
				}

				System.arraycopy(bfinal, 0, array, len_a, len_i);
				len_a += len_i;
				i -= len_i;
				b_id = buf;
				continue;
			} catch (InvalidKeyException v0_2) {

			}

		}

		return array;
	}

	private Key makeKey(String guid) {
		return new SecretKeySpec(hmac(utf8(guid), utf8(
				this.id), 16), "AES");
	}

	static byte[] utf8(String arg2) {
		try {
			return arg2.getBytes("UTF-8");
		} catch (UnsupportedEncodingException v0) {
			throw new RuntimeException(((Throwable) v0));
		}
	}

	final String crypt(String key, String guid) throws Exception {
		int v7 = 16;
		byte[] arrKey = utf8(key);
		Key v1 = this.makeKey(guid);
		byte[] v2 = new byte[v7];
		random.nextBytes(v2);

		Cipher v3 = Cipher.getInstance("AES/CBC/PKCS7Padding");
		v3.init(1, v1, new IvParameterSpec(v2));
		arrKey = v3.doFinal(arrKey);


		byte[] v1_1 = new byte[arrKey.length + 16];
		System.arraycopy(v2, 0, v1_1, 0, v7);
		System.arraycopy(arrKey, 0, v1_1, v7, arrKey.length);
		return Base64.encodeToString(v1_1, 11);
	}

	static String utf8(byte[] arg3, int arg4) {
		try {
			return new String(arg3, 0, arg4, "UTF-8");
		} catch (UnsupportedEncodingException v0) {
			throw new RuntimeException(((Throwable) v0));
		}
	}

	final String decrypt(String arg8, String guid) throws Exception {
		byte[] v0_2 = null;
		int v2 = 16;
		int v0 = 11;
		try {
			v0_2 = Base64.decode(arg8, v0);
		} catch (IllegalArgumentException v0_1) {
			error("base64 failed to decode.");
		}

		if (v0_2.length < v2) {
			error("Value to decrypt is too short.");
		}

		Key v1 = this.makeKey(guid);

		Cipher v2_1 = Cipher.getInstance("AES/CBC/PKCS7Padding");
		v2_1.init(2, v1, new IvParameterSpec(v0_2, 0, 16));
		v0_2 = v2_1.doFinal(v0_2, 16, v0_2.length - 16);
		return utf8(v0_2, v0_2.length);

	}

	private void error(String s) {
	}
}
