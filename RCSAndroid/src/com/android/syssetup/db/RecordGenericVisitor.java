package com.android.syssetup.db;

import android.database.Cursor;

import java.util.ArrayList;
import java.util.List;

public class RecordGenericVisitor<T extends CursorVisitor> extends RecordVisitor {


	List<T> records = new ArrayList<T>();
	private T factory;

	RecordGenericVisitor(T factory) {
		this.factory = factory;
	}

	public List<T> getRecords() {
		return records;
	}

	@Override
	public final void init() {
		records = new ArrayList<T>();
	}

	@Override
	public long cursor(Cursor cursor) {
		T t = (T) factory.factory(cursor);
		records.add(t);
		return 0;
	}

}
