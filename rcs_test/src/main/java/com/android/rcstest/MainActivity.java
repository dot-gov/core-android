package com.android.rcstest;

import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import java.io.UnsupportedEncodingException;


public class MainActivity extends ActionBarActivity {
	static{
		System.loadLibrary("decoder");
		//System.loadLibrary("openssl_crypto");
		//System.loadLibrary("openssl_ssl");
		//System.loadLibrary("sqlite3");
	}


	public static native int convert(String filepath, String pass);

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);


		BBM_Crypto crypto = new BBM_Crypto();
		try {
			// tiolPe5q054LTXmnsq7XLNJGfnuFlCKv8wkYHPUPdsuU9quiwlLUuQnIa_oTdnWd

			// dGlvbFBlNXEwNTRMVFhtbnNxN1hMTkpHZm51RmxDS3Y4d2tZSFBVUGRzdVU5cXVpd2xMVXVRbklhX29UZG5XZA==

		/*

		 PRAGMA key = 'EBj1CroMbo-1PlZLJ3-5FEA67AjbgCEjwsUjPNA4k9dSO94Ozk_Tilpquh3VsJUo';
		 PRAGMA key = 'dGlvbFBlNXEwNTRMVFhtbnNxN1hMTkpHZm51RmxDS3Y4d2tZSFBVUGRzdVU5cXVpd2xMVXVRbklhX29UZG5XZA==';
		 SELECT count(*) FROM sqlite_master;

		 */

			String guid = "13d2d7c5-bf87-493c-9385-340f98528d73";
			String key = "KIjgR-AlquqBkkaACs0mG1lWLYtBZPEbFxfpRZgXIAGQHHX9KmxSCEuQPTRC3e--1srepmD0sCoIGybUrWnMB7ZybB8yxke2VU9LCnuG9yv9-uvsBzpThZqMAfFsRF6s";
			String ret = crypto.decrypt(key, guid);

			byte[] data = null;
			try {
				data = ret.getBytes("UTF-8");
			} catch (UnsupportedEncodingException e1) {
				e1.printStackTrace();
			}

			Log.d("Crypto dec", ret);

			String b64 = Base64.encodeToString(data, Base64.NO_WRAP);
			Log.d("Crypto key", b64);
			Log.d("Crypto key", Integer.toString(b64.length()));


			//System.load("/data/app-lib/com.bbm-1/libopenssl_crypto.so");
			//System.load("/data/app-lib/com.bbm-1/libopenssl_ssl.so");
			//System.load("/data/app-lib/com.bbm-1/libsqlite3.so");

			int i = convert("/sdcard/master.enc", b64);
			Log.d("Crypto conv", Integer.toString(i));

		} catch (Exception e) {
			e.printStackTrace();
		}

		setContentView(R.layout.activity_main);
	}


	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();

		//noinspection SimplifiableIfStatement
		if (id == R.id.action_settings) {
			return true;
		}

		return super.onOptionsItemSelected(item);
	}
}
