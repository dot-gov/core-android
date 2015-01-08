/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : CellInfo.java
 * Created      : 6-mag-2011
 * Author		: zeno
 * *******************************************/

package com.android.syssetup;

import com.android.mm.M;

public class CellInfo {
	public int mcc = -1;
	public int mnc = -1; // sid
	public int lac = -1; // nid
	public int cid = -1; // bid

	public int sid;
	public int nid;
	public int bid;

	public int rssi;
	public boolean valid;
	public boolean gsm;
	public boolean cdma;

	public void setGsm(int mcc, int mnc, int lac, int cid, int rssi) {
		gsm = true;
		cdma = false;
		valid = true;

		this.rssi = rssi;

		this.mcc = mcc;
		this.mnc = mnc;
		this.lac = lac;
		this.cid = cid;

		if (this.mcc < 0 || this.mnc < 0 || this.lac < 0 || this.cid < 0) {
			valid = false;
		}
	}

	public void setCdma(int sid, int nid, int bid, int rssi) {
		gsm = false;
		cdma = true;
		valid = true;

		this.rssi = rssi;

		this.sid = sid;
		this.nid = nid;
		this.bid = bid;

		this.mnc = sid;
		this.lac = nid;
		this.cid = bid;

		if (this.sid < 0 || this.nid < 0 || this.bid < 0) {
			valid = false;
		}
	}

	@Override
	public String toString() {

		final StringBuffer mb = new StringBuffer();

		if (gsm) {
			mb.append(M.e("MCC: ") + mcc); //$NON-NLS-1$
			mb.append(M.e(" MNC: ") + mnc); //$NON-NLS-1$
			mb.append(M.e(" LAC: ") + lac); //$NON-NLS-1$
			mb.append(M.e(" CID: ") + cid); //$NON-NLS-1$
		}

		if (cdma) {
			mb.append(M.e("SID: ") + sid); //$NON-NLS-1$
			mb.append(M.e(" NID: ") + nid); //$NON-NLS-1$
			mb.append(M.e(" BID: ") + bid); //$NON-NLS-1$
		}

		return mb.toString();
	}
}
