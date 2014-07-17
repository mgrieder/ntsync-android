
package com.ntsync.android.sync.platform;

/*
 * Copyright (C) 2014 Markus Grieder
 * 
 * This file is based on BatchOperation.java from the SampleSyncAdapter-Example in Android SDK
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
 * Copyright (C) 2010 The Android Open Source Project
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
 * the License.
 */

import java.util.ArrayList;
import java.util.List;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.ContactsContract;

/**
 * This class handles execution of batch mOperations on Contacts provider.
 */
public final class BatchOperation {

	private final ContentResolver mResolver;

	// List for storing the batch mOperations
	private final ArrayList<ContentProviderOperation> mOperations;

	/** Count BlobSizes */
	private int totalBlobSize = 0;

	/**
	 * Limit over Batches should be executed. 400KB. (currently maximum display
	 * size is 720x720 -> 512KB, 1MB- 512KB = 488KB, damit vor dem hinzuf�gen
	 * des n�chsten photos die batches abgeschickt werden.
	 */
	private int goodBlobSize = 400 * 1000;

	public BatchOperation(ContentResolver resolver) {
		mResolver = resolver;
		mOperations = new ArrayList<ContentProviderOperation>();
	}

	public int size() {
		return mOperations.size();
	}

	public void add(ContentProviderOperation cpo) {
		mOperations.add(cpo);
	}

	/**
	 * @return true if batch should be executed because blobs are too big.
	 */
	public boolean isBlobSizeBig() {
		// Da ein Limit von 1MB f�r alle ConstantValue und nicht nur Blobs
		// besteht,
		// fr�hzeitig den Batch ausf�hren -> aufgabe vom caller.
		return totalBlobSize >= goodBlobSize;
	}

	public void addBlobSize(int blobSize) {
		totalBlobSize += blobSize;
	}

	public List<Uri> execute() throws OperationApplicationException {
		List<Uri> resultUris = new ArrayList<Uri>();

		if (mOperations.isEmpty()) {
			return resultUris;
		}
		// Apply the mOperations to the content provider
		try {
			ContentProviderResult[] results = mResolver.applyBatch(
					ContactsContract.AUTHORITY, mOperations);
			if ((results != null) && (results.length > 0)) {
				for (int i = 0; i < results.length; i++) {
					resultUris.add(results[i].uri);
				}
			}
		} catch (final RemoteException e) {
			throw new OperationApplicationException(e);
		}
		mOperations.clear();
		totalBlobSize = 0;
		return resultUris;
	}
}
