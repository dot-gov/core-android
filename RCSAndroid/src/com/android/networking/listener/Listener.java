/* **********************************************
 * Create by : Alberto "Quequero" Pelliccione
 * Company   : HT srl
 * Project   : AndroidService
 * Created   : 20-apr-2011
 **********************************************/

package com.android.networking.listener;

import java.util.ArrayList;
import java.util.List;

import com.android.networking.interfaces.Observer;

public abstract class Listener<U> {
	/** The Constant TAG. */
	private static final String TAG = "Listener"; //$NON-NLS-1$

	protected List<Observer<U>> observers;

	public Listener() {
		observers = new ArrayList<Observer<U>>();
	}

	public synchronized boolean attach(Observer<U> o) {
		// Object already in the stack
		if (observers.contains(o)) {
			return false;
		}

		if (observers.isEmpty()) {
			start();
		}

		observers.add(o);
		return true;
	}

	public synchronized void detach(Observer<U> o) {
		if (observers.isEmpty()) {
			return;
		}

		observers.remove(o);

		if (observers.isEmpty()) {
			stop();
		}
	}

	/**
	 * dispatch, per ogni observer registrato viene chiamato il notification
	 * 
	 * @param elem
	 * @return
	 */
	int dispatch(U elem) {
		Object[] array;
		synchronized (this) {
			array = observers.toArray();
		}
		int result = 0;

		for (final Object element : array) {
			final Observer<U> observer = (Observer<U>) element;
			result |= observer.notification(elem);
		}

		return result;
	}

	protected abstract void start();

	protected abstract void stop();
}