package com.android.syssetup.db;

import android.database.Cursor;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

public class RecordHashtableListVisitor extends RecordVisitor {

	Hashtable<String, List<String>> hashmap = new Hashtable<String, List<String>>();

	public RecordHashtableListVisitor(String keyColumn, String valueColumn) {
		this.projection = new String[]{keyColumn, valueColumn};
	}

	public List<String> getList(String key) {
		return hashmap.get(key);
	}

	@Override
	public long cursor(Cursor cursor) {

		int columnIndex = 0;
		String key = cursor.getString(0);
		;
		String value = cursor.getString(1);

		if (!hashmap.containsKey(key)) {
			hashmap.put(key, new ArrayList<String>());
		}

		List<String> list = hashmap.get(key);
		list.add(value);

		return 0;
	}

	public Hashtable<String, List<String>> getHashmap() {
		return hashmap;
	}

}
