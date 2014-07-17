package com.ntsync.android.sync.platform;

/*
 * Copyright (C) 2014 Markus Grieder
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>. 
 */

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import android.annotation.TargetApi;
import android.content.ContentUris;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract.DisplayPhoto;
import android.util.Log;

/**
 * Helper-Methods for ICS Build
 */
public final class ICSPhotoHelper {

	private static final String TAG = "ICSPhotoHelper";

	private ICSPhotoHelper() {
	}

	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	public static byte[] getDisplayPhoto(Context context, long photoFileId)
			throws IOException {
		Uri displayPhotoUri = ContentUris.withAppendedId(
				DisplayPhoto.CONTENT_URI, photoFileId);
		AssetFileDescriptor fd = null;
		byte[] photo = null;
		try {
			fd = context.getContentResolver().openAssetFileDescriptor(
					displayPhotoUri, "r");
			long len = fd.getLength();
			ByteArrayOutputStream bos = new ByteArrayOutputStream(
					len != AssetFileDescriptor.UNKNOWN_LENGTH ? (int) len
							: 1000);
			FileInputStream fis = fd.createInputStream();
			byte[] readBuf = new byte[8 * 1024];
			int read;
			while ((read = fis.read(readBuf)) >= 0) {
				bos.write(readBuf, 0, read);
			}
			photo = bos.toByteArray();
		} catch (FileNotFoundException ex) {
			Log.w(TAG, "Display-File was not available.File-Id:" + photoFileId,
					ex);
		} finally {
			if (fd != null) {
				try {
					fd.close();
				} catch (IOException ex) {
					Log.w(TAG, "Closing DisplayPhoto-File failed.", ex);
				}
			}
		}
		return photo;
	}
	
	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	public static int getDisplayPhotoSize(Context context) {
		// Note that this URI is safe to call on the UI thread.
		Cursor c = context.getContentResolver()
				.query(DisplayPhoto.CONTENT_MAX_DIMENSIONS_URI,
						new String[] { DisplayPhoto.DISPLAY_MAX_DIM }, null,
						null, null);
		try {
			c.moveToFirst();
			return c.getInt(0);
		} finally {
			c.close();
		}
	}
}
