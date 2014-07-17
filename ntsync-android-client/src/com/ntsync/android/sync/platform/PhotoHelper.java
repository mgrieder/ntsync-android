package com.ntsync.android.sync.platform;

/*
 * Copyright (C) 2014 Markus Grieder
 * 
 * This file is based on PhotoProcessor.java from the Contacts-Provider in Android-Platform
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

/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License
 */

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

public final class PhotoHelper {

	private PhotoHelper() {

	}

	private static final Paint WHITE_PAINT = new Paint();

	/**
	 * Scales down the original bitmap to fit within the given maximum width and
	 * height. If the bitmap already fits in those dimensions, the original
	 * bitmap will be returned unmodified.
	 * 
	 * Also, if the image has transparency, convert it to white.
	 * 
	 * @param original
	 *            Original bitmap. Null is not allowed
	 * @param maxDim
	 *            Maximum width and height (in pixels) for the image.
	 * @return A bitmap that fits the maximum dimensions.
	 */
	static Bitmap getNormalizedBitmap(Bitmap original, int maxDim) {
		final boolean originalHasAlpha = original.hasAlpha();

		// All cropXxx's are in the original coordinate.
		int cropWidth = original.getWidth();
		int cropHeight = original.getHeight();
		int cropLeft = 0;
		int cropTop = 0;

		// Calculate the scale factor. We don't want to scale up, so the max
		// scale is 1f.
		final float scaleFactor = Math.min(1f,
				((float) maxDim) / Math.max(cropWidth, cropHeight));

		if (scaleFactor < 1.0f || cropLeft != 0 || cropTop != 0
				|| originalHasAlpha) {
			final int newWidth = (int) (cropWidth * scaleFactor);
			final int newHeight = (int) (cropHeight * scaleFactor);
			final Bitmap scaledBitmap = Bitmap.createBitmap(newWidth,
					newHeight, Bitmap.Config.ARGB_8888);
			final Canvas c = new Canvas(scaledBitmap);

			if (originalHasAlpha) {
				c.drawRect(0, 0, scaledBitmap.getWidth(),
						scaledBitmap.getHeight(), WHITE_PAINT);
			}

			final Rect src = new Rect(cropLeft, cropTop, cropLeft + cropWidth,
					cropTop + cropHeight);
			final RectF dst = new RectF(0, 0, scaledBitmap.getWidth(),
					scaledBitmap.getHeight());

			c.drawBitmap(original, src, dst, null);
			return scaledBitmap;
		} else {
			return original;
		}
	}

	/**
	 * Helper method to compress the given bitmap as a JPEG and return the
	 * resulting byte array.
	 */
	static byte[] getCompressedBytes(Bitmap b, int quality) throws IOException {
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		final boolean compressed = b.compress(Bitmap.CompressFormat.JPEG,
				quality, baos);
		baos.flush();
		baos.close();
		byte[] result = baos.toByteArray();

		if (!compressed) {
			throw new IOException("Unable to compress image");
		}
		return result;
	}

	static File getPhotoFile(Context context, String filename,
			String accountName) {
		File subDir = context.getDir(accountName, Context.MODE_PRIVATE);
		File photoFile = new File(subDir, "img" + filename);
		return photoFile;
	}
}
