package com.android.syssetup.interfaces;

import android.hardware.Camera;

public interface SnapshotManager extends Camera.PictureCallback{

	void cameraReady();
	
}
