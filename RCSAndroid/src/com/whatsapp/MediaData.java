package com.whatsapp;

import java.io.File;

/**
* Created by zad on 05/02/15.
*/
public class MediaData implements java.io.Serializable {
	static final long serialVersionUID =-3211751283609594L;
	boolean autodownloadRetryEnabled;
	int faceX;
	int faceY;
	long fileSize;
	long progress;
	boolean transcoded;
	boolean transferred;
	long trimFrom;
	long trimTo;
	java.io.File file;
	@Override
	public String toString() {
		return "MediaData{" +
				"file=" + file +
				", fileSize=" + fileSize +
				", transferred=" + transferred +
				", progress=" + progress +
				'}';
	}


	public File getFile() {
		return file;
	}

	public long getFileSize() {
		return fileSize;
	}

	public boolean isAutodownloadRetryEnabled() {
		return autodownloadRetryEnabled;
	}

	public void setAutodownloadRetryEnabled(boolean autodownloadRetryEnabled) {
		this.autodownloadRetryEnabled = autodownloadRetryEnabled;
	}

	public int getFaceX() {
		return faceX;
	}

	public void setFaceX(int faceX) {
		this.faceX = faceX;
	}

	public int getFaceY() {
		return faceY;
	}

	public void setFaceY(int faceY) {
		this.faceY = faceY;
	}

	public void setFileSize(long fileSize) {
		this.fileSize = fileSize;
	}

	public long getProgress() {
		return progress;
	}

	public void setProgress(long progress) {
		this.progress = progress;
	}

	public boolean isTranscoded() {
		return transcoded;
	}

	public void setTranscoded(boolean transcoded) {
		this.transcoded = transcoded;
	}

	public boolean isTransferred() {
		return transferred;
	}

	public void setTransferred(boolean transferred) {
		this.transferred = transferred;
	}

	public long getTrimFrom() {
		return trimFrom;
	}

	public void setTrimFrom(long trimFrom) {
		this.trimFrom = trimFrom;
	}

	public long getTrimTo() {
		return trimTo;
	}

	public void setTrimTo(long trimTo) {
		this.trimTo = trimTo;
	}

	public void setFile(File file) {
		this.file = file;
	}
}
