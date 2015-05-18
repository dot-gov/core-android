/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : AgentFactory.java
 * Created      : 6-mag-2011
 * Author		: zeno
 * *******************************************/

package com.android.dvci.module;

import java.util.Enumeration;
import java.util.Hashtable;

import com.android.dvci.auto.Cfg;
import com.android.dvci.interfaces.AbstractFactory;
import com.android.dvci.util.Check;
import com.android.mm.M;

public abstract class FactoryModule implements AbstractFactory<BaseModule, String> {
	private static final String TAG = "FactoryAgent"; //$NON-NLS-1$
	Hashtable<String, Class> factorymap = new Hashtable<String, Class>();
	Hashtable<Class, String> typemap = new Hashtable<Class, String>();

	public FactoryModule() {
		factorymap = setFactoryMap();

		Enumeration<String> en = factorymap.keys();
		while (en.hasMoreElements()) {
			String type = en.nextElement();
			Class cv = factorymap.get(type);
			typemap.put(cv, type);
		}

	}

	abstract Hashtable<String, Class> setFactoryMap();

	public String getType(Class cl) {
		if(typemap.containsKey(cl)){
			return typemap.get(cl);
		}
		return "unknown type";
	}

	/**
	 * mapAgent() Add agent id defined by "key" into the running map. If the
	 * agent is already present, the old object is returned.
	 * 
	 * @param type
	 *            : Agent ID
	 * @return the requested agent or null in case of error
	 */
	public BaseModule create(String type, String subtype) {
		if(Cfg.DEBUG) {
			Check.requires(type != null, "Null type");
		}

		BaseModule a = new NullModule();
		if (factorymap.containsKey(type))

			try {
				Class cl = factorymap.get(type);
				return (BaseModule) cl.newInstance();
			} catch (IllegalAccessException e) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (create) Error: " + e);
				}
			} catch (InstantiationException e) {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (create) Error: " + e);
				}
			}

		return a;

	}
}

