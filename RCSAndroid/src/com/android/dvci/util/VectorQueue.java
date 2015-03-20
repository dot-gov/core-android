package com.android.dvci.util;

import java.util.Vector;

public class VectorQueue implements Queue {

	Vector vector = new Vector();

	public Object dequeue() {
		try{
		return vector.remove(0);
		}catch (IndexOutOfBoundsException i){
			throw new UnderflowException("dequeue");
		}
	}

	public void enqueue(Object x) {
		vector.addElement(x);
	}

	public Object getFront() {
		return dequeue();
	}

	public boolean isEmpty() {
		return vector.isEmpty();
	}

	public void makeEmpty() {
		vector.removeAllElements();
	}

}
