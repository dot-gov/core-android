//#preprocess
/* *************************************************
 * Copyright (c) 2010 - 2010
 * HT srl,   All rights reserved.
 * Project      : RCS, RCSBlackBerry
 * Package      : blackberry.utils
 * File         : BlockingQueue.java
 * Created      : 28-apr-2010
 * *************************************************/
package com.android.syssetup.util;

//#ifdef DEBUG
//#endif

/**
 * The Class BlockingQueue.
 *
 * @author Rob Gordon.
 */
public final class BlockingQueue {

	private final Queue list = new VectorQueue();
	Object blockedLock = new Object();

	// private final boolean wait = false;
	private boolean closed = false;

	/**
	 * Close.
	 */
	public synchronized void close() {
		closed = true;
		notifyAll();
	}

	/**
	 * Dequeue.
	 *
	 * @return the object
	 */
	public synchronized Object dequeue() {
		while (!closed && list.isEmpty()) {
			try {
				wait();
			} catch (final InterruptedException e) {
				// ignore
			}
		}
		if (list.isEmpty()) {
			return null;
		}
		return list.dequeue();
	}

	/**
	 * Enqueue.
	 *
	 * @param o the o
	 */
	public synchronized void enqueue(final Object o) {
		if (closed) {
			throw new ClosedException();
		}
		list.enqueue(o);
		notifyAll();
	}

	/**
	 * Checks if is empty.
	 *
	 * @return true, if is empty
	 */
	public synchronized boolean isEmpty() {
		return list.isEmpty();
	}

	/**
	 * Open.
	 */
	public synchronized void open() {
		closed = false;
	}

	/**
	 * The Class ClosedException.
	 */
	public static class ClosedException extends RuntimeException {

		/**
		 * Instantiates a new closed exception.
		 */
		ClosedException() {
			super("Queue closed.");
		}
	}

}
