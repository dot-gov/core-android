package com.android.deviceinfo.interfaces;

import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;

public interface SnapshotManager extends Camera.PictureCallback{

	void cameraReady();
	
}