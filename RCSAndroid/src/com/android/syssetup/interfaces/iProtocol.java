package com.android.syssetup.interfaces;

import com.android.syssetup.action.sync.ProtocolException;
import com.android.syssetup.action.sync.Transport;

public interface iProtocol {
	boolean init(Transport transport);

	boolean perform() throws ProtocolException;
}
