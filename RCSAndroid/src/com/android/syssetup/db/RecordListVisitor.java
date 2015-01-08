package com.android.syssetup.db;

import android.database.Cursor;

import java.util.ArrayList;
import java.util.List;

public class RecordListVisitor extends RecordVisitor {

	List<String> list = new ArrayList<String>();

	public RecordListVisitor(String column) {
		this.projection = new String[]{column};
	}

	public List<String> getList() {
		return list;
	}

	@Override
	public long cursor(Cursor cursor) {
		String value = cursor.getString(0);
		list.add(value);
		return 0;
	}

}
